"""Day 2 演示：本地房源 → embedding → 检索问答。

在哪个终端执行：任意终端，先进入 ai-service 目录。

    cd E:\\二手房中介管理系统\\ai-service
    python demo_day2.py                                    跑内置的几个问题
    python demo_day2.py "有没有带车位的房子？"              问一个问题

通过 / 失败怎么判断：
    先看到「已载入 N 条房源」和一次建库耗时，再看到每个问题的「检索命中」清单
    （带相似度）与「模型回答」。最要紧的是最后那两行耗时：
    「向量化查询」是联网的那一趟，「向量比较」是纯本地计算。
    两者的差距，就是「为什么不需要向量库」的直接理由。
"""

from __future__ import annotations

import sys
import time

from config import ConfigError, load_settings
from rag import HouseRag

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True, errors="replace")

LINE = "=" * 72
SUB = "-" * 72

DEFAULT_QUESTIONS = [
    "有没有章贡区空着、又离赣州三中很近的房子？",
    "我预算不高，想找个租金便宜点的小房子，有推荐吗？",
    "需要带车位的，另外我有时候要在家里办公。",
    "有没有已经装修好、能直接住的？",
]


def main() -> int:
    try:
        settings = load_settings()
    except ConfigError as exc:
        print(f"配置有问题：\n{exc}", file=sys.stderr)
        return 2

    questions = sys.argv[1:] or DEFAULT_QUESTIONS

    print(LINE)
    print("Day 2：最小 RAG（本地房源 + embedding + 检索问答）")
    print(LINE)
    print(f"对话模型：{settings.chat_model}")
    print(f"向量模型：{settings.embedding_model}")
    print(f"密钥：{settings.masked_key()}（已脱敏）")

    # 建库这一次要调向量化接口，所以会慢一点；之后每次检索都在本地算，不再联网。
    started = time.perf_counter()
    rag = HouseRag(settings)
    build_seconds = time.perf_counter() - started
    print(
        f"\n已载入 {rag.document_count} 条房源并完成向量化，"
        f"建库耗时 {build_seconds:.2f}s（含一次向量化接口调用）"
    )
    print("说明：索引就是一个内存数组，没有落盘、没有独立服务。")

    for index, question in enumerate(questions, 1):
        print()
        print(LINE)
        print(f"问题 {index}：{question}")
        print(LINE)

        # 分开计时。这两个数字必须分开看：
        #   向量化查询 —— 要联网，是一次真实的接口请求
        #   向量比较   —— 纯本地计算，完全不联网
        # 混在一起计的话，「检索本身有多快」会被联网耗时盖住，看不出瓶颈到底在哪。
        started = time.perf_counter()
        query_vector = rag.embed_query(question)
        embed_ms = (time.perf_counter() - started) * 1000

        started = time.perf_counter()
        hits = rag.search_by_vector(query_vector)
        search_ms = (time.perf_counter() - started) * 1000

        print(f"检索命中（全量 {rag.document_count} 条中取前 {len(hits)} 条）：")
        for doc, score in hits:
            meta = doc.metadata
            print(
                f"  [{meta['id']}] 相似度 {score:+.4f}  "
                f"{meta['type']} / {meta['area']} 平 / {meta['status']}"
            )
            print(f"            {meta['address']}")

        print(f"\n  向量化查询：{embed_ms:8.3f} ms   （要联网，一次真实的接口调用）")
        print(f"  向量比较  ：{search_ms:8.3f} ms   （纯本地，{rag.document_count} 次余弦相似度）")
        print(
            "  看两行的差距就知道瓶颈在哪：贵的是联网那一趟，不是向量比较本身。\n"
            f"  这 {rag.document_count} 条数据下，检索耗时连误差都算不上。\n"
            "  想看它到什么量级才会真的变成瓶颈，跑 tools/bench_brute_force.py。"
        )

        # 复用已经算好的 hits，避免查询向量被算第二遍
        # （每一次向量化都是一次真实的接口调用，重复算就是白花一次钱和一次往返）。
        answer = rag.ask(question, hits=hits)
        print(f"\n模型回答：{answer.text}")

    print()
    print(LINE)
    print("完成。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
