"""Day 1 的落地场景：一句自然语言房源描述 → 结构化字段。

流程一共四步，每一步都可能失败，而失败各有各的处理方式：

    模型输出
      │
      ├─ ① json.loads 能解析吗？        不能 → 回喂「不是合法 JSON」，重来一轮
      │
      ├─ ② 外层信封结构对吗？            不对 → 回喂具体错在哪，重来一轮
      │     （ok / data / missing / reason）
      │
      ├─ ③ ok == false 吗？             是   → 这**不是错误**，是「原文信息不足」，
      │                                        正常返回并说明缺什么，不再重试
      │
      └─ ④ data 逐字段校验通过吗？        不过 → 把中文错误说明回喂给模型让它修正

第 ③ 步是最容易被忽略的一环：如果只准备「抽出来的字段」这一种输出形态，
那么遇到信息不足的描述时，模型只有两条路——瞎编，或者输出违反格式的东西。
两条路都会污染数据。所以外层留一个信封，让模型有能力**如实说「我不知道」**。

提示词里也写了：宁可留空也不要补全。
"""

from __future__ import annotations

import json
import re
import time
from dataclasses import dataclass, field
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from llm_client import LLMClient
from schema import HouseFields, format_errors, schema_description, validate_payload

# ---------------------------------------------------------------------------
# 提示词
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = f"""你是一个房源信息抽取器，服务于一个二手房中介管理系统。

把用户给的一句房源描述，抽取成下面这些字段：
{schema_description()}

输出格式固定为下面这个 JSON 对象，不允许增删任何键：

{{
  "ok": true 或 false,
  "data": {{"type": "...", "area": 0, "address": "...", "status": "空置"}},
  "missing": [],
  "reason": ""
}}

规则：
1. 只输出 JSON 本身，不要输出解释文字，也不要包在 markdown 代码块里。
2. ok 表示「必填字段是否齐了」。必填字段是 type、area、address 三个。
3. ok 为 true 时：data 必须完整，missing 为 []，reason 为 ""。
4. ok 为 false 时：data 放 null，missing 列出缺哪些字段名，
   reason 用一句话说明原文为什么抽不出来。
5. 字段内容必须能在原文里找到依据。**原文没提到的，不要按常识补全**——
   宁可判 ok=false，也不要编一个看起来合理的值出来。
6. area 只写数字本身（如 89.5），不要带「平」「平方米」「㎡」等单位。
7. status 只能取「空置」或「已租出」。原文听不出来时用「空置」。
8. 多说一句：宁可少抽，不可编造。这个系统的下游是要写进数据库的。
"""


# ---------------------------------------------------------------------------
# 外层信封
# ---------------------------------------------------------------------------
class Envelope(BaseModel):
    """模型输出的外层结构：一层控制信息 + 一层数据。"""

    model_config = ConfigDict(extra="forbid")

    ok: bool = Field(..., description="必填字段是否齐全")
    data: dict[str, Any] | None = Field(default=None, description="ok=true 时的字段对象")
    missing: list[str] = Field(default_factory=list, description="ok=false 时缺失的字段名")
    reason: str = Field(default="", description="ok=false 时的一句话说明")


def parse_envelope(payload: Any) -> tuple[Envelope | None, list[str]]:
    """校验外层信封。返回 ``(信封, [错误])``。"""
    if not isinstance(payload, dict):
        return None, [f"顶层必须是 JSON 对象，实际是 {type(payload).__name__}"]
    try:
        envelope = Envelope.model_validate(payload)
    except ValidationError as exc:
        return None, [f"外层结构错误：{msg}" for msg in format_errors(exc)]

    problems: list[str] = []
    if envelope.ok:
        if envelope.data is None:
            problems.append("ok 为 true 时，data 不能是 null")
    else:
        if not envelope.missing:
            problems.append("ok 为 false 时，missing 必须列出缺失的字段名")
        if not envelope.reason.strip():
            problems.append("ok 为 false 时，reason 必须说明原因")
    return (None, problems) if problems else (envelope, [])


_CODE_FENCE = re.compile(r"^\s*```(?:json)?\s*|\s*```\s*$", re.IGNORECASE)


def strip_code_fence(text: str) -> str:
    """兜底：有些模型仍会习惯性把 JSON 包在 ``` 里。

    JSON 模式理论上不该出现这种情况，但「理论上」不值得赌——
    多这几行，就少一类「明明模型答对了却解析失败」的假故障。
    """
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = _CODE_FENCE.sub("", stripped)
    return stripped.strip()


# ---------------------------------------------------------------------------
# 结果
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class ExtractResult:
    """一次抽取的完整过程记录。失败也要返回，因为「为什么失败」本身就是信息。"""

    ok: bool                              # 是否拿到了可用字段
    fields: HouseFields | None            # 通过校验的字段对象
    raw_outputs: list[str] = field(default_factory=list)      # 每一轮模型的原始输出
    errors: list[list[str]] = field(default_factory=list)     # 每一轮的问题说明
    rounds: int = 0                       # 实际调用模型的轮数
    prompt_tokens: int = 0
    completion_tokens: int = 0
    duration: float = 0.0
    note: str = ""                        # 信息不足或修复失败时的说明

    @property
    def repaired(self) -> bool:
        """是否靠修复循环才拿到可用结果（第一轮就成功则不是）。"""
        return self.ok and self.rounds > 1


def _repair_prompt(problems: list[str]) -> str:
    lines = "\n".join(f"  {i}. {p}" for i, p in enumerate(problems, 1))
    return (
        "你上一次的输出没有通过校验，问题如下：\n"
        f"{lines}\n\n"
        "请重新输出修正后的 JSON 对象。仍然只输出 JSON 本身，不要任何解释文字。"
    )


def extract_house(
    description: str,
    client: LLMClient,
    *,
    max_repair_rounds: int = 2,
) -> ExtractResult:
    """把一句房源描述抽成结构化字段。

    ``max_repair_rounds`` 是**修复轮数**，不含首轮，所以最多调用模型
    ``max_repair_rounds + 1`` 次。给上限是必要的：模型偶尔会陷入同一种错误，
    没有上限的话这个循环就变成了一个按轮计费的死循环。
    """
    if not description.strip():
        return ExtractResult(ok=False, fields=None, note="输入为空，没有可抽取的内容")

    messages: list[dict[str, str]] = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": description},
    ]

    started = time.perf_counter()
    raw_outputs: list[str] = []
    errors: list[list[str]] = []
    prompt_tokens = completion_tokens = 0
    last_problems: list[str] = []

    for _ in range(max_repair_rounds + 1):
        completion = client.complete_json(messages)
        prompt_tokens += completion.prompt_tokens
        completion_tokens += completion.completion_tokens
        raw = strip_code_fence(completion.text)
        raw_outputs.append(raw)

        problems = _inspect(raw)
        if problems.raw_json is None:  # ① JSON 都解析不出来
            errors.append(problems.messages)
            last_problems = problems.messages
            messages.append({"role": "assistant", "content": raw})
            messages.append({"role": "user", "content": _repair_prompt(problems.messages)})
            continue
        if problems.envelope is None:  # ② 外层信封不合格
            errors.append(problems.messages)
            last_problems = problems.messages
            messages.append({"role": "assistant", "content": raw})
            messages.append({"role": "user", "content": _repair_prompt(problems.messages)})
            continue

        envelope = problems.envelope
        if not envelope.ok:  # ③ 信息不足：正常返回，这是模型在如实回答
            errors.append([])
            return ExtractResult(
                ok=False,
                fields=None,
                raw_outputs=raw_outputs,
                errors=errors,
                rounds=len(raw_outputs),
                prompt_tokens=prompt_tokens,
                completion_tokens=completion_tokens,
                duration=time.perf_counter() - started,
                note=(
                    "原文信息不足，缺少："
                    + ("、".join(envelope.missing) if envelope.missing else "（未说明）")
                    + (f"。模型说明：{envelope.reason}" if envelope.reason else "")
                ),
            )

        fields, field_errors = validate_payload(envelope.data)  # ④ 逐字段校验
        if fields is not None:
            errors.append([])
            return ExtractResult(
                ok=True,
                fields=fields,
                raw_outputs=raw_outputs,
                errors=errors,
                rounds=len(raw_outputs),
                prompt_tokens=prompt_tokens,
                completion_tokens=completion_tokens,
                duration=time.perf_counter() - started,
            )

        errors.append(field_errors)
        last_problems = field_errors
        messages.append({"role": "assistant", "content": raw})
        messages.append({"role": "user", "content": _repair_prompt(field_errors)})

    return ExtractResult(
        ok=False,
        fields=None,
        raw_outputs=raw_outputs,
        errors=errors,
        rounds=len(raw_outputs),
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        duration=time.perf_counter() - started,
        note=f"{len(raw_outputs)} 轮都没能通过校验，最后一次的问题是：{'；'.join(last_problems)}",
    )


@dataclass(frozen=True)
class _Inspection:
    raw_json: Any | None
    envelope: Envelope | None
    messages: list[str]


def _inspect(raw: str) -> _Inspection:
    """把「一段模型输出」过一遍 ①② 两道关。"""
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        return _Inspection(
            raw_json=None,
            envelope=None,
            messages=[f"输出不是合法 JSON：{exc.msg}（第 {exc.lineno} 行第 {exc.colno} 列）"],
        )

    envelope, problems = parse_envelope(payload)
    if envelope is None:
        return _Inspection(raw_json=payload, envelope=None, messages=problems)
    return _Inspection(raw_json=payload, envelope=envelope, messages=[])
