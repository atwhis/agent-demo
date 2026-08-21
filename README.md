# agent-demo

用于学习 Spring AI 2.0 Tool Calling 的双服务 Demo。大模型负责理解自然语言、选择 Tool 和解释结果；销售服务负责计算全部业务数字；AI 服务把 Tool 的结构化结果编译成安全的 ChartSpec，并通过 SSE 向原生前端发送最终文字和图表。

## 模块

```text
agent-demo
├── agent-demo-sales
│   ├── agent-demo-sales-client   # Feign/MVC 公共契约和 DTO
│   └── agent-demo-sales-service  # MySQL、业务规则、统计 API、演示数据
└── agent-demo-ai
    └── agent-demo-ai-service     # 双模型、Tools、SSE、静态前端
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
docker compose up -d
docker compose ps
docker compose logs -f mysql nacos
```

默认端口：MySQL `3306`、Nacos 控制台 `8080`、Nacos 服务端 `8848`、Nacos gRPC `9848`。MySQL 与 Nacos 都带健康检查；Nacos 会等待 MySQL healthy 后再启动。

同一个 MySQL 8.4 实例中会创建两个物理数据库，并由 `.env` 中同一个非 root 账号访问：

- `agent_demo_sales`：销售业务表与最近 90 天演示数据
- `nacos_config`：Nacos 配置、命名空间、用户等服务端数据

Nacos 不再使用内置 Derby，也不把数据库账号密码配置到 Nacos 自身。Compose 直接通过 Nacos 官方 Docker 环境变量传入连接信息。`docker/mysql/init/02-nacos-mysql-schema.sql.source` 来自 Nacos Server `3.2.3` 标签的官方 MySQL schema，项目不自行维护表结构变体；使用 `.source` 后缀是为了只由前一个初始化脚本导入 `nacos_config`，避免 MySQL 镜像把它再次自动导入默认销售库。

普通停止不会删除数据：

```bash
docker compose down
```

正常 `down` / `up` 会保留 MySQL 命名卷，因此 Nacos 配置与销售数据都保留。仅在明确需要同时重建两个数据库时删除命名卷（本地数据不可恢复）：

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

本地默认关闭 Nacos 认证，因此 `NACOS_AUTH_TOKEN` 留空；若启用认证，请仅在本地 `.env` 中设置符合 Nacos 长度要求的 Base64 Secret，不要提交到 Git。

销售服务的 local Data ID 默认打开学习用 SQL 日志：

- `org.springframework.jdbc.core.JdbcTemplate=DEBUG`：打印 SQL 模板
- `org.springframework.jdbc.core.StatementCreatorUtils=TRACE`：打印绑定参数

可在 Nacos 中把两项改为 `OFF`，或设置 `JDBC_SQL_LOG_LEVEL=OFF`、`JDBC_PARAMETER_LOG_LEVEL=OFF`。其他环境的 Data ID 应默认关闭。项目没有引入 P6Spy，也不统计 SQL 耗时。

## 编译与测试

```bash
mvn clean package
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
mvn -pl agent-demo-ai/agent-demo-ai-service spring-boot:run
```

若缺少任一 Key，AI 服务会在启动阶段失败，但不会打印 Key 值。打开 [http://localhost:8082](http://localhost:8082) 使用页面；模型列表为 DeepSeek V4 Flash 和 OpenAI GPT-5.6 Terra，切换只影响下一次请求。

在 IntelliJ IDEA 中，可打开对应 Spring Boot Run Configuration，在 `Environment variables` 中配置：

```text
MYSQL_USER=agent_demo;MYSQL_PASSWORD=你的本地MySQL密码
```

AI 服务再增加：

```text
DEEPSEEK_API_KEY=你的DeepSeekKey;OPENAI_API_KEY=你的OpenAIKey
```

macOS/Linux 终端使用冒号无关，IDEA 的变量编辑器既支持逐项填写，也支持上述分号分隔格式。不要把 Run Configuration 共享到 Git。

## 演示数据为什么由 Java 生成

项目保留 `DemoDataInitializer`，而不维护一份很大的历史 `data.sql`。初始化器仅在 `t_customer` 为空时运行，固定随机种子使分布可重复，而日期以启动当天为基准生成最近 90 天，因此“最近一个月/三个月”的示例不会随静态 SQL 逐渐过期。重复启动不会再次插入数据；要重新生成，需明确执行前述 `docker compose down -v`。

## 请求链路

```text
浏览器 POST SSE
  → 根据 modelId 选择不可变 ChatClient
  → 模型选择一个销售 Tool
  → Tool 校验日期/limit/粒度
  → Feign 调用 agent-demo-sales-service
  → JDBC 聚合返回结构化业务数据
  → Tool 结果回传给同一个模型生成最终 Markdown
  → 服务端依据 Tool 结果生成白名单 ChartSpec
  → delta* → chart? → done（失败时 error → 结束）
```

前端不会显示推理内容、Tool 名称、Tool 参数、Feign 信息或中间结果。Markdown 经过 DOMPurify 清理；图表仅接受 `bar`、`pie`、`line` 三类声明式协议，模型不能生成或执行 ECharts option、函数或 JavaScript。

## 示例问题

| 问题 | Tool | 图表 |
|---|---|---|
| 查询最近一个月购买商品数量最多的客户排行 | 客户购买排行 | 横向柱状图 |
| 查询最近三个月商品分类销售额占比 | 分类销售占比 | 饼图 |
| 最近一个月最受欢迎的具体商品排行 | 热门商品排行 | 横向柱状图 |
| 最近三个月按周统计销售额趋势 | 销售趋势（WEEK） | 折线图 |

统计只包含 `PAID` 和 `COMPLETED` 订单，日期区间包含开始日和结束日。业务问题若没有成功且非空的销售 Tool 结果，服务端不会发送成功 `done` 事件。

## 当前范围

一期不包含登录权限、销售管理页面、财务模块、RAG、pgvector、长期记忆、模型计费、Feign 重试/熔断和真实模型自动化冒烟测试。真实模型验证需要在本地提供 Key 后手工执行上述四个问题。
