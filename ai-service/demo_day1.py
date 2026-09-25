"""Day 1 演示：流式输出、结构化抽取与校验、超时重试。

在哪个终端执行：任意终端（cmd / PowerShell / Git Bash 都能跑），
但必须先进入 ai-service 目录，否则 import 找不到同级的 config.py。

    cd E:\\二手房中介管理系统\\ai-service
    python demo_day1.py               只跑 ① ②，会调用模型 4 次左右
    python demo_day1.py --retry-demo  额外跑 ③，会再失败几次（重试演示故意如此）

通过 / 失败怎么判断：
    看到「首段到达」「通过校验」「重试决策」三类文字陆续打出来即为正常。
    唯一会中断整个脚本的是配置或鉴权问题（缺 Key / Key 写错），
    这时会打印一段带操作步骤的提示，而不是一串 stack trace。
"""

from __future__ import annotations

import argparse
import sys
import time
from dataclasses import replace

from config import ConfigError, Settings, load_settings
from extract import extract_house
from llm_client import LLMClient, RetryEvent
from schema import COLUMN_MAP

# Windows 控制台默认给 stdout 上块缓冲，流式效果会变成「憋到最后一次性吐出」。
# 这里只调缓冲和编码容错，不改编码——改了会和终端的代码页对不上，反而变乱码。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True, errors="replace")

LINE = "=" * 72
SUB = "-" * 72

# 三个样例是刻意挑的，各负责演示一条路径：
#   样例 1  字段齐全     → 一轮通过
#   样例 2  含「精装修」「售价」→ 诱导模型输出表里没有的列，触发 extra=forbid 与修复循环
#   样例 3  只说了楼层和位置 → 信息不足，模型应当如实判 ok=false 而不是编数字
SAMPLES: list[tuple[str, str]] = [
    (
        "字段齐全",
        "章贡区长征大道锦绣花园 3 栋 802，两室一厅，89.5 个平方，南北通透，"
        "中间楼层，现在空着随时能看。",
    ),
    (
        "含表外字段",
        "章贡区红旗大道万象公馆 1 栋 1503，三室两厅 128 平，精装修，家电齐全，"
        "带地下车位，售价 168 万。",
    ),
    (
        "信息不足",
        "南康区那边有套老房子，楼下就是菜市场，六楼没电梯，租金便宜。",
    ),
]


def _print_retry(event: RetryEvent) -> None:
    if event.will_retry:
        print(
            f"    [重试] 第 {event.attempt} 次尝试失败：{event.error}\n"
            f"           等待 {event.delay:.2f}s 后重试"
        )
    else:
        print(f"    [放弃] 第 {event.attempt} 次尝试失败：{event.error}（不再重试）")


# ---------------------------------------------------------------------------
# ① 流式输出
# ---------------------------------------------------------------------------
def demo_stream(client: LLMClient) -> None:
    print(LINE)
    print("① 流式输出：模型吐一段，立刻显示一段")
    print(LINE)

    messages = [
        {
            "role": "system",
            "content": "你是二手房中介的房源文案助手，回答控制在 80 字以内。",
        },
        {
            "role": "user",
            "content": "用一句话介绍赣州章贡区一套 89.5 平的两室一厅，突出适合陪读家庭。",
        },
    ]

    started = time.perf_counter()
    first_piece_at: float | None = None
    pieces = 0

    print("模型输出：", end="", flush=True)
    for piece in client.stream_chat(messages):
        if first_piece_at is None:
            first_piece_at = time.perf_counter() - started
        pieces += 1
        print(piece, end="", flush=True)
    total = time.perf_counter() - started
    print("\n")
    print(f"  首段到达：{first_piece_at:.2f}s      全部完成：{total:.2f}s      共 {pieces} 段")
    print("  （这两个数字的差值就是流式的价值：用户不用等完整回答生成完才看到东西）")


# ---------------------------------------------------------------------------
# ② 结构化抽取 + 校验
# ---------------------------------------------------------------------------
def _print_fields(fields) -> None:
    print("  抽取结果（左列是 houses 表的列，右列是本次抽到的值）：")
    for name, column, ddl in COLUMN_MAP:
        value = getattr(fields, name)
        print(f"    {column:<16} {str(value):<28} {ddl}")


def demo_extract(client: LLMClient) -> None:
    print()
    print(LINE)
    print("② 结构化 JSON 输出：固定字段 + 逐字段校验 + 失败修复")
    print(LINE)

    for index, (label, description) in enumerate(SAMPLES, 1):
        print(f"\n{SUB}")
        print(f"样例 {index}（{label}）：{description}")
        print(SUB)

        result = extract_house(description, client)

        for round_no, raw in enumerate(result.raw_outputs, 1):
            tag = "模型原始输出" if round_no == 1 else f"第 {round_no} 轮修正输出"
            print(f"  {tag}：{raw[:160]}{'...' if len(raw) > 160 else ''}")
            if round_no - 1 < len(result.errors) and result.errors[round_no - 1]:
                for message in result.errors[round_no - 1]:
                    print(f"    [!] 校验未通过：{message}")

        if result.ok:
            print()
            _print_fields(result.fields)
            if result.repaired:
                print(f"  [OK] 经 {result.rounds} 轮修复后通过校验")
            else:
                print("  [OK] 一轮通过校验")
        else:
            print(f"  [--] 未产出可用字段：{result.note}")

        print(
            f"  耗时 {result.duration:.2f}s ｜ 轮数 {result.rounds} ｜ "
            f"tokens 入 {result.prompt_tokens} / 出 {result.completion_tokens}"
        )


# ---------------------------------------------------------------------------
# ③ 超时与重试
# ---------------------------------------------------------------------------
def demo_retry(settings: Settings) -> None:
    print()
    print(LINE)
    print("③ 超时重试：可重试的等一等再试，不可重试的立刻收手")
    print(LINE)

    probe = [{"role": "user", "content": "你好"}]

    # 场景 A：把单次超时压到 1 毫秒，必然触发超时。
    # 这里验证的是「临时性故障会重试」——注意它会真的等 0.2+0.4 秒。
    print(f"\n{SUB}")
    print("场景 A：单次请求超时（把超时压到 0.001 秒，必然触发）")
    print("         预期：超时属于临时故障，应当重试，用满 3 次尝试才放弃")
    print(SUB)
    impatient = replace(
        settings,
        timeout_seconds=0.001,
        backoff_base=0.2,
        backoff_max=0.5,
    )
    started = time.perf_counter()
    try:
        LLMClient(impatient, on_retry=_print_retry).complete_json(probe)
        print("    意外地成功了——说明网络快到这个超时都追不上，可把值调得更小再试")
    except Exception as exc:
        print(f"    最终结果：{type(exc).__name__}（耗时 {time.perf_counter() - started:.2f}s）")

    # 场景 B：故意用错的 Key，拿到 401。
    # 这里验证的是「重试判断不是一刀切」——同样失败，401 一次都不该多试。
    print(f"\n{SUB}")
    print("场景 B：Key 写错（401）")
    print("         预期：属于请求本身的问题，重试多少次都一样，应当只尝试 1 次")
    print(SUB)
    wrong_key = replace(settings, api_key="sk-this-key-is-deliberately-wrong")
    started = time.perf_counter()
    try:
        LLMClient(wrong_key, on_retry=_print_retry).complete_json(probe)
        print("    意外地成功了——那说明这个 Key 居然有效，请检查配置")
    except Exception as exc:
        print(f"    最终结果：{type(exc).__name__}（耗时 {time.perf_counter() - started:.2f}s）")


def main() -> int:
    parser = argparse.ArgumentParser(description="Day 1 演示")
    parser.add_argument(
        "--retry-demo",
        action="store_true",
        help="额外演示超时重试（会故意失败几次，并真的等待约 1 秒）",
    )
    parser.add_argument(
        "--only",
        choices=["stream", "extract", "retry"],
        help="只跑其中一项",
    )
    args = parser.parse_args()

    try:
        settings = load_settings()
    except ConfigError as exc:
        print(f"配置有问题：\n{exc}", file=sys.stderr)
        return 2

    print(f"模型服务：{settings.base_url}")
    print(f"对话模型：{settings.chat_model}")
    print(f"密钥：{settings.masked_key()}（已脱敏）")
    print(f"单次超时：{settings.timeout_seconds}s ｜ 最多尝试：{settings.max_attempts} 次")
    print()

    client = LLMClient(settings, on_retry=_print_retry)

    if args.only in (None, "stream"):
        demo_stream(client)
    if args.only in (None, "extract"):
        demo_extract(client)
    if args.only == "retry" or (args.retry_demo and args.only is None):
        demo_retry(settings)

    print()
    print(LINE)
    print("完成。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
