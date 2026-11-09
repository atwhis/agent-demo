# agent-demo

用于学习 Spring AI 2.0 Tool Calling、意图路由和纯文本 RAG 的双服务 Demo。大模型负责理解自然语言、选择 Tool 和解释结果；销售服务负责计算全部业务数字；AI 服务把 Tool 的结构化结果编译成安全的 ChartSpec，并通过 SSE 向原生前端发送最终文字和图表。

## 模块

```text
agent-demo
├── agent-demo-sales
│   ├── agent-demo-sales-client   # Feign/MVC 公共契约和 DTO
│   └── agent-demo-sales-service  # MySQL、业务规则、统计 API、演示数据
└── agent-demo-ai
    └── agent-demo-ai-service     # 双模型、会话窗口、工具记录、SSE、静态前端
```

运行进程只有 `agent-demo-sales-service` 和 `agent-demo-ai-service`。AI 服务只依赖 Sales Client，不依赖 Sales Service 实现。

## 环境要求

- JDK 21
- Maven 3.8+
- Docker Desktop / Docker Compose
- DeepSeek 和 OpenAI API Key（只在运行 AI 服务时需要；编译与离线测试不需要）

## 启动依赖环境

```bash
cp .env.example .env
printf 'NACOS_AUTH_TOKEN=%s\n' "$(openssl rand -base64 32)" >> .env
docker compose up -d
docker compose ps
docker compose logs -f mysql nacos pgvector
```

默认端口为 MySQL `3306`、Nacos 控制台 `8080`、Nacos 服务端 `8848`、Nacos gRPC `9848` 和本机 PgVector `5432`。三个容器都有健康检查，Nacos 在 MySQL 健康后启动。

同一个 MySQL 8.4 实例中会创建两个物理数据库，并由 `.env` 中同一个非 root 账号访问：

- `agent_demo_sales`：销售业务表与最近 90 天演示数据
- `nacos_config`：Nacos 配置、命名空间、用户等服务端数据

Nacos 不再使用内置 Derby，也不把数据库账号密码配置到 Nacos 自身。Compose 直接通过 Nacos 官方 Docker 环境变量传入连接信息，JDBC 使用 TLS 完成 MySQL 8.4 的密码认证。`docker/mysql/init/02-nacos-mysql-schema.sql.source` 来自 Nacos Server `3.2.3` 标签的官方 MySQL schema，项目不自行维护表结构变体；使用 `.source` 后缀是为了只由前一个初始化脚本导入 `nacos_config`，避免 MySQL 镜像把它再次自动导入默认销售库。

普通停止不会删除数据：

```bash
docker compose down
```

正常 `down` / `up` 会保留 MySQL 和 PostgreSQL 命名卷。`down -v` 会同时删除销售数据、Nacos 配置与知识库资料，只有明确需要全部重建时才执行。

```bash
docker compose down -v
docker compose up -d
```

## Nacos 配置

项目使用 `spring.config.import`，不使用 `bootstrap.yml`。在 Nacos 控制台的 `AGENT_DEMO` Group 中创建：

- `agent-demo-sales-service-local.yaml`：复制 `config/nacos/agent-demo-sales-service-local.yaml`
- `agent-demo-ai-service-local.yaml`：复制 `config/nacos/agent-demo-ai-service-local.yaml`

应用自己的 `application.yml` 只保留应用名、Nacos 地址/Group/Namespace、`spring.config.import` 和少量框架不变量。端口、销售数据源、演示数据开关、模型地址与模型名等普通运行配置放在上述 Nacos Data ID 中。

示例文件只含非敏感配置和环境变量占位符。MySQL 密码、DeepSeek API Key、OpenAI API Key 不得以真实值写入 Nacos、代码、`.env.example` 或浏览器，应通过对应 Java 服务进程的环境变量传入。Nacos 自己启动所需的 MySQL 密码由 Compose 从本地 `.env` 传给容器，这部分无法由尚未启动的 Nacos 提供。

本地默认关闭 Nacos 客户端认证。Nacos 3.2.3 的 Docker 启动脚本仍要求 `NACOS_AUTH_TOKEN` 非空。按上面的命令生成解码后为 32 字节的 Base64 密钥，仅保存到本地 `.env`。已有 `.env` 时，在原文件中新增或补全该变量，再运行 `docker compose up -d --no-deps nacos` 使新配置生效。

销售服务的 local Data ID 默认打开学习用 SQL 日志：

- `org.springframework.jdbc.core.JdbcTemplate=DEBUG`：打印 SQL 模板
- `org.springframework.jdbc.core.StatementCreatorUtils=TRACE`：打印绑定参数

可在 Nacos 中把两项改为 `OFF`，或设置 `JDBC_SQL_LOG_LEVEL=OFF`、`JDBC_PARAMETER_LOG_LEVEL=OFF`。其他环境的 Data ID 应默认关闭。项目没有引入 P6Spy，也不统计 SQL 耗时。

## 编译与测试

Java 测试源文件保留在本地工作区。以下测试命令适用于已经有这些文件的工作区。

```bash
mvn clean package
```

AI 模块的离线测试使用假模型和假销售 Client，不需要 API Key。前端请求隔离测试使用 Node.js，无需安装 npm 依赖：

```bash
mvn -pl agent-demo-ai/agent-demo-ai-service -am test
node agent-demo-ai/agent-demo-ai-service/src/test/js/conversation-ui.test.cjs
```

只运行销售服务测试：

```bash
mvn -pl agent-demo-sales/agent-demo-sales-service -am test
```

当 Compose 与销售服务均已启动时，可额外验证 AI 模块通过 Nacos 服务名发现并 Feign 调用销售服务（正常 package 会跳过这项外部环境测试）：

```bash
RUN_NACOS_SMOKE=true AI_MODELS_ENABLED=false \
  mvn -pl agent-demo-ai/agent-demo-ai-service -Dtest=NacosFeignSmokeTest test
```

销售测试使用 H2 的 MySQL 兼容模式，覆盖应用层金额、库存与逻辑引用规则、四类统计、状态过滤、连续补零、Feign 契约以及 90 天初始化数据，不依赖 Docker 或真实模型。六张业务表统一使用 `t_` 前缀；DDL 不含外键和 `CHECK`，但保留主键、非空、默认值、唯一索引和查询索引。

## 启动服务

先启动销售服务（仅在业务库为空时，按固定随机种子初始化相对当前日期的最近 90 天数据）：

```bash
mvn -pl agent-demo-sales/agent-demo-sales-service spring-boot:run
```

再开一个终端，通过环境变量启动 AI 服务：

```bash
export DEEPSEEK_API_KEY='your-deepseek-key'
export OPENAI_API_KEY='your-openai-key'
export KNOWLEDGE_DB_PASSWORD='agent_demo' # 与本地 Compose 的知识库密码一致
mvn -pl agent-demo-ai/agent-demo-ai-service spring-boot:run
```

若缺少任一 Key，AI 服务会在启动阶段失败，但不会打印 Key 值。打开 [http://localhost:8082](http://localhost:8082) 使用页面；模型列表为 DeepSeek V4 Flash 和 OpenAI GPT-5.6 Terra，切换只影响下一次请求。

在 IntelliJ IDEA 中，可打开对应 Spring Boot Run Configuration，在 `Environment variables` 中配置：

```text
MYSQL_USER=agent_demo;MYSQL_PASSWORD=你的本地MySQL密码
```

AI 服务再增加：

```text
DEEPSEEK_API_KEY=你的DeepSeekKey;OPENAI_API_KEY=你的OpenAIKey;KNOWLEDGE_DB_PASSWORD=你的PostgreSQL密码
```

Compose 的 `.env` 只用于容器配置。用 IDEA 启动 AI 服务时，请在 `AiServiceApplication` 的环境变量中设置同一个 `KNOWLEDGE_DB_PASSWORD`。修改后重新运行 AI 服务。

macOS/Linux 终端使用冒号无关，IDEA 的变量编辑器既支持逐项填写，也支持上述分号分隔格式。不要把 Run Configuration 共享到 Git。

## 演示数据为什么由 Java 生成

项目保留 `DemoDataInitializer`，而不维护一份很大的历史 `data.sql`。初始化器仅在 `t_customer` 为空时运行，固定随机种子使分布可重复，而日期以启动当天为基准生成最近 90 天，因此“最近一个月/三个月”的示例不会随静态 SQL 逐渐过期。重复启动不会再次插入数据；要重新生成，需明确执行前述 `docker compose down -v`。

## 请求链路

```text
浏览器 POST SSE
  → start {conversationId, turnId}
  → 所选模型读取同会话最近完整消息，判断路由并补全问题
      SALES   → 现有销售 Tool + Feign → delta* → chart?
      RAG     → 本地 Embedding → PgVector Top15 → 本地重排 Top5 → delta* → sources?
      CLARIFY → 发送澄清 delta
  → 成功提交消息对，再发送 done {conversationId, turnId, route}
  → 失败发送 error，取消直接结束
```

前端不会显示推理内容、Tool 名称、Tool 参数、Feign 信息或中间结果。Markdown 经过 DOMPurify 清理；图表仅接受 `bar`、`pie`、`line` 三类声明式协议，模型不能生成或执行 ECharts option、函数或 JavaScript。

AI 服务以 `chat_timing` 记录毫秒耗时。按 `turnId` 可以串起意图识别、各阶段及服务端完成或取消整轮请求的日志。

- RAG 分开记录问题向量化、PgVector TopK 查询、重排选取 TopN 和模型生成。无候选或证据时，未执行的阶段标为 `SKIPPED`。
- SALES 记录每次工具调用的工具名、`callId` 和结果状态。模型流总耗时包含工具循环。最终回答耗时从最后一次工具返回算到模型流结束。

## 示例问题

| 问题 | Tool | 图表 |
|---|---|---|
| 查询最近一个月购买商品数量最多的客户排行 | 客户购买排行 | 横向柱状图 |
| 查询最近三个月商品分类销售额占比 | 分类销售占比 | 饼图 |
| 最近一个月最受欢迎的具体商品排行 | 热门商品排行 | 横向柱状图 |
| 最近三个月按周统计销售额趋势 | 销售趋势（WEEK） | 折线图 |

统计只包含 `PAID` 和 `COMPLETED` 订单，日期区间包含开始日和结束日。业务问题及“只看前三名”等明确续问必须有本轮成功销售调用；成功空集正常说明无数据且不生成图表，工具失败或缺少当前轮证据时不会发送成功 `done`。历史数字不能代替重新查询。

## 多轮会话与调用记录

在同一页面依次提问“最近一个月最受欢迎的具体商品排行”→“只看前三名”→“改成最近三个月”，模型继承未修改的条件，每轮重新查询。切换模型后继续使用相同会话窗口。点击顶部“新会话”会停止旧流、清空消息和草稿，并请求清理旧会话；清理失败会在状态栏提示。

- 每个 conversationId 在内存中保留最近 10 个成功 user／assistant 消息对。历史最多 20 条，并以 UTF-8 24,000 字节预算整对裁剪后送入模型；这是 Demo 的保守预算，不做摘要和精确 tokenizer 计数。
- 同会话只允许一个活动轮次，重复提交返回 `CONVERSATION_BUSY`；不同会话独立执行。
- 失败、取消、半截回答不加入消息窗口。另按会话保留最近 10 次已接纳请求的调用记录，包含失败和取消，超出后淘汰。
- turnId 与 traceId 相同；每次工具执行有独立 callId、参数、结果、行数、状态、起止时间和耗时。展示工具也独立记录。图表 `sourceCallId` 指向实际来源，`sourceTool` 和版本 `1.0` 保留。
- 消息、调用记录和活动状态全部在重启后清空。新会话会删除旧状态；取消能阻止迟到结果写回，但不保证立即中断已发出的 Feign 查询。

调试接口（标识从 `start`／`done` 事件取得）：

```bash
# 读取对应轮次和全部调用；不存在、已淘汰或会话不匹配均为 404
curl http://localhost:8082/api/conversations/CONVERSATION_ID/turns/TURN_ID

# 幂等清理消息、调用记录和活动写入资格；成功为 204
curl -X DELETE http://localhost:8082/api/conversations/CONVERSATION_ID
```

这些接口只用于本地 Demo 调试，普通助手页面不展示原始调用记录；当前没有用户认证。RAG 轮次记录还包含补全后的问题、候选向量分与重排分，以及最终片段和来源编号。

## 导入与知识问答

1. 点击输入区左下的“导入文本”，粘贴正文或选择 UTF-8 TXT 文件。选中文件时会显示文件名和大小，并默认用文件名填写标题。点击“确认导入”后由后端读取文件并建立索引。用途可自动识别，或指定为制度规则、产品说明、操作指南、其他。
2. 试着导入“销售额只统计已支付和已完成的订单，取消订单不计入”，再问“哪些订单状态计入销售额？”。有依据的回答会附上可以展开的来源。
3. 问“按销售统计制度解释上个月销售趋势”，助手会先请你选择查实际数据还是了解规则。回答“先查实际数据”后会沿用上个月的时间条件。

标题最多 80 字，正文最多 2 MiB（2,097,152 字节）。粘贴和 TXT 上传分别走 `POST /api/knowledge/documents/text` 和 `POST /api/knowledge/documents/upload`。两者都接收 `title`、`documentType` 和 `modelId`，正文分别使用 JSON 的 `content` 和 multipart 的 `file`。重复正文返回原文档的标题、类型和片段数。点击“新会话”清除内存对话，PostgreSQL 里的文档与向量可继续查询。

Nacos 示例中的 `agent-demo.ai.knowledge` 提供连接地址、本地模型目录、切片参数和检索开关。若修改 Compose 端口、库名或用户名，也应为 AI 进程设置对应的 `KNOWLEDGE_DB_URL` 和 `KNOWLEDGE_DB_USER`。首次使用知识库时由 AI 服务建立表和 HNSW 余弦索引，开发数据库账号需要创建扩展和表的权限。

### 本地模型与验收

| 模型 | 运行所需文件 |
|---|---|
| [BGE small 中文 v1.5 ONNX](https://huggingface.co/Xenova/bge-small-zh-v1.5) | `models/embedding/model.onnx`、`model.onnx_data`、`tokenizer.json` |
| [mMARCO mMiniLMv2 ARM64 INT8 ONNX](https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1) | `models/reranker/model_qint8_arm64.onnx`、`tokenizer.json` |

两目录位于 AI 模块的 `src/main/resources`。运行模型所需文件由开发者放到对应目录，Git 只跟踪这两个目录里的 README。ONNX Runtime 1.24.3 和 DJL Tokenizers 0.36.0 在 Apple Silicon 原生 Java 21.0.12 上通过中文推理和打包 JAR 资源展开验证。单次冒烟中，两条 Embedding 用时约 30 ms，两对重排约 10 ms，相关／无关样例的原始 logit 分别约为 0.134 和 −8.524。默认筛选门槛为 −3，可按实际资料样本校准。

```bash
# Apple Silicon 原生 JDK 21，本地模型冒烟
RUN_ONNX_SMOKE=true mvn -pl agent-demo-ai/agent-demo-ai-service -am test \
  -Dtest=LocalOnnxSmokeTest -Dsurefire.failIfNoSpecifiedTests=false

# 验证打包资源展开时，先执行 package，再设置 ONNX_RESOURCE_JAR 为 AI 服务 JAR 的绝对路径
# PgVector 集成测试，请使用空的测试数据库并提前启动 PgVector
RUN_PGVECTOR_IT=true KNOWLEDGE_DB_URL=jdbc:postgresql://127.0.0.1:5432/agent_demo_knowledge_test \
  KNOWLEDGE_DB_USER=agent_demo KNOWLEDGE_DB_PASSWORD=agent_demo \
  mvn -pl agent-demo-ai/agent-demo-ai-service -am test \
  -Dtest=PgVectorKnowledgeIT -Dsurefire.failIfNoSpecifiedTests=false
```

默认切片目标为 320 token，上限 400，长段局部重叠 40。重排为问题预留 96 token，问题和片段含特殊标记合计最多 512 token。替换 Embedding 模型后需重新生成知识向量，单独替换 reranker 可沿用已存向量。关闭 `AI_KNOWLEDGE_ENABLED` 可停用导入与检索。需要回退到旧的聊天行为时，同时回退 AI 服务和前端并保留 PostgreSQL 卷。
