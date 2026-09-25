"""schema.py 的断言测试。

不联网、不调模型——校验逻辑是纯函数，本来就该能单独验证。
这也是这套代码把「规则」和「调用」分开的直接好处：改一条校验规则，
跑一遍这个文件就知道有没有把别的规则碰坏。
"""

from __future__ import annotations

from schema import (
    COLUMN_MAP,
    STATUS_RENTED,
    STATUS_VACANT,
    HouseFields,
    schema_description,
    validate_payload,
)


def _valid(**overrides) -> dict:
    payload = {
        "type": "两室一厅",
        "area": 89.5,
        "address": "赣州市章贡区长征大道 12 号锦绣花园 3 栋 802",
        "status": STATUS_VACANT,
    }
    payload.update(overrides)
    return payload


# --------------------------------------------------------------------- 通过
def test_accepts_valid_payload():
    fields, errors = validate_payload(_valid())
    assert errors == []
    assert fields is not None
    assert fields.type == "两室一厅"
    assert fields.area == 89.5
    assert fields.status == STATUS_VACANT


def test_status_defaults_to_vacant_when_absent():
    payload = _valid()
    payload.pop("status")
    fields, errors = validate_payload(payload)
    assert errors == []
    assert fields.status == STATUS_VACANT


def test_accepts_rented_status():
    fields, errors = validate_payload(_valid(status=STATUS_RENTED))
    assert errors == []
    assert fields.status == STATUS_RENTED


def test_strips_surrounding_whitespace():
    fields, _ = validate_payload(_valid(type="  两室一厅  ", address=" 赣州  "))
    assert fields.type == "两室一厅"
    assert fields.address == "赣州"


def test_accepts_numeric_string_for_area():
    """模型偶尔把数字写成字符串，"89.5" 这种应当照收，不必走修复循环。"""
    fields, errors = validate_payload(_valid(area="89.5"))
    assert errors == []
    assert fields.area == 89.5


# --------------------------------------------------------------------- 拒绝
def test_missing_required_field_gives_chinese_hint():
    payload = _valid()
    payload.pop("type")
    fields, errors = validate_payload(payload)
    assert fields is None
    assert any("户型" in message and "缺少" in message for message in errors), errors


def test_area_must_be_positive():
    for bad in (0, -1, -89.5):
        fields, errors = validate_payload(_valid(area=bad))
        assert fields is None, f"area={bad} 不该通过"
        assert any("面积" in message for message in errors), errors


def test_area_has_upper_bound():
    fields, errors = validate_payload(_valid(area=1001))
    assert fields is None
    assert any("1000" in message for message in errors), errors


def test_numeric_limits_are_rendered_without_float_tail():
    """边界值取自 pydantic 的 ctx，那里是 float——1000.0 要显示成 1000。

    这条是回归测试：pydantic 2.13 把 string_too_long 的 ctx 键从 limit 改成了
    max_length，模板没跟上就会出现字面量 "{limit}" 直接漏给模型看的场面。
    """
    _, errors = validate_payload(_valid(area=1001))
    assert errors == ["面积（area）：不能大于 1000"], errors

    _, errors = validate_payload(_valid(area=0))
    assert errors == ["面积（area）：必须大于 0"], errors

    _, errors = validate_payload(_valid(type="室" * 51))
    assert errors == ["户型（type）：超过 50 个字符的长度上限"], errors


def test_error_messages_never_leak_placeholders():
    """任何一条校验失败的说明都不能带 {xxx} 这样的半成品。"""
    cases = [
        _valid(area=0),
        _valid(area=1001),
        _valid(type=""),
        _valid(type="室" * 51),
        _valid(address=""),
        _valid(status="在租"),
        _valid(decoration="精装修"),
        {k: v for k, v in _valid().items() if k != "type"},
    ]
    for payload in cases:
        _, errors = validate_payload(payload)
        assert errors, f"{payload} 本该校验失败"
        for message in errors:
            assert "{" not in message and "}" not in message, message


def test_area_rejects_unit_suffix():
    """带单位是最常见的一种脏数据，报错信息要直接告诉模型该怎么改。"""
    fields, errors = validate_payload(_valid(area="89.5平"))
    assert fields is None
    assert any("面积" in message and "数字" in message for message in errors), errors


def test_type_length_limit():
    fields, errors = validate_payload(_valid(type="室" * 51))
    assert fields is None
    assert any("50" in message for message in errors), errors


def test_address_cannot_be_empty():
    fields, errors = validate_payload(_valid(address="   "))
    assert fields is None
    assert any("地址" in message for message in errors), errors


def test_status_must_be_one_of_two_values():
    fields, errors = validate_payload(_valid(status="在租"))
    assert fields is None
    assert any("空置" in message and "已租出" in message for message in errors), errors


def test_rejects_columns_that_do_not_exist_in_table():
    """本模块最重要的一条规则：houses 表没有的列，一律拒收。"""
    fields, errors = validate_payload(_valid(decoration="精装修"))
    assert fields is None
    assert any("houses 表" in message for message in errors), errors


def test_rejects_price_column_too():
    fields, errors = validate_payload(_valid(price=1680000))
    assert fields is None
    assert any("houses 表" in message for message in errors), errors


def test_rejects_non_object_top_level():
    for bad in ("一句话", 89.5, ["两室一厅"], None):
        fields, errors = validate_payload(bad)
        assert fields is None, f"{bad!r} 不该通过"
        assert any("JSON 对象" in message for message in errors), errors


# ----------------------------------------------------------- 定义本身的自洽
def test_column_map_covers_exactly_the_model_fields():
    """COLUMN_MAP 是给人看/给提示词用的映射表，不能和模型字段走散。"""
    mapped = {name for name, _, _ in COLUMN_MAP}
    assert mapped == set(HouseFields.model_fields)


def test_column_map_points_at_real_table_columns():
    for _name, column, _ddl in COLUMN_MAP:
        assert column.startswith("houses."), column


def test_schema_description_lists_every_field():
    text = schema_description()
    for name in HouseFields.model_fields:
        assert f'"{name}"' in text, f"{name} 没进提示词"


def test_status_literal_matches_core_constants():
    """status 的取值必须和 realestate-core 的 model.House 一致，不能多也不能少。"""
    literal = set(HouseFields.model_fields["status"].annotation.__args__)
    assert literal == {STATUS_VACANT, STATUS_RENTED} == {"空置", "已租出"}
