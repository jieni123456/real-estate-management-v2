"""extract.py 的修复循环测试：不联网。

用假客户端按脚本喂进「模型的输出」，验证的是这套编排逻辑本身——
哪一轮该重试、哪一轮该直接返回、回喂给模型的内容里到底带了什么。
真实模型的输出无法安排得这么精确，所以这部分只能靠假客户端来覆盖。
"""

from __future__ import annotations

import json

from extract import extract_house, parse_envelope, strip_code_fence
from llm_client import Completion

ADDRESS = "赣州市章贡区长征大道 12 号锦绣花园 3 栋 802"


def good_output(**data_overrides) -> str:
    data = {
        "type": "两室一厅",
        "area": 89.5,
        "address": ADDRESS,
        "status": "空置",
    }
    data.update(data_overrides)
    return json.dumps(
        {"ok": True, "data": data, "missing": [], "reason": ""},
        ensure_ascii=False,
    )


def incomplete_output(*missing) -> str:
    return json.dumps(
        {
            "ok": False,
            "data": None,
            "missing": list(missing),
            "reason": "原文只提到了楼层和位置，没有说户型和面积",
        },
        ensure_ascii=False,
    )


class FakeClient:
    """按预设脚本依次返回输出的假客户端，并记录每次收到的消息。"""

    def __init__(self, *outputs: str) -> None:
        self._outputs = list(outputs)
        self.calls = 0
        self.seen_messages: list[list[dict]] = []

    def complete_json(self, messages, *, temperature: float = 0.1) -> Completion:
        self.seen_messages.append([dict(message) for message in messages])
        if not self._outputs:
            raise AssertionError(
                f"假客户端第 {self.calls + 1} 次被调用，但预设的输出已经用完了"
            )
        self.calls += 1
        return Completion(text=self._outputs.pop(0), prompt_tokens=10, completion_tokens=5)


# ----------------------------------------------------------------- 一轮通过
def test_one_round_success():
    client = FakeClient(good_output())
    result = extract_house("章贡区长征大道，两室一厅 89.5 平，空置", client)

    assert result.ok is True
    assert result.rounds == 1
    assert result.repaired is False
    assert result.fields.type == "两室一厅"
    assert result.fields.area == 89.5
    assert result.errors[0] == []
    assert client.calls == 1


# -------------------------------------------------------- 表外字段触发修复
def test_repairs_column_that_is_not_in_table():
    """模型被「精装修」三个字带着跑，多输出了一个 houses 表里没有的列。"""
    polluted = json.dumps(
        {
            "ok": True,
            "data": {
                "type": "三室两厅",
                "area": 128,
                "address": ADDRESS,
                "status": "空置",
                "decoration": "精装修",  # houses 表没有这一列
            },
            "missing": [],
            "reason": "",
        },
        ensure_ascii=False,
    )
    client = FakeClient(polluted, good_output(type="三室两厅", area=128))

    result = extract_house("万象公馆，三室两厅 128 平，精装修", client)

    assert result.ok is True
    assert result.rounds == 2
    assert result.repaired is True
    assert result.fields.type == "三室两厅"
    # 第一轮的错误说明要能指向具体的列
    assert any("houses 表" in message for message in result.errors[0]), result.errors


def test_repair_prompt_is_fed_back_with_the_error_detail():
    polluted = json.dumps(
        {"ok": True, "data": {"type": "三室两厅", "area": 128, "address": ADDRESS,
                              "status": "空置", "price": 1680000},
         "missing": [], "reason": ""},
        ensure_ascii=False,
    )
    client = FakeClient(polluted, good_output(type="三室两厅", area=128))

    extract_house("万象公馆，三室两厅 128 平，售价 168 万", client)

    second_round = client.seen_messages[1]
    last_user_message = second_round[-1]["content"]
    assert last_user_message.startswith("你上一次的输出没有通过校验")
    assert "houses 表" in last_user_message
    assert "price" in last_user_message
    # 上一轮的原始输出也要带上，否则模型不知道自己在改什么
    assert any(message["role"] == "assistant" for message in second_round)


# ----------------------------------------------------------------- JSON 修复
def test_repairs_malformed_json():
    client = FakeClient("这不是 JSON：户型两室一厅", good_output())

    result = extract_house("章贡区，两室一厅 89.5 平", client)

    assert result.ok is True
    assert result.rounds == 2
    assert any("不是合法 JSON" in message for message in result.errors[0]), result.errors


def test_repairs_envelope_shape_error():
    """ok 说是 false，却又没给出 missing —— 信封本身不自洽，要回喂。"""
    broken = json.dumps({"ok": False, "data": None, "missing": [], "reason": ""},
                        ensure_ascii=False)
    client = FakeClient(broken, good_output())

    result = extract_house("章贡区，两室一厅 89.5 平", client)

    assert result.ok is True
    assert result.rounds == 2
    assert any("missing" in message for message in result.errors[0]), result.errors


# --------------------------------------------------------------- 信息不足
def test_incomplete_is_a_valid_answer_not_a_failure():
    """模型如实说「抽不出来」时，不该再让它重试——它并没有答错。"""
    client = FakeClient(incomplete_output("type", "area"))

    result = extract_house("南康区那边有套老房子，六楼没电梯", client)

    assert result.ok is False
    assert result.fields is None
    assert result.rounds == 1, "这不是错误，不该进入修复循环"
    assert client.calls == 1
    assert "type" in result.note and "area" in result.note


# ----------------------------------------------------------------- 修复上限
def test_gives_up_after_repair_rounds():
    """模型陷入同一种错误时，修复循环必须有上限，否则就是一个按轮计费的死循环。"""
    bad = json.dumps(
        {"ok": True, "data": {"type": "两室一厅", "area": -1, "address": ADDRESS},
         "missing": [], "reason": ""},
        ensure_ascii=False,
    )
    client = FakeClient(bad, bad, bad)

    result = extract_house("章贡区，两室一厅 -1 平", client, max_repair_rounds=2)

    assert result.ok is False
    assert result.fields is None
    assert result.rounds == 3, "首轮 + 2 轮修复 = 最多 3 次调用"
    assert client.calls == 3
    assert "3 轮" in result.note


def test_max_repair_rounds_zero_means_single_call():
    client = FakeClient(good_output())
    result = extract_house("章贡区，两室一厅 89.5 平", client, max_repair_rounds=0)
    assert result.ok is True
    assert result.rounds == 1


# --------------------------------------------------------------- 边界与工具
def test_empty_input_does_not_call_model():
    client = FakeClient()
    result = extract_house("   ", client)

    assert result.ok is False
    assert client.calls == 0, "空输入没必要浪费一次调用"
    assert "为空" in result.note


def test_token_usage_is_accumulated_across_rounds():
    polluted = json.dumps({"ok": True, "data": {"type": "两室一厅", "area": 89.5,
                                                "address": ADDRESS, "decoration": "精装修"},
                           "missing": [], "reason": ""},
                          ensure_ascii=False)
    client = FakeClient(polluted, good_output())

    result = extract_house("章贡区，两室一厅 89.5 平，精装修", client)

    assert result.rounds == 2
    assert result.prompt_tokens == 20   # 每轮 10
    assert result.completion_tokens == 10  # 每轮 5


def test_strip_code_fence():
    assert strip_code_fence('```json\n{"ok": true}\n```') == '{"ok": true}'
    assert strip_code_fence('```\n{"ok": true}\n```') == '{"ok": true}'
    assert strip_code_fence('{"ok": true}') == '{"ok": true}'


def test_parse_envelope_requires_missing_when_not_ok():
    envelope, problems = parse_envelope({"ok": False, "reason": "抽不出"})
    assert envelope is None
    assert any("missing" in message for message in problems)


def test_parse_envelope_requires_data_when_ok():
    envelope, problems = parse_envelope({"ok": True, "data": None})
    assert envelope is None
    assert any("data" in message for message in problems)


def test_parse_envelope_rejects_extra_key():
    envelope, problems = parse_envelope(
        {"ok": True, "data": {}, "missing": [], "reason": "", "confidence": 0.9}
    )
    assert envelope is None
    assert problems, "信封里多出的键应当被拒绝"
