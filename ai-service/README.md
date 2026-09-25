# ai-service

给二手房中介管理系统补的一块 AI 能力。**独立于 Java 后端运行，不与现有模块耦合**——
它不改数据库结构、不改 `realestate-core` 的任何代码，只是照着 `houses` 表的字段
定义，把一句自然语言房源描述抽成结构化字段。

## 它做什么

| 能力 | 代码位置 | 一句话说明 |
|---|---|---|
| 流式输出 | `llm_client.py` → `LLMClient.stream_chat` | 模型吐一段，这里就显示一段 |
| 结构化 JSON 输出 + 校验 | `schema.py`、`extract.py` | 固定 4 个字段，逐字段校验，不合格就回喂给模型修 |
| 超时重试 | `llm_client.py` | 临时故障指数退避重试；请求本身有问题就立刻收手 |
| 最小 RAG | `rag.py` | 本地房源 → embedding → 检索问答（不上向量库） |

## 目录

```
ai-service/
├─ .env.example        配置模板（占位符，可入库）
├─ .env                真实密钥（已 gitignore，不入库）
├─ requirements.txt    依赖，版本写死
├─ config.py           读环境变量；缺 Key 立刻报错，不留内置默认值
├─ schema.py           字段定义 + 校验 + 中文错误说明
├─ llm_client.py       流式 / 超时 / 可分辨的重试
├─ extract.py          房源描述 → 结构化字段的编排（含修复循环）
├─ rag.py              LangChain 最小 RAG
├─ demo_day1.py        Day 1 演示入口
├─ demo_day2.py        Day 2 演示入口
├─ data/houses.json    6 条示例房源
├─ tests/              70 项断言测试，不联网
└─ tools/
   ├─ mock_llm_server.py     本地假模型服务：没 Key 时也能把链路走一遍
   └─ bench_brute_force.py   暴力检索的规模-耗时实测
```

## 环境要求

- **Python 3.12 以上**。卡在 3.12 的是 `numpy 2.5.3`，它声明了 `requires-python >= 3.12`；
  而 numpy 在依赖链里（`langchain-core` 只把它列为可选依赖，得自己装）。
  其余依赖最低只要 3.10。本项目在 3.13 上跑通了全部测试。
- 一个通义千问（阿里云百炼）的 API Key。
- 网络：`dashscope.aliyuncs.com` 可直连，**不需要挂 VPN**。

> 顺带记一笔：本机 `pip` 直连 pypi.org 会超时，走国内镜像是必须的。
> 命令里都带了 `-i https://pypi.tuna.tsinghua.edu.cn/simple`。

## 安装

在**任意终端**里执行（下面的命令在 Git Bash 和 PowerShell 下都试过）：

```bash
cd E:/二手房中介管理系统/ai-service

python -m venv .venv

# Git Bash
source .venv/Scripts/activate
# PowerShell / cmd
# .venv\Scripts\activate

pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

**怎么算成功**：`pip install` 最后一行出现 `Successfully installed ...`，
并且 `python -c "import openai, pydantic, langchain_openai"` 没有报错。

## 配置

```bash
cp .env.example .env
```

然后编辑 `.env`，把 `DASHSCOPE_API_KEY` 换成自己的密钥。申请地址
<https://bailian.console.aliyun.com/> → 右上角「API-KEY」。

`.env` 已在 `.gitignore` 里，不会被提交。**代码里没有任何内置密钥，缺失时
`load_settings()` 会直接抛出带操作步骤的错误，而不是带着空 Key 去发请求。**

模型名也在这里改。如果报「模型不存在」，到百炼控制台的「模型广场」找一个
你账号可用的名字填进 `CHAT_MODEL` / `EMBEDDING_MODEL` 即可，代码不用动。

## 运行

```bash
# Day 1：流式输出 + 结构化抽取 + 校验
python demo_day1.py

# 额外演示超时重试（会故意失败几次，真的等待约 1 秒）
python demo_day1.py --retry-demo

# Day 2：本地房源检索问答
python demo_day2.py
python demo_day2.py "有没有带车位的房子？"
```

测试（不联网，随时可跑）：

```bash
python -m pytest tests -q
```

---

## 一、抽出来的字段为什么只有 4 个

`houses` 表的真实结构（`realestate-core/.../dao/DatabaseUtil.java:159-166`）：

```sql
CREATE TABLE houses (
    id          VARCHAR(50)  PRIMARY KEY,
    type        VARCHAR(50)  NOT NULL,
    area        DOUBLE       NOT NULL,
    address     VARCHAR(255) NOT NULL,
    landlord_id VARCHAR(50)  NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT '空置',
    FOREIGN KEY (landlord_id) REFERENCES landlords(id))
```

| 抽取字段 | 对应列 | 为什么抽 / 为什么不抽 |
|---|---|---|
| `type` | `houses.type` | 户型，如「两室一厅」 |
| `area` | `houses.area` | 面积，纯数字（下限 0，上限 1000） |
| `address` | `houses.address` | 地址 |
| `status` | `houses.status` | 取值只有「空置」「已租出」，照抄 `model.House` 的常量 |
| ~~`id`~~ | `houses.id` | 库内主键，不是「从一句话里能听出来的信息」 |
| ~~`landlord_id`~~ | `houses.landlord_id` | 必须指向一条真实存在的房东记录，让模型猜只会产出对不上库的假值 |
| ~~装修 / 售价~~ | **不存在** | **houses 表里根本没有这两列**，抽了也无处落库 |

最后一行是刻意的取舍。二手房场景里「精装修」「售价 168 万」是高频信息，模型也
确实会想抽出来——但本项目表结构里没有对应列。所以：

- `HouseFields` 设了 `extra="forbid"`，模型多输出一个 `decoration` / `price`，
  **整个响应会被判失败**，然后进入修复循环。
- `demo_day1.py` 的「样例 2」就是专门喂了一句带「精装修」「售价」的描述，
  用来看这条规则是否真的会触发。

宁可少抽两个字段，也不让代码里出现对不上表结构的字段。这条已作为缺口
`G-024` 登记在根目录 `需求报告.txt`，等表结构真加了列再扩。

## 二、流式与重试是冲突的，怎么办

重试的前提是「这次请求没成功」。但流式输出一旦已经把内容交给了调用方——
很可能已经打印到屏幕上了——再重试就会把同一段话重说一遍，用户看到的是
重复文本而不是错误提示。

`stream_chat` 的处理是：**只有在一个字都还没吐出去的时候才允许重试**。

```python
emitted = 0                      # 已经 yield 出去的片段数
...
except Exception as exc:
    if emitted == 0 and not last_attempt and is_retryable(exc):
        等待后重试                  # 什么都没发出去，重来是安全的
    raise                        # 已经输出过了，把异常交给调用方
```

对应两条测试：`test_stream_retries_when_nothing_emitted_yet` 与
`test_stream_does_not_retry_after_first_piece`。

## 三、重试怎么判断「值不值得」

一刀切的「失败就重试三次」是错的。这里分成两类：

| 类别 | 异常 | 处理 |
|---|---|---|
| 临时故障 | `APITimeoutError`、`APIConnectionError`、`RateLimitError`(429)、`InternalServerError`(5xx) | 指数退避重试 |
| 请求本身有问题 | `AuthenticationError`(401)、`PermissionDeniedError`(403)、`BadRequestError`(400)、`NotFoundError`(404) | **立刻收手**，重试只是白烧额度 |

退避：第 n 次失败后等 `base * 2^(n-1)` 秒，封顶 `cap`，再叠加 ±25% 随机抖动。
抖动不是装饰——多个调用方同时被限流时，固定间隔会让它们在同一时刻齐刷刷重试，
把刚缓过来的服务再打垮一次。

还有一行容易忽略的配置：

```python
OpenAI(..., max_retries=0)
```

`openai` SDK 默认自己重试 2 次，如果在外面再套一层 3 次重试，实际最多会发出
3×3=9 次请求，而你以为只发了 3 次。**必须把 SDK 自带的重试关掉，重试权收归一处。**

## 四、结构化输出怎么校验、失败了怎么办

用了 `response_format={"type": "json_object"}`（JSON 模式）。但要清楚它的边界：

> **JSON 模式只保证输出是「合法 JSON」，不保证字段对不对。**

字段名对不对、类型对不对、有没有多出表里没有的列——全是自己的活，
由 `schema.py` 的 pydantic 模型来管。失败之后的处理是**把中文错误说明回喂给模型**，
让它自己修，最多修 2 轮：

```
模型输出 → ① 合法 JSON？ → ② 外层信封结构对？ → ③ ok 是 false？ → ④ 字段逐个校验
             否↓              否↓                 是↓                否↓
          回喂错误重来      回喂错误重来        正常返回（不是错误）  回喂错误重来
```

第 ③ 步是最容易被忽略的一环。如果只准备「抽出来的字段」这一种输出形态，
那么遇到信息不足的描述时，模型只有两条路：瞎编，或者输出违反格式的东西。
所以外层留了一个信封结构，让模型有能力**如实说「我不知道」**：

```json
{"ok": false, "data": null, "missing": ["type", "area"], "reason": "原文只提到了楼层和位置"}
```

`demo_day1.py` 的「样例 3」就是这种情况。

## 五、为什么这里不需要向量库

Day 2 的数据是 `data/houses.json` 里的 **6 条**房源。检索就是一个内存数组上的
暴力余弦相似度计算——`InMemoryVectorStore` 的「索引」本质上就是一个 6×1024
的浮点矩阵，没有索引结构、没有落盘、没有独立服务。

**为什么不引入 Chroma / FAISS / Milvus？**

向量库解决的是这些问题：

| 向量库解决的问题 | 本项目的实际情况 |
|---|---|
| 数据量大到暴力检索太慢（10^5 条以上） | 6 条 |
| 需要**近似**最近邻（ANN）把检索从 O(N) 降到 O(log N) | N=6，O(N) 就是 6 次乘法 |
| 需要持久化，重启不用重新算 | 启动时 0.5 秒重算一遍，无所谓 |
| 多进程 / 多服务共享同一份索引 | 单进程，纯离线脚本 |
| 按元数据过滤 + 增量写入 | 数据是静态文件，改一次重新建库 |

关键的一点在这里：**ANN 是近似算法，它用召回率换速度。** 数据量小的时候，
暴力检索不但更简单，而且**结果更准**（精确解，不是近似解）。
这个规模下引入向量库，付出的是多一层依赖、多一个要运维的进程、多一次网络往返，
换来的收益是零——它是净负债，不是加分项。

**什么时候该上向量库**：数据量到了十万条量级以上、或者需要持久化和多实例
共享的时候。到那时把 `InMemoryVectorStore` 换成 `Chroma` 只要改两行——
这也正是「现在不上」的另一个理由：抽象层留在 LangChain 这一侧，
将来换存储是局部改动，不是重写。

### 实测数字，不是估计

跑 `python tools/bench_brute_force.py` 得到（1024 维，取前 3 条，
纯计算、不含网络，每组取 5 次里的最好成绩）：

| 数据量 | 耗时 | 相对 6 条 |
|---:|---:|---:|
| 6 | 0.004 ms | 1x |
| 100 | 0.010 ms | 3x |
| 1,000 | 0.137 ms | 38x |
| 10,000 | 1.067 ms | 296x |
| 100,000 | 10.030 ms | 2786x |

关键在最后一列：**数据量涨了一万七千倍，检索耗时也才到 10 毫秒**，
仍然小于「联网把问题向量化」那一趟（通常几十毫秒）。
也就是说，在十万条量级之前，检索的瓶颈根本不在向量比较上。

`demo_day2.py` 每次会打印两行耗时，是同一件事的现场版本：

```
向量化查询：   5.124 ms   （要联网，一次真实的接口调用）
向量比较  ：   0.536 ms   （纯本地，6 次余弦相似度）
```

现场那 0.536 ms 比表里的 0.004 ms 大，差的是 LangChain 封装里构建 Document
对象的固定开销——它是常数，不随数据量增长。

## 六、LangChain 用到了哪一步

用到的是它的**组件**和 LCEL 组合，不是它的**框架**——没有把整个应用交给
`AgentExecutor` 去编排。

| 组件 | 来自 | 做什么 |
|---|---|---|
| `Document` | langchain-core | 一条房源 = 正文 + 元数据 |
| `OpenAIEmbeddings` | langchain-openai | 调通义千问的向量化接口 |
| `InMemoryVectorStore` | langchain-core | 内存向量表，暴力余弦相似度 |
| `ChatPromptTemplate` | langchain-core | 提示词模板 |
| `ChatOpenAI` | langchain-openai | 对话模型 |
| `StrOutputParser` | langchain-core | 从返回结果里取纯文本 |
| LCEL（`\|`） | langchain-core | 把 `PROMPT \| llm \| parser` 串成一条链 |

刻意没用的：Agent / AgentExecutor、任何外部向量库、文档加载器全家桶、
多路召回与重排序、会话历史管理。6 条数据的问答套上这些，只是多几层要维护的抽象。

检索走的是 `similarity_search_with_score_by_vector` 而不是 `as_retriever`，
原因是有判断的：**要拿相似度分数**。有了分数才能看出命中的那条和第 2 名差
多少，也才能判断检索有没有真的起作用；`as_retriever` 只给 `Document`，看不出这些。
（`HouseRag.as_lcel_retriever()` 里留了惯用写法，需要时可以直接用。）

## 七、接兼容端点时必须加的那一行

```python
OpenAIEmbeddings(..., check_embedding_ctx_length=False)
```

`langchain-openai` 默认 `check_embedding_ctx_length=True`，会用 tiktoken 先把文本
切成 token id 数组再发出去——那是 OpenAI 自家的私有约定。通义千问的兼容端点
只接受原始字符串，收到 `[[1234, 5678, ...]]` 这种 int 数组会直接报参数错误。

这一行不加上，Day 2 是跑不起来的。

## 八、没有 API Key 的时候怎么验证

`tools/mock_llm_server.py` 是一个本地假模型服务。它不是单元测试里那种 mock——
它会真的监听端口、真的收发 HTTP、真的按 OpenAI 协议返回 SSE 流：

```bash
# 终端 1
python tools/mock_llm_server.py 8899

# 终端 2（Git Bash 的写法；PowerShell 里把 VAR=x cmd 换成分两行 $env:VAR="x"）
LLM_BASE_URL=http://127.0.0.1:8899/v1 DASHSCOPE_API_KEY=sk-mock python demo_day1.py
```

它能验证到「除了模型本身回答得好不好之外」的全部环节：

- 请求路径、鉴权头、`response_format`（JSON 模式）有没有正确带上
- 流式响应的 SSE 解析能不能真的做到一段一段
- 向量化接口收到的是**纯文本还是 token id 数组**（就是上一节那个坑）
- 真实 HTTP 失败下重试是不是按预期退避（加 `--fail-first 2`）

它**不能**替代真实调用：向量是拿文本哈希播出来的伪随机数，没有语义，
所以检索命中是随机的；文本回复是照着关键词写的预设脚本。
这两件事只有接真模型才有意义。
