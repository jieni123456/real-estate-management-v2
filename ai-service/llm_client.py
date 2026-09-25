"""模型调用层：流式输出、超时、以及「哪些失败值得重试」。

这一层刻意只做三件事，业务含义（抽取什么字段、怎么组织提示词）留在别处：

1. **超时**——统一从 ``Settings`` 取，不让每个调用点各设一个。
2. **流式输出**——逐段 yield，调用方拿到一段就能显示一段。
3. **可分辨的重试**——区分「等会儿再试可能就行」与「再试一百次也一样」，
   并且把每次重试的决策暴露出去，而不是闷头重试到调用方以为是自己卡了。

有个容易踩的坑写在这里：**SDK 自带重试**。``openai`` 客户端默认
``max_retries=2``，如果在外面再套一层 3 次重试，实际最多会发出 3×3=9 次请求，
而你以为只发了 3 次。所以下面构造客户端时显式设成 ``max_retries=0``，
把重试权收到自己手里。
"""

from __future__ import annotations

import random
import time
from collections.abc import Callable, Iterator, Sequence
from dataclasses import dataclass

import openai
from openai import OpenAI

from config import Settings

# ---------------------------------------------------------------------------
# 异常分类
# ---------------------------------------------------------------------------
# 可重试：临时性故障——这次不行，过一会儿可能就行。
RETRYABLE_ERRORS: tuple[type[BaseException], ...] = (
    openai.APITimeoutError,      # 请求超时（我们自己在 Settings 里设的那个）
    openai.APIConnectionError,   # 连不上、连接中途被断开
    openai.RateLimitError,       # HTTP 429，触发限流
    openai.InternalServerError,  # HTTP 5xx，服务端自己出错
)

# 不可重试：请求本身有问题，原样再发一次只会拿到同样的结果，还白烧额度。
#   401 AuthenticationError  Key 错了或没带
#   403 PermissionDeniedError 没开通该模型的权限
#   400 BadRequestError       请求参数不合法
#   404 NotFoundError         模型名写错了
FATAL_ERRORS: tuple[type[BaseException], ...] = (
    openai.AuthenticationError,
    openai.PermissionDeniedError,
    openai.BadRequestError,
    openai.NotFoundError,
)


def is_retryable(exc: BaseException) -> bool:
    """这个异常值不值得重试。

    判断顺序是有讲究的：**先排除「明确不该重试」，再看「明确该重试」**。
    因为 ``AuthenticationError``（401）这类也都是 ``APIStatusError`` 的子类，
    如果先按状态码粗判一轮，401 会被漏进来。
    """
    if isinstance(exc, FATAL_ERRORS):
        return False
    if isinstance(exc, RETRYABLE_ERRORS):
        return True
    # 兜底：其余 5xx 一并按临时故障处理（502 / 503 / 504 之流）。
    if isinstance(exc, openai.APIStatusError):
        return exc.status_code >= 500
    return False


def backoff_delay(attempt: int, base: float, cap: float, jitter_ratio: float = 0.25) -> float:
    """第 ``attempt`` 次尝试失败后应该等多久（``attempt`` 从 1 开始）。

    指数退避：``base * 2**(attempt-1)``，封顶 ``cap``。
    再叠加 ±``jitter_ratio`` 的随机抖动——这一条不是装饰：如果多个调用方同时被
    限流，固定间隔会让它们在同一时刻齐刷刷重试，把刚缓过来的服务再打垮一次。
    """
    raw = min(base * (2 ** (attempt - 1)), cap)
    if jitter_ratio <= 0:
        return raw
    return raw * random.uniform(1 - jitter_ratio, 1 + jitter_ratio)


# 模块级别名，方便测试把真实等待替换掉（否则跑一次重试测试要干等好几秒）。
_sleep: Callable[[float], None] = time.sleep


@dataclass(frozen=True)
class RetryEvent:
    """一次重试决策。给调用方一个「看得见重试过程」的机会。"""

    attempt: int          # 第几次尝试失败了（从 1 开始）
    error: str            # 异常类型与简要信息
    delay: float          # 接下来等待的秒数
    will_retry: bool      # 是否真的还会再试


@dataclass(frozen=True)
class Completion:
    """一次非流式调用的结果。带上 token 用量，便于观察成本。"""

    text: str
    prompt_tokens: int
    completion_tokens: int


class LLMClient:
    """对话、JSON 输出、流式输出的统一入口。"""

    def __init__(
        self,
        settings: Settings,
        *,
        on_retry: Callable[[RetryEvent], None] | None = None,
    ) -> None:
        self._settings = settings
        self._on_retry = on_retry
        self._raw = OpenAI(
            api_key=settings.api_key,
            base_url=settings.base_url,
            timeout=settings.timeout_seconds,
            # 关掉 SDK 自带重试，理由见模块开头的说明。
            max_retries=0,
        )

    # ------------------------------------------------------------------ 内部
    @property
    def settings(self) -> Settings:
        return self._settings

    def _notify(self, event: RetryEvent) -> None:
        if self._on_retry is not None:
            self._on_retry(event)

    def _wait_before_retry(self, attempt: int, exc: BaseException) -> None:
        delay = backoff_delay(
            attempt,
            self._settings.backoff_base,
            self._settings.backoff_max,
        )
        self._notify(
            RetryEvent(
                attempt=attempt,
                error=f"{type(exc).__name__}: {exc}",
                delay=delay,
                will_retry=True,
            )
        )
        _sleep(delay)

    # ---------------------------------------------------------------- 流式
    def stream_chat(
        self,
        messages: Sequence[dict[str, str]],
        *,
        temperature: float = 0.3,
    ) -> Iterator[str]:
        """流式输出：模型吐一段，这里就 yield 一段。

        这里处理了「流式」和「重试」天生冲突的地方：

        重试的前提是「这次请求没成功」，可流式输出一旦已经把内容交给了调用方
        （很可能已经打印到屏幕上了），再重试就会把同一段话重说一遍，
        用户看到的是重复内容而不是错误。所以——**只有在一个字都还没吐出去的时候
        才允许重试**；已经开始输出之后遇到异常，就直接把异常抛给调用方。
        """
        emitted = 0  # 已经 yield 出去的片段数

        for attempt in range(1, self._settings.max_attempts + 1):
            try:
                stream = self._raw.chat.completions.create(
                    model=self._settings.chat_model,
                    messages=list(messages),
                    temperature=temperature,
                    stream=True,
                )
                for chunk in stream:
                    # 有些服务端会在末尾补一个 choices 为空、只带 usage 的包。
                    if not chunk.choices:
                        continue
                    piece = chunk.choices[0].delta.content
                    if piece:
                        emitted += 1
                        yield piece
                return
            except Exception as exc:
                last_attempt = attempt >= self._settings.max_attempts
                if emitted == 0 and not last_attempt and is_retryable(exc):
                    self._wait_before_retry(attempt, exc)
                    continue
                self._notify(
                    RetryEvent(
                        attempt=attempt,
                        error=f"{type(exc).__name__}: {exc}",
                        delay=0.0,
                        will_retry=False,
                    )
                )
                raise

    # ------------------------------------------------------- 非流式 / JSON
    def complete_json(
        self,
        messages: Sequence[dict[str, str]],
        *,
        temperature: float = 0.1,
    ) -> Completion:
        """要求模型输出一个 JSON 对象，返回原始字符串与用量。

        用的是 ``response_format={"type": "json_object"}``（JSON 模式）。
        要清楚它的边界：**它只保证输出是「合法 JSON」，不保证字段对不对**。
        字段名对不对、类型对不对、有没有多出表里没有的列——那是 ``schema.py``
        的活，必须在拿到字符串之后自己校验。
        """
        try:
            response = self._call_json(messages, temperature, use_json_mode=True)
        except openai.BadRequestError:
            # 少数兼容端点不认 response_format。这属于「请求不被接受」，
            # 重试解决不了，但降级一次就能过——所以在重试机制之外单独兜一层。
            response = self._call_json(messages, temperature, use_json_mode=False)

        message = response.choices[0].message.content or ""
        usage = response.usage
        return Completion(
            text=message,
            prompt_tokens=getattr(usage, "prompt_tokens", 0) or 0,
            completion_tokens=getattr(usage, "completion_tokens", 0) or 0,
        )

    def _call_json(
        self,
        messages: Sequence[dict[str, str]],
        temperature: float,
        *,
        use_json_mode: bool,
    ):
        """带重试的非流式调用。非流式没有「已经吐出去的内容」这个顾虑，
        所以这里可以放心地整轮重试。"""
        kwargs: dict[str, object] = {
            "model": self._settings.chat_model,
            "messages": list(messages),
            "temperature": temperature,
        }
        if use_json_mode:
            kwargs["response_format"] = {"type": "json_object"}

        for attempt in range(1, self._settings.max_attempts + 1):
            try:
                return self._raw.chat.completions.create(**kwargs)  # type: ignore[arg-type]
            except Exception as exc:
                last_attempt = attempt >= self._settings.max_attempts
                if last_attempt or not is_retryable(exc):
                    self._notify(
                        RetryEvent(
                            attempt=attempt,
                            error=f"{type(exc).__name__}: {exc}",
                            delay=0.0,
                            will_retry=False,
                        )
                    )
                    raise
                self._wait_before_retry(attempt, exc)

        # 循环必然要么 return 要么 raise，走不到这里；写上是为了让类型检查器满意。
        raise AssertionError("unreachable")

    # ------------------------------------------------------------ 便捷方法
    def ping(self) -> str:
        """确认 Key、端点、模型名三件事都配对。

        只发一个极小的请求，费用可忽略，但能一次性排掉 401（Key 错）、
        404（模型名错）、以及地域选错（北京 Key 打新加坡端点）这三类问题。
        """
        response = self._raw.chat.completions.create(
            model=self._settings.chat_model,
            messages=[{"role": "user", "content": "ping"}],
            max_tokens=1,
        )
        return response.model
