"""rag.py 里不联网的那部分：数据装载、文本拼装、文档构造。

``HouseRag`` 的构造要调向量化接口，属于联网部分，这里不碰——它的行为
已经在 demo_day2.py 跑真实链路时验证过了（包括「查询向量只算一次」这一点）。

这里测的是另一类东西：**数据文件本身有没有毛病**。这种问题很阴——
抽取器看着一切正常，检索却莫名其妙少几条，回头查半天才发现是数据里少个字段。
"""

from __future__ import annotations

from rag import DATA_FILE, build_documents, load_records, render_record
from schema import HouseFields, validate_payload


def test_data_file_exists():
    assert DATA_FILE.exists(), f"找不到数据文件 {DATA_FILE}"


def test_loads_all_records():
    assert len(load_records()) == 6


def test_record_ids_are_unique():
    ids = [record["id"] for record in load_records()]
    assert len(set(ids)) == len(ids), f"房源编号有重复：{ids}"


def test_every_record_passes_the_field_schema():
    """示例数据必须能过 schema.py 的校验。

    这保证演示用的数据和字段规则不会打架。一旦将来给字段加了新约束
    （比如收紧面积上限），这条测试会第一时间提醒「示例数据也得跟着改」。
    """
    for record in load_records():
        payload = {name: record[name] for name in ("type", "area", "address", "status")}
        fields, errors = validate_payload(payload)
        assert errors == [], f"{record['id']} 过不了字段校验：{errors}"
        assert fields is not None


def test_statuses_are_within_the_allowed_set():
    allowed = set(HouseFields.model_fields["status"].annotation.__args__)
    for record in load_records():
        assert record["status"] in allowed, record


def test_areas_are_positive():
    for record in load_records():
        assert record["area"] > 0, record


def test_rendered_text_carries_the_key_facts():
    record = load_records()[0]
    text = render_record(record)
    for expected in (
        record["id"],
        record["address"],
        record["type"],
        str(record["area"]),
        record["status"],
        record["remark"],
    ):
        assert expected in text, f"检索文本里少了 {expected!r}"


def test_render_record_works_without_remark():
    """remark 不在表结构里，是可选的补充说明，缺了也得能拼出文本。"""
    record = {
        "id": "H-9999",
        "type": "一室一厅",
        "area": 40.0,
        "address": "赣州市章贡区某路 1 号",
        "status": "空置",
    }
    text = render_record(record)
    assert "H-9999" in text
    assert "一室一厅" in text
    assert "40.0" in text


def test_document_metadata_matches_record():
    records = load_records()
    documents = build_documents(records)
    assert len(documents) == len(records)
    for record, document in zip(records, documents):
        assert document.metadata["id"] == record["id"]
        assert document.metadata["address"] == record["address"]
        assert document.metadata["type"] == record["type"]
        assert document.metadata["status"] == record["status"]
        assert document.page_content == render_record(record)


def test_data_file_is_readable_as_utf8():
    """按 UTF-8 读得到中文——编码写错的话会静默变成乱码，不报错。"""
    assert "赣州" in DATA_FILE.read_text(encoding="utf-8")
