"""房源字段的结构化定义与校验。

字段与取值**严格对齐** ``houses`` 表，不额外发明列。建表语句见
``realestate-core/src/main/java/dao/DatabaseUtil.java`` 第 159-166 行：

    CREATE TABLE houses (
        id          VARCHAR(50)  PRIMARY KEY,
        type        VARCHAR(50)  NOT NULL,
        area        DOUBLE       NOT NULL,
        address     VARCHAR(255) NOT NULL,
        landlord_id VARCHAR(50)  NOT NULL,
        status      VARCHAR(10)  NOT NULL DEFAULT '空置',
        FOREIGN KEY (landlord_id) REFERENCES landlords(id))

``id`` 与 ``landlord_id`` 刻意不进模型：前者是库内主键，后者必须指向一条真实
存在的房东记录——两者都不是「从一句自然语言里能听出来的信息」，让模型去猜只会
产出看起来合理、实际对不上库的假值。

因此可抽取字段是 4 个：``type`` / ``area`` / ``address`` / ``status``。

需要说明的是，「装修」「售价」这两个二手房场景里常提的属性在本项目的表结构里
**并不存在**（houses 表没有对应列），所以这里不抽——需求报告里登记为缺口
G-024，等表结构真的加上列再扩。宁可少抽两个字段，也不让简历上出现
「对接了装修/售价字段」这种对不上代码的话。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

# ---------------------------------------------------------------------------
# 取值常量：与 realestate-core 的 model.House 保持一致
# （House.STATUS_VACANT = "空置"，House.STATUS_RENTED = "已租出"），
# 不在这里另起一套字面量，避免两端的枚举值哪天各走各的。
# ---------------------------------------------------------------------------
STATUS_VACANT = "空置"
STATUS_RENTED = "已租出"

# 字段 → 它在 houses 表里的落点。给 README、演示输出和测试共用。
COLUMN_MAP: tuple[tuple[str, str, str], ...] = (
    ("type", "houses.type", "VARCHAR(50) NOT NULL"),
    ("area", "houses.area", "DOUBLE NOT NULL"),
    ("address", "houses.address", "VARCHAR(255) NOT NULL"),
    ("status", "houses.status", "VARCHAR(10) NOT NULL DEFAULT '空置'"),
)


class HouseFields(BaseModel):
    """从一句自然语言房源描述里抽出来的字段。"""

    model_config = ConfigDict(
        # 多出一个字段就直接判失败。这是本模块的核心立场：
        # 能落到库里的只能是表里真实存在的列，模型自由发挥出来的
        # decoration / price 之类一律不接受。
        extra="forbid",
        str_strip_whitespace=True,
    )

    type: str = Field(
        ...,
        min_length=1,
        max_length=50,
        description="户型，如「两室一厅」。对应 houses.type VARCHAR(50) NOT NULL",
    )
    area: float = Field(
        ...,
        gt=0,
        le=1000,
        description="建筑面积，单位平方米，只给数字。对应 houses.area DOUBLE NOT NULL",
    )
    address: str = Field(
        ...,
        min_length=1,
        max_length=255,
        description="房屋地址。对应 houses.address VARCHAR(255) NOT NULL",
    )
    status: Literal["空置", "已租出"] = Field(
        default=STATUS_VACANT,
        description="房屋状态。取值取自 model.House，对应 houses.status",
    )


# Literal 的参数必须是编译期字面量，没法直接写常量名，所以在这里把两者绑死：
# 谁改了上面的常量却忘了改字面量，import 这个模块时就会当场失败。
_LITERAL_STATUSES = set(HouseFields.model_fields["status"].annotation.__args__)  # type: ignore[union-attr]
assert _LITERAL_STATUSES == {STATUS_VACANT, STATUS_RENTED}, (
    "HouseFields.status 的字面量取值与 model.House 的常量已经不一致："
    f"{_LITERAL_STATUSES}"
)


# ---------------------------------------------------------------------------
# 把 pydantic 的英文报错翻成人话。
#
# 这些信息不只是给人看的——extract.py 的修复循环会把它们原样回喂给模型，
# 所以措辞要能指导模型「改哪里、怎么改」，而不是只说「校验失败」。
# ---------------------------------------------------------------------------
FIELD_LABELS: dict[str, str] = {
    "type": "户型（type）",
    "area": "面积（area）",
    "address": "地址（address）",
    "status": "状态（status）",
}

_ERROR_TEMPLATES: dict[str, str] = {
    "missing": "缺少该字段，必须提供",
    "extra_forbidden": "houses 表里没有这个列，请删掉它",
    "string_too_short": "内容为空，必须提供",
    "string_too_long": "超过 {limit} 个字符的长度上限",
    "string_type": "应为字符串",
    "float_parsing": "不是合法数字。面积请只给数字本身，不要带「平」「平方米」「㎡」等单位",
    "float_type": "应为数字，不要写成带单位的字符串",
    "int_parsing": "不是合法整数",
    "greater_than": "必须大于 {limit}",
    "less_than_equal": "不能大于 {limit}",
    "literal_error": "取值只能是「空置」或「已租出」",
}

# ctx 的键名在 pydantic 各版本间改过：string_too_long 早期用 limit，2.13 起叫
# max_length；数值边界则一直是 gt / le。这里统一映射成模板里的 {limit}，
# 模板就不用跟着 pydantic 的版本走。
_CTX_KEY_ALIASES: dict[str, str] = {
    "max_length": "limit",
    "min_length": "limit",
    "gt": "limit",
    "le": "limit",
}


def _as_display(value: Any) -> Any:
    """1000.0 显示成 1000——错误提示里的数字不需要拖一条浮点尾巴。"""
    if isinstance(value, float) and value.is_integer():
        return int(value)
    return value


def _describe(err: dict[str, Any]) -> str:
    """把 pydantic 的一条 error 渲染成中文说明。"""
    template = _ERROR_TEMPLATES.get(err.get("type", ""))
    if template is None:
        return str(err.get("msg", "校验未通过"))

    params: dict[str, Any] = {}
    for key, value in (err.get("ctx") or {}).items():
        params[_CTX_KEY_ALIASES.get(key, key)] = _as_display(value)

    try:
        return template.format(**params)
    except (KeyError, IndexError):
        # 模板里还有没被填上的占位符——说明 pydantic 又换了 ctx 的键名。
        # 这时退回原始消息，也好过把 "{limit}" 这种半成品丢给模型看。
        return str(err.get("msg", "校验未通过"))


def format_errors(exc: ValidationError) -> list[str]:
    """把 ValidationError 摊平成一行一条的中文提示。"""
    messages: list[str] = []
    for err in exc.errors():
        path = ".".join(str(part) for part in err.get("loc", ())) or "(顶层)"
        label = FIELD_LABELS.get(path, path)
        messages.append(f"{label}：{_describe(err)}")
    return messages


def validate_payload(payload: Any) -> tuple[HouseFields | None, list[str]]:
    """校验一个解析出来的 JSON 对象。

    返回 ``(模型对象, [])`` 表示通过；返回 ``(None, [错误说明])`` 表示不通过。
    不抛异常——「校验失败」在这个流程里是预期内的一种结果，不是程序缺陷。
    """
    if not isinstance(payload, dict):
        return None, [f"顶层必须是 JSON 对象，实际拿到的是 {type(payload).__name__}"]
    try:
        return HouseFields.model_validate(payload), []
    except ValidationError as exc:
        return None, format_errors(exc)


def schema_description() -> str:
    """给模型看的字段说明。

    直接遍历 ``COLUMN_MAP`` 生成，而不是在提示词里手抄一份字段清单——
    提示词和代码各写一份，迟早会各走各的。
    """
    lines = []
    for name, _column, _ddl in COLUMN_MAP:
        field = HouseFields.model_fields[name]
        lines.append(f'  - "{name}"：{field.description}')
    return "\n".join(lines)
