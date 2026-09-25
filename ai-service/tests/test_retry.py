"""llm_client.py 的重试、退避与流式行为测试。

不联网：用假的底层客户端喂进预设的异常与响应。**只有这种测试才能验证
「失败之后到底试了几次」**——真去调线上接口，你没法安排它恰好超时两次再成功。

``_sleep`` 被替换成「只记录、不真的等」，否则跑一遍测试要干等好几秒。
"""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import MagicMock

import httpx
import openai
import pytest

import llm_client
from config import Settings
from llm_client import LLMClient, backoff_delay, is_retryable

URL = "https://example.com/v1/chat/completions"


def _response(status: int) -> httpx.Response:
    return httpx.Response(status, request=httpx.Request("POST", URL))


def timeout_error() -> openai.APITimeoutError:
    return openai.APITimeoutError(request=httpx.Request("POST", URL))


def connection_error() -> openai.APIConnectionError:
    return openai.APIConnectionError(request=httpx.Request("POST", URL))


def rate_limit_error() -> openai.RateLimitError:
    return openai.RateLimitError("rate limited", response=_response(429), body=None)


def server_error() -> openai.InternalServerError:
    return openai.InternalServerError("boom", response=_response(503), body=None)


def auth_error() -> openai.AuthenticationError:
    return openai.AuthenticationError("bad key", response=_response(401), body=None)


def bad_request_error() -> openai.BadRequestError:
    return openai.BadRequestError("bad param", response=_response(400), body=None)


def not_found_error() -> openai.NotFoundError:
    return openai.NotFoundError("no such model", response=_response(404), body=None)


def settings(**overrides) -> Settings:
    base = {
        "api_key": "sk-test",
        "base_url": "https://example.com/v1",
        "chat_model": "test-model",
        "embedding_model": "test-embedding",
        "timeout_seconds": 1.0,
        "max_attempts": 3,
        "backoff_base": 1.0,
        "backoff_max": 8.0,
    }
    base.update(overrides)
    return Settings(**base)


def make_client(**overrides) -> LLMClient:
    return LLMClient(settings(**overrides))


def fake_completion(text: str) -> SimpleNamespace:
    return SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=text))],
        usage=SimpleNamespace(prompt_tokens=11, completion_tokens=7),
    )


def fake_chunk(text: str) -> SimpleNamespace:
    return SimpleNamespace(
        choices=[SimpleNamespace(delta=SimpleNamespace(content=text))]
    )


@pytest.fixture(autouse=True)
def no_sleep(monkeypatch):
    """把真实等待换掉，同时把每次等待的秒数记下来供断言。"""
    slept: list[float] = []
    monkeypatch.setattr(llm_client, "_sleep", slept.append)
    return slept


# ------------------------------------------------------------------ 异常分类
@pytest.mark.parametrize(
    "factory",
    [timeout_error, connection_error, rate_limit_error, server_error],
)
def test_transient_failures_are_retryable(factory):
    assert is_retryable(factory()) is True


@pytest.mark.parametrize(
    "factory",
    [auth_error, bad_request_error, not_found_error],
)
def test_request_level_failures_are_not_retryable(factory):
    """401/400/404 重试多少次都是同一个结果，重试只是白烧额度。"""
    assert is_retryable(factory()) is False


def test_python_level_errors_are_not_retryable():
    assert is_retryable(ValueError("普通程序错误")) is False


def test_auth_error_is_status_error_but_still_not_retryable():
    """回归测试：401 也是 APIStatusError 的子类。

    如果 is_retryable 先按状态码粗判一轮，401 会被误判成「非 5xx 不重试」——
    碰巧也对。但若哪天有人把兜底条件写成 ``>= 400``，这条测试会立刻拦住。
    """
    error = auth_error()
    assert isinstance(error, openai.APIStatusError)
    assert error.status_code == 401
    assert is_retryable(error) is False


# ------------------------------------------------------------------ 退避计算
def test_backoff_grows_exponentially():
    assert backoff_delay(1, 1.0, 8.0, jitter_ratio=0) == 1.0
    assert backoff_delay(2, 1.0, 8.0, jitter_ratio=0) == 2.0
    assert backoff_delay(3, 1.0, 8.0, jitter_ratio=0) == 4.0


def test_backoff_is_capped():
    assert backoff_delay(4, 1.0, 8.0, jitter_ratio=0) == 8.0
    assert backoff_delay(10, 1.0, 8.0, jitter_ratio=0) == 8.0


def test_backoff_jitter_stays_within_a_quarter():
    for _ in range(200):
        delay = backoff_delay(3, 1.0, 8.0)  # 基数 4.0，抖动区间 [3.0, 5.0]
        assert 3.0 <= delay <= 5.0


# ------------------------------------------------------------------ 重试循环
def test_retries_then_succeeds(no_sleep):
    client = make_client(max_attempts=3)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = [
        timeout_error(),
        rate_limit_error(),
        fake_completion('{"ok": true}'),
    ]
    client._raw = raw

    result = client.complete_json([{"role": "user", "content": "hi"}])

    assert result.text == '{"ok": true}'
    assert raw.chat.completions.create.call_count == 3
    assert len(no_sleep) == 2, "两次失败各等一次"
    assert 0.75 <= no_sleep[0] <= 1.25, "第 1 次退避基数 1.0s，±25% 抖动"
    assert 1.5 <= no_sleep[1] <= 2.5, "第 2 次退避基数 2.0s，±25% 抖动"


def test_gives_up_after_max_attempts(no_sleep):
    client = make_client(max_attempts=3)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = timeout_error()
    client._raw = raw

    with pytest.raises(openai.APITimeoutError):
        client.complete_json([{"role": "user", "content": "hi"}])

    assert raw.chat.completions.create.call_count == 3
    assert len(no_sleep) == 2, "放弃前只等 2 次，不该白等第 3 次"


def test_fatal_error_is_not_retried(no_sleep):
    client = make_client(max_attempts=3)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = auth_error()
    client._raw = raw

    with pytest.raises(openai.AuthenticationError):
        client.complete_json([{"role": "user", "content": "hi"}])

    assert raw.chat.completions.create.call_count == 1
    assert no_sleep == []


def test_max_attempts_one_means_no_retry(no_sleep):
    client = make_client(max_attempts=1)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = rate_limit_error()
    client._raw = raw

    with pytest.raises(openai.RateLimitError):
        client.complete_json([{"role": "user", "content": "hi"}])

    assert raw.chat.completions.create.call_count == 1


def test_retry_events_are_reported():
    events = []
    client = LLMClient(settings(max_attempts=2), on_retry=events.append)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = [connection_error(), fake_completion("ok")]
    client._raw = raw

    client.complete_json([{"role": "user", "content": "hi"}])

    assert len(events) == 1
    assert events[0].attempt == 1
    assert events[0].will_retry is True
    assert "APIConnectionError" in events[0].error


# --------------------------------------------------------------- JSON 模式降级
def test_falls_back_when_endpoint_rejects_json_mode():
    """兼容端点不认 response_format 时，去掉它再发一次，而不是直接失败。"""
    seen: list[dict] = []

    def create(**kwargs):
        seen.append(kwargs)
        if "response_format" in kwargs:
            raise bad_request_error()
        return fake_completion('{"ok": true}')

    client = make_client()
    raw = MagicMock()
    raw.chat.completions.create.side_effect = create
    client._raw = raw

    result = client.complete_json([{"role": "user", "content": "hi"}])

    assert result.text == '{"ok": true}'
    assert len(seen) == 2
    assert "response_format" in seen[0]
    assert "response_format" not in seen[1]


# -------------------------------------------------------------------- 流式
def test_stream_yields_pieces_in_order():
    client = make_client()
    raw = MagicMock()
    raw.chat.completions.create.return_value = iter(
        [fake_chunk("赣州"), fake_chunk("两室一厅"), fake_chunk("空置")]
    )
    client._raw = raw

    assert list(client.stream_chat([{"role": "user", "content": "hi"}])) == [
        "赣州",
        "两室一厅",
        "空置",
    ]


def test_stream_skips_empty_chunks():
    """有些服务端会在末尾补一个 choices 为空的包，不能让它把输出弄崩。"""
    client = make_client()
    raw = MagicMock()
    raw.chat.completions.create.return_value = iter(
        [
            fake_chunk("赣州"),
            SimpleNamespace(choices=[]),               # 末尾的 usage 包
            SimpleNamespace(choices=[SimpleNamespace(delta=SimpleNamespace(content=None))]),
            fake_chunk("空置"),
        ]
    )
    client._raw = raw

    assert "".join(client.stream_chat([{"role": "user", "content": "hi"}])) == "赣州空置"


def test_stream_retries_when_nothing_emitted_yet():
    """一个字都还没吐出去之前失败 —— 重试不会让用户看到重复内容，可以重试。"""
    client = make_client(max_attempts=3)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = [
        connection_error(),
        iter([fake_chunk("赣州"), fake_chunk("空置")]),
    ]
    client._raw = raw

    assert "".join(client.stream_chat([{"role": "user", "content": "hi"}])) == "赣州空置"
    assert raw.chat.completions.create.call_count == 2


def test_stream_does_not_retry_after_first_piece(no_sleep):
    """已经吐出去内容之后再失败 —— 必须抛异常，不能重来。

    这就是「流式」与「重试」冲突的地方：重来会把同一段话再输出一遍，
    用户看到的是重复文本，而不是一个错误提示。
    """
    def broken_stream():
        yield fake_chunk("赣州")
        raise timeout_error()

    client = make_client(max_attempts=3)
    raw = MagicMock()
    raw.chat.completions.create.return_value = broken_stream()
    client._raw = raw

    collected: list[str] = []
    with pytest.raises(openai.APITimeoutError):
        for piece in client.stream_chat([{"role": "user", "content": "hi"}]):
            collected.append(piece)

    assert collected == ["赣州"], "已经输出的部分保留给调用方，不做回滚"
    assert raw.chat.completions.create.call_count == 1, "不得重试"
    assert no_sleep == [], "既然不重试，就不该有等待"


def test_stream_does_not_retry_fatal_error():
    client = make_client(max_attempts=3)
    raw = MagicMock()
    raw.chat.completions.create.side_effect = auth_error()
    client._raw = raw

    with pytest.raises(openai.AuthenticationError):
        list(client.stream_chat([{"role": "user", "content": "hi"}]))

    assert raw.chat.completions.create.call_count == 1


# -------------------------------------------------------------------- 配置
def test_sdk_internal_retry_is_disabled():
    """自建重试必须配 max_retries=0，否则两层重试叠乘，实际请求数是乘积。"""
    client = make_client(max_attempts=3)
    assert client._raw.max_retries == 0


def test_masked_key_hides_secret():
    client = LLMClient(settings(api_key="sk-1234567890abcdefghij"))
    masked = client.settings.masked_key()
    assert masked.startswith("sk-123")
    assert "4567890" not in masked
    assert masked.endswith("ghij")
