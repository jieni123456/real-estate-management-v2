"""Day 2：用 LangChain 做最小 RAG——本地房源 → embedding → 检索问答。

刻意**不上向量数据库**。数据是 ``data/houses.json`` 里的 6 条房源，全部驻留内存，
检索就是一次暴力余弦相似度计算。理由（也是简历上真正能讲的那句话）见
``README.md`` 的「为什么这里不需要向量库」一节。

这里用到的 LangChain 组件只有最小的一组：

* ``Document``            —— 把一条房源表示成「正文 + 元数据」
* ``OpenAIEmbeddings``    —— 调通义千问的向量化接口
* ``InMemoryVectorStore`` —— 内存里的向量表（**不是数据库**，没有持久化、没有索引）
* ``ChatPromptTemplate``  —— 提示词模板
* LCEL（``|``）           —— 把「模板 → 模型 → 取文本」串成一条链

没有用到的、也刻意不用的：Agent / AgentExecutor、外部向量库、文档加载器全家桶、
以及各种 Retriever 的高级检索策略。数据只有 6 条，套上去只是多一层要维护的东西。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from langchain_core.documents import Document
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.vectorstores import InMemoryVectorStore
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from config import Settings

DATA_FILE = Path(__file__).resolve().parent / "data" / "houses.json"

# 提示词里有两条硬约束，都是为了压住大模型的「热心」：
# 一是只能依据检索到的资料回答，二是资料里没有就直说没有。
# 没有这两条，模型会拿训练时见过的赣州楼盘信息来补答，客户问 A 房它答 B 房。
PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "你是二手房中介管理系统里的房源助手。"
            "只能依据下面提供的房源资料回答问题。"
            "资料里没有提到的信息，就明确说「现有房源资料里没有」，不要凭常识补充，"
            "也不要推荐资料之外的房源。回答用简体中文，控制在三句话以内。",
        ),
        (
            "human",
            "房源资料：\n{context}\n\n客户问题：{question}",
        ),
    ]
)


def load_records() -> list[dict]:
    """读取本地房源数据。

    ``data/houses.json`` 的字段取自 houses 表（type / area / address / status），
    另外带一条 ``remark``——它**不在表结构里**，只用来让检索文本有足够的内容可谈
    （否则一段话只有「两室一厅 89.5 平方米 空置」几个词，语义检索没什么可检索的）。
    ``remark`` 不会写进数据库，也不构成任何接口字段。
    """
    records = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    if not isinstance(records, list) or not records:
        raise ValueError(f"{DATA_FILE} 应当是一个非空的房源数组")
    return records


def render_record(record: dict) -> str:
    """把一条房源记录拼成一段自然语言——embedding 的输入就是它。

    结构化字段拼成句子而不是直接喂 JSON，是因为向量模型见过的大量是自然语言，
    句子形态能让它把「陪读」「通勤」这类语义和房子关联起来。
    """
    parts = [
        f"房源编号 {record['id']}，位于{record['address']}。",
        f"户型为{record['type']}，建筑面积 {record['area']} 平方米，当前状态：{record['status']}。",
    ]
    if record.get("remark"):
        parts.append(str(record["remark"]))
    return "".join(parts)


def build_documents(records: list[dict] | None = None) -> list[Document]:
    records = records if records is not None else load_records()
    documents: list[Document] = []
    for record in records:
        documents.append(
            Document(
                page_content=render_record(record),
                # 元数据只放结构化字段，便于答案里能回指到具体房源。
                metadata={
                    "id": record["id"],
                    "address": record["address"],
                    "type": record["type"],
                    "area": record["area"],
                    "status": record["status"],
                },
            )
        )
    return documents


def build_embeddings(settings: Settings) -> OpenAIEmbeddings:
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.api_key,
        base_url=settings.base_url,
        timeout=settings.timeout_seconds,
        # 与 llm_client.py 同理：关掉 SDK 自带重试，重试权收归自己。
        max_retries=0,
        # ⚠ 这一行是接兼容端点必踩的坑，不能省。
        # langchain-openai 默认 check_embedding_ctx_length=True，会用 tiktoken 先把
        # 文本切成 token id 数组再发出去——那是 OpenAI 自家的私有约定。
        # 通义千问的兼容端点只接受原始字符串，收到 [[1234, 5678, ...]] 这种 int 数组
        # 会直接报参数错误。关掉它，才走「发纯文本」的通用路径。
        check_embedding_ctx_length=False,
    )


def build_llm(settings: Settings) -> ChatOpenAI:
    return ChatOpenAI(
        model=settings.chat_model,
        api_key=settings.api_key,
        base_url=settings.base_url,
        temperature=0.2,
        timeout=settings.timeout_seconds,
        max_retries=0,
    )


@dataclass(frozen=True)
class Answer:
    question: str
    text: str
    hits: list[tuple[Document, float]]   # (命中的房源, 相似度分数)


class HouseRag:
    """最小 RAG：建一次索引，之后可以反复问。"""

    def __init__(self, settings: Settings, *, top_k: int = 3) -> None:
        self._settings = settings
        self._top_k = top_k
        self._embeddings = build_embeddings(settings)
        self._llm = build_llm(settings)

        self._documents = build_documents()
        # 唯一的「索引」就是这个内存对象：一个 6 × 1024 的浮点数组。
        # add_documents 内部会调 embedding 接口，把 6 段文本一次性向量化。
        self._store = InMemoryVectorStore(embedding=self._embeddings)
        self._store.add_documents(self._documents)

        self._chain = PROMPT | self._llm | StrOutputParser()

    # ------------------------------------------------------------------ 检索
    @property
    def document_count(self) -> int:
        return len(self._documents)

    def embed_query(self, question: str) -> list[float]:
        """只算查询向量。**这一步要联网**——每次调用都是一次真实的接口请求。"""
        return self._embeddings.embed_query(question)

    def search_by_vector(
        self, vector: list[float], *, k: int | None = None
    ) -> list[tuple[Document, float]]:
        """用给定的查询向量做检索。**纯本地计算，不联网。**

        和 ``similarity_search_with_score`` 的差别就在这一步上：后者会先调一次
        向量化接口再算。把两者拆开，才能把「联网那部分」和「计算那部分」分开计时——
        而这两者的耗时对比，恰好就是「为什么不需要向量库」的论据。
        """
        k = k if k is not None else self._top_k
        return self._store.similarity_search_with_score_by_vector(vector, k=k)

    def retrieve(
        self, question: str, *, k: int | None = None
    ) -> list[tuple[Document, float]]:
        """检索（含查询向量化）。分数是**余弦相似度原值，越大越相关**。

        这一点是从 langchain-core 的实现里读出来的，不是猜的：
        ``similarity_search_with_score_by_vector`` 对全部文档算一遍余弦相似度，
        按降序取前 k 条，然后**原样返回相似度**——既没有取负号，也没有换算成距离。
        所以这里的分数不能用「越小越好」的直觉去读。
        """
        return self.search_by_vector(self.embed_query(question), k=k)

    def as_lcel_retriever(self):
        """LangChain 惯用写法：把向量表当检索器用。

        链式组合里要的是「给一个问题，返回若干 Document」，不需要分数，
        这时 ``as_retriever`` 更顺手。本项目的主路径刻意没走它——因为 demo 要的
        恰恰是分数：有了相似度才能看出命中的那条和第 2 名差多少，
        也才能判断「检索到底有没有真的起作用」。只拿 Document 是看不出这些的。
        """
        return self._store.as_retriever(search_kwargs={"k": self._top_k})

    # -------------------------------------------------------------------- 问
    def ask(
        self,
        question: str,
        *,
        k: int | None = None,
        hits: list[tuple[Document, float]] | None = None,
    ) -> Answer:
        """检索 + 生成答案。

        ``hits`` 可以传入外部已经算好的检索结果，避免查询向量被算两遍——
        每一次向量化都是一次真实的接口调用，重复算就是白花钱、白等一次往返。
        """
        if hits is None:
            hits = self.retrieve(question, k=k)
        context = "\n\n".join(
            f"【{doc.metadata['id']}】{doc.page_content}" for doc, _ in hits
        )
        text = self._chain.invoke({"context": context, "question": question})
        return Answer(question=question, text=text, hits=hits)
