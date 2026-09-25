"""配置读取：全部来自环境变量 / .env，源码里不含任何密钥。

沿用项目根目录 ``db.properties.example`` 的既定策略：

* **密钥类配置不提供内置默认值**。缺失时 ``load_settings()`` 立刻抛
  ``ConfigError``，而不是带着一个空 Key 去发请求、再拿一个 401 回来倒推原因。
* 非密钥项（服务地址、模型名、超时、退避参数）给默认值，方便开箱即用。

环境变量一览见同目录 ``.env.example``。
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

# .env 与本源文件同级（ai-service/.env），已被 .gitignore 排除。
ENV_FILE = Path(__file__).resolve().parent / ".env"

# load_dotenv 默认不覆盖已存在的真实环境变量，符合「环境变量 > .env」的优先级。
load_dotenv(ENV_FILE)

DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
DEFAULT_CHAT_MODEL = "qwen-plus"
DEFAULT_EMBEDDING_MODEL = "text-embedding-v4"


class ConfigError(RuntimeError):
    """配置缺失或非法。让它一启动就抛出，而不是拖到第一次调用才失败。"""


def _read_int(name: str, default: int) -> int:
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise ConfigError(f"环境变量 {name} 必须是整数，当前值：{raw!r}") from exc


def _read_float(name: str, default: float) -> float:
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        return float(raw)
    except ValueError as exc:
        raise ConfigError(f"环境变量 {name} 必须是数字，当前值：{raw!r}") from exc


def _read_str(name: str, default: str) -> str:
    raw = os.getenv(name, "").strip()
    return raw or default


@dataclass(frozen=True)
class Settings:
    """一次调用所需的全部配置。冻结成不可变对象，避免被中途改写。"""

    api_key: str
    base_url: str
    chat_model: str
    embedding_model: str
    timeout_seconds: float
    max_attempts: int
    backoff_base: float
    backoff_max: float

    def masked_key(self) -> str:
        """脱敏后的 Key，用于打印日志。绝不原样输出到终端或文件。"""
        if len(self.api_key) <= 10:
            return "***"
        return f"{self.api_key[:6]}...{self.api_key[-4:]}"


def load_settings() -> Settings:
    """读取并校验配置。缺 Key 时抛出带操作步骤的 ConfigError。"""
    api_key = os.getenv("DASHSCOPE_API_KEY", "").strip()
    if not api_key:
        raise ConfigError(
            "缺少环境变量 DASHSCOPE_API_KEY，无法调用模型。请任选一种方式配置：\n"
            f"  方式一（推荐）在 {ENV_FILE} 中写入一行：\n"
            "          DASHSCOPE_API_KEY=sk-你的密钥\n"
            f"      （该文件已被 .gitignore 排除；若不存在，从 .env.example 复制一份）\n"
            "  方式二  在系统环境变量里设置同名变量。\n"
            "  申请地址 https://bailian.console.aliyun.com/ → 右上角「API-KEY」。"
        )

    max_attempts = _read_int("LLM_MAX_ATTEMPTS", 3)
    if max_attempts < 1:
        raise ConfigError(f"LLM_MAX_ATTEMPTS 至少为 1，当前值：{max_attempts}")

    timeout_seconds = _read_float("LLM_TIMEOUT_SECONDS", 30.0)
    if timeout_seconds <= 0:
        raise ConfigError(f"LLM_TIMEOUT_SECONDS 必须为正数，当前值：{timeout_seconds}")

    backoff_base = _read_float("LLM_BACKOFF_BASE", 1.0)
    backoff_max = _read_float("LLM_BACKOFF_MAX", 8.0)
    if backoff_base < 0 or backoff_max < 0:
        raise ConfigError("LLM_BACKOFF_BASE / LLM_BACKOFF_MAX 不能为负数")

    return Settings(
        api_key=api_key,
        base_url=_read_str("LLM_BASE_URL", DEFAULT_BASE_URL),
        chat_model=_read_str("CHAT_MODEL", DEFAULT_CHAT_MODEL),
        embedding_model=_read_str("EMBEDDING_MODEL", DEFAULT_EMBEDDING_MODEL),
        timeout_seconds=timeout_seconds,
        max_attempts=max_attempts,
        backoff_base=backoff_base,
        backoff_max=backoff_max,
    )
