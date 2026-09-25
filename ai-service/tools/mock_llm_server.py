"""本地假模型服务：在没有 API Key 的情况下把整条链路走一遍。

它不是单元测试里那种 mock——它会**真的监听端口、真的收发 HTTP、真的按 OpenAI
协议返回 SSE 流**。所以能验证到「除了模型本身回答得好不好之外」的全部环节：

* 请求发得对不对：路径、鉴权头、`response_format`（JSON 模式）是否带上
* 流式响应的 SSE 解析是不是真的能一段一段拿到
* 向量化接口收到的是**纯文本还是 token id 数组**——这一条尤其关键，
  langchain-openai 默认会发 token id，通义千问的兼容端点不认（见 README 第六节）
* 重试在真实 HTTP 失败下的表现

它**不能**替代真实调用：向量是拿文本哈希播出来的伪随机数，没有语义，
所以检索命中是随机的；文本回复是照着关键词写的预设脚本。
这两件事只有接真模型才有意义。

用法（两个终端，或者一条命令里用 & 串起来）：

    python tools/mock_llm_server.py 8899
    LLM_BASE_URL=http://127.0.0.1:8899/v1 DASHSCOPE_API_KEY=sk-mock python demo_day1.py

加了 --fail-first N 之后，前 N 次 chat 请求会返回 503，
用来观察真实的 HTTP 失败下重试是不是按预期退避。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True, errors="replace")

EMBEDDING_DIM = 1024

TYPE_RE = re.compile(r"[一二两三四五六七八九十]室[一二两三四五六七八九十]厅")
AREA_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(?:个平方|平方米|平方|平|㎡)")
FALLBACK_AREA_RE = re.compile(r"(\d+(?:\.\d+)?)")
ADDRESS_RE = re.compile(r"([\u4e00-\u9fa5]+区[\u4e00-\u9fa50-9\s]+)")

STATE = {"chat_calls": 0, "fail_first": 0, "requests": []}


# ---------------------------------------------------------------------------
# 照关键词写死的「模型回答」
# ---------------------------------------------------------------------------
def _first_user_message(messages: list[dict]) -> str:
    """取第一条用户消息，也就是**原始的房源描述**。

    不能用「最后一条」——修复轮的最后一条是「你上一次的输出没有通过校验…」，
    拿它去抽字段只会抽出修复提示里的序号。真模型能看到完整对话历史，
    不会犯这个错；假服务要是不注意，就会表现得像代码有 bug。
    """
    for message in messages:
        if message.get("role") == "user":
            return str(message.get("content") or "")
    return ""


def _extract_fields(description: str) -> dict:
    """从描述里抓几个字段，让假服务的输出看起来像那么回事。"""
    type_match = TYPE_RE.search(description)
    area_match = AREA_RE.search(description) or FALLBACK_AREA_RE.search(description)
    address_match = ADDRESS_RE.search(description)
    return {
        "type": type_match.group(0) if type_match else "",
        "area": float(area_match.group(1)) if area_match else 0.0,
        "address": address_match.group(1).strip() if address_match else "",
        "status": "已租出" if "已租" in description else "空置",
    }


def decide_reply(messages: list[dict]) -> str:
    """按预设脚本决定这一轮返回什么。

    三条分支分别对应 demo_day1.py 的三个样例，目的是让三条代码路径都真的走一遍：
      含「老房子」    → 信息不足，返回 ok=false
      含「精装修」且是首轮 → 故意多输出表里没有的列，触发修复循环
      其余            → 正常返回
    """
    description = _first_user_message(messages)
    rounds_so_far = max(0, (len(messages) - 2) // 2)  # 首轮 messages 长度是 2

    system_text = next(
        (str(message.get("content") or "") for message in messages if message.get("role") == "system"),
        "",
    )
    # Day 2 的问答场景：system 提示词是 rag.py 里那一套，它要的是自然语言回答，
    # 不是抽取结果。假服务分不出检索到了什么，就回一句固定的话。
    if "房源助手" in system_text:
        return (
            "（这是假服务返回的固定回答，只用于验证链路，不代表真实效果。）"
            "按现有房源资料，上面列出的那几条可以看看。"
        )

    if "老房子" in description:
        return json.dumps(
            {
                "ok": False,
                "data": None,
                "missing": ["type", "area"],
                "reason": "原文只提到了楼层和位置，没有说户型和面积",
            },
            ensure_ascii=False,
        )

    fields = _extract_fields(description)

    if "精装修" in description and rounds_so_far == 0:
        # 首轮故意带上 decoration 与 price——houses 表里没有这两列，
        # 应当被 extra="forbid" 拦下，然后进入修复循环。
        polluted = dict(fields)
        polluted["decoration"] = "精装修"
        polluted["price"] = 1680000
        return json.dumps(
            {"ok": True, "data": polluted, "missing": [], "reason": ""},
            ensure_ascii=False,
        )

    return json.dumps(
        {"ok": True, "data": fields, "missing": [], "reason": ""},
        ensure_ascii=False,
    )


STREAM_TEXT = (
    "赣州章贡区这套 89.5 平的两室一厅，南北通透、楼层居中，"
    "小区对面就是赣州三中，走路两分钟，很适合陪读家庭。"
)


def _pseudo_embedding(text: str) -> list[float]:
    """确定性伪随机向量。

    同一个字符串每次都得到同一个向量，所以建库与查询是可复现的；
    但它**没有语义**——「两室一厅」和「三室两厅」在这里没有任何相似关系。
    想验证真正的语义检索，必须换成真实模型的向量接口。
    """
    seed = int.from_bytes(hashlib.sha256(text.encode("utf-8")).digest()[:8], "big")
    rng = random.Random(seed)
    vector = [rng.gauss(0.0, 1.0) for _ in range(EMBEDDING_DIM)]
    norm = sum(value * value for value in vector) ** 0.5 or 1.0
    return [value / norm for value in vector]


# ---------------------------------------------------------------------------
# HTTP 处理
# ---------------------------------------------------------------------------
class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MockLLM/1.0"

    def log_message(self, fmt: str, *args) -> None:  # 静音默认日志
        pass

    # ------------------------------------------------------------------ 出口
    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_sse(self, pieces: list[str]) -> None:
        """按 SSE 协议逐块发送，chunked 编码。"""
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()

        def write(payload: str) -> None:
            data = payload.encode("utf-8")
            self.wfile.write(f"{len(data):X}\r\n".encode("ascii") + data + b"\r\n")
            self.wfile.flush()

        for piece in pieces:
            chunk = {
                "id": "chatcmpl-mock",
                "object": "chat.completion.chunk",
                "created": int(time.time()),
                "model": "mock-model",
                "choices": [
                    {"index": 0, "delta": {"content": piece}, "finish_reason": None}
                ],
            }
            write(f"data: {json.dumps(chunk, ensure_ascii=False)}\n\n")
            time.sleep(0.03)  # 让流式的「一段一段」在终端上看得出来

        done = {
            "id": "chatcmpl-mock",
            "object": "chat.completion.chunk",
            "created": int(time.time()),
            "model": "mock-model",
            "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}],
        }
        write(f"data: {json.dumps(done, ensure_ascii=False)}\n\n")
        write("data: [DONE]\n\n")
        self.wfile.write(b"0\r\n\r\n")
        self.wfile.flush()

    # ------------------------------------------------------------------ 路由
    def do_POST(self) -> None:  # noqa: N802  （BaseHTTPRequestHandler 的约定命名）
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._send_json(400, {"error": {"message": "invalid json", "type": "invalid_request_error"}})
            return

        auth = self.headers.get("Authorization") or "(无)"
        STATE["requests"].append({"path": self.path, "body": body})

        if self.path.rstrip("/").endswith("/chat/completions"):
            self._handle_chat(body, auth)
        elif self.path.rstrip("/").endswith("/embeddings"):
            self._handle_embeddings(body, auth)
        else:
            print(f"[404] {self.path}")
            self._send_json(
                404, {"error": {"message": f"unknown path {self.path}", "type": "invalid_request_error"}}
            )

    def _handle_chat(self, body: dict, auth: str) -> None:
        STATE["chat_calls"] += 1
        call = STATE["chat_calls"]
        messages = body.get("messages") or []
        streaming = bool(body.get("stream"))

        print(
            f"[chat #{call}] 模型={body.get('model')} 流式={streaming} "
            f"JSON模式={'response_format' in body} 消息数={len(messages)} 鉴权={auth[:8]}..."
        )

        if call <= STATE["fail_first"]:
            print(f"           -> 按 --fail-first 返回 503（可重试）")
            self._send_json(
                503, {"error": {"message": "temporarily unavailable", "type": "server_error"}}
            )
            return

        if streaming:
            pieces = list(STREAM_TEXT)
            print(f"           -> 流式返回 {len(pieces)} 段")
            self._send_sse(pieces)
            return

        reply = decide_reply(messages)
        preview = reply[:70] + ("..." if len(reply) > 70 else "")
        print(f"           -> {preview}")
        self._send_json(
            200,
            {
                "id": "chatcmpl-mock",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": body.get("model") or "mock-model",
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": reply},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {"prompt_tokens": 120, "completion_tokens": 48, "total_tokens": 168},
            },
        )

    def _handle_embeddings(self, body: dict, auth: str) -> None:
        inputs = body.get("input")
        if isinstance(inputs, str):
            texts, shape = [inputs], "字符串"
        elif isinstance(inputs, list) and all(isinstance(item, str) for item in inputs):
            texts, shape = list(inputs), f"字符串数组({len(inputs)} 条)"
        elif isinstance(inputs, list) and inputs and isinstance(inputs[0], list):
            # 这就是 check_embedding_ctx_length=True 时会走到的分支：
            # langchain-openai 把文本用 tiktoken 编码成了 token id 数组。
            texts, shape = ["(token id 数组)"], f"⚠ token id 二维数组 {len(inputs)} 条"
        else:
            texts, shape = [], f"未知类型 {type(inputs).__name__}"

        print(f"[embeddings] 模型={body.get('model')} 鉴权={auth[:8]}...")
        print(f"             input 形态：{shape}")
        if shape.startswith("⚠"):
            print("             ^^ 通义千问的兼容端点不认这种形态，会直接报参数错误。")
            print("                这一行就是 README 第六节说的那个坑。")
        else:
            print(f"             首条文本：{texts[0][:50] if texts else '(空)'}")

        data = [
            {"object": "embedding", "index": index, "embedding": _pseudo_embedding(text)}
            for index, text in enumerate(texts)
        ]
        self._send_json(
            200,
            {
                "object": "list",
                "data": data,
                "model": body.get("model") or "mock-embedding",
                "usage": {"prompt_tokens": 10 * len(texts), "total_tokens": 10 * len(texts)},
            },
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="本地假模型服务")
    parser.add_argument("port", nargs="?", type=int, default=8899)
    parser.add_argument(
        "--fail-first",
        type=int,
        default=0,
        metavar="N",
        help="前 N 次 chat 请求返回 503，用来观察重试退避",
    )
    args = parser.parse_args()
    STATE["fail_first"] = args.fail_first

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"假模型服务已启动：http://127.0.0.1:{args.port}/v1")
    if args.fail_first:
        print(f"前 {args.fail_first} 次 chat 请求会被故意打成 503")
    print("按 Ctrl+C 停止。")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
