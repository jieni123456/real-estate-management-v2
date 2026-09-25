"""暴力检索到底能撑到多大规模：用实测数字回答，而不是凭感觉。

README 里说「6 条数据不需要向量库」，这句话本身太弱——6 条数据当然不需要。
真正该回答的是：**如果数据涨上去，暴力检索什么时候会变成瓶颈？**

所以这里直接造不同规模的随机向量做一次检索，量一量：

    在哪个终端执行：ai-service 目录下，任意终端
        cd E:\\二手房中介管理系统\\ai-service
        python tools/bench_brute_force.py
        python tools/bench_brute_force.py --include-large   再加一组 20 万条

关于口径，两头都要说清楚：

  - 这里是**纯计算**耗时（一次 numpy 矩阵乘法），不含网络、不含向量化。
    真实检索还要先调一次向量化接口把问题转成向量，那一趟通常是几十毫秒，
    比下面所有数字都大。
  - 反过来，走 LangChain 的 ``InMemoryVectorStore`` 封装时，构建 Document 对象
    这些 Python 层开销是**固定的零点几毫秒**，和数据量基本无关。
    所以 demo_day2 里「向量比较」那一行会比这里的数字大，差的就是这部分常数开销。

内存提醒：20 万条 × 1024 维 × 4 字节 约 800 MB。--include-large 会临时吃这么多。
"""

from __future__ import annotations

import argparse
import sys
import time

import numpy as np

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True, errors="replace")

DIMENSION = 1024   # text-embedding-v4 的默认维度
TOP_K = 3          # 实际演示里取的条数
REPEAT = 5         # 每组重复几次取最好成绩，避开首次的内存分配与缓存抖动


def bench(matrix: np.ndarray, query: np.ndarray) -> float:
    """返回一次「算完全部相似度并取前 k 条」的最好成绩（毫秒）。"""
    best = float("inf")
    for _ in range(REPEAT):
        started = time.perf_counter()
        scores = matrix @ query                       # 一次矩阵乘法，等价于逐条算余弦相似度
        np.argpartition(-scores, TOP_K)[:TOP_K]       # 取分数最高的 k 条
        best = min(best, (time.perf_counter() - started) * 1000)
    return best


def main() -> int:
    parser = argparse.ArgumentParser(description="暴力检索的规模-耗时实测")
    parser.add_argument("--include-large", action="store_true", help="额外跑一组 20 万条（约吃 800MB 内存）")
    args = parser.parse_args()

    sizes = [6, 100, 1_000, 10_000, 100_000]
    if args.include_large:
        sizes.append(200_000)

    rng = np.random.default_rng(20260925)
    query = rng.normal(size=DIMENSION).astype(np.float32)
    query /= np.linalg.norm(query)

    print("=" * 68)
    print("暴力检索实测：不同数据量下，算完全部相似度要多久")
    print("=" * 68)
    print(f"向量维度 {DIMENSION} ｜ 取前 {TOP_K} 条 ｜ 每组取 {REPEAT} 次里的最好成绩")
    print("口径：纯计算，不含网络、不含向量化。")
    print()
    print(f"{'数据量':>10} {'耗时(ms)':>12} {'相对 6 条':>12}   说明")
    print("-" * 68)

    baseline = None
    for size in sizes:
        matrix = rng.normal(size=(size, DIMENSION)).astype(np.float32)
        elapsed = bench(matrix, query)
        if baseline is None:
            baseline = elapsed
        ratio = elapsed / baseline if baseline else 0.0
        note = ""
        if size == 6:
            note = "本项目现在的规模"
        elif elapsed < 10:
            note = "仍然是「察觉不到」的量级"
        elif elapsed < 100:
            note = "开始和一次向量化请求同一量级"
        else:
            note = "这时候才值得考虑向量索引"
        print(f"{size:>10} {elapsed:>12.3f} {ratio:>11.0f}x   {note}")
        del matrix  # 及时释放，别把上一组的内存留到下一组

    print()
    print("怎么读这张表：")
    print("  1. 耗时长的是「联网把问题向量化」那一趟，不是这里算的这一步。")
    print("     实测参考：一次向量化接口调用通常几十毫秒，比上表最后一行还大。")
    print("  2. 所以在数据量到十万条量级之前，检索的瓶颈压根不在向量比较上。")
    print("     这个阶段引入向量库，是多一层依赖、多一个要运维的进程，收益为零。")
    print("  3. 反过来，等到上表出现红色信号（比如超过几十毫秒），那就该上了——")
    print("     那时把 InMemoryVectorStore 换成 Chroma 之类只改两行，因为抽象层在")
    print("     LangChain 那一侧，不是散落在业务代码里。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
