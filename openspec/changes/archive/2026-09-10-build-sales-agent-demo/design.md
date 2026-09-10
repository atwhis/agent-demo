## Context

这是一个全新的学习型项目，目标是观察 Spring AI 2.0 与两个真实大模型之间的 Tool Calling 和流式交互。项目没有历史数据迁移、登录权限、销售管理页面和长期会话存储要求；参考实现来自现有 `spring-ai-rag-demo-main` 的类型化 SSE、实时 Markdown 和受控 ChartSpec 设计，但不复制其 ERP、RAG、计费、历史记录和通用图表规划复杂度。

技术基线为 JDK 21、Spring Boot 4.0.x、Spring AI 2.0.x、Spring Cloud 2025.1.x、Spring Cloud Alibaba 2025.1.x、Nacos Server 3.2.x、MySQL、Spring JDBC、MyBatis-Plus Boot 4、OpenFeign、原生 HTML/CSS/JS、marked.js、DOMPurify、highlight.js 和 Apache ECharts。

OpenAI 官方模型目录将 GPT-5.6 Terra 定位为智能与成本平衡型号，并确认其支持 Chat Completions、Streaming 和 Function Calling，因此本设计选择 `gpt-5.6-terra` 与 `deepseek-v4-flash` 组成一期双模型。

## Goals / Non-Goals

**Goals:**

- 用两个独立运行服务演示自然语言、模型选择、Tool Calling、Feign、SQL 聚合、最终回答和图表的完整链路。
- 让 DeepSeek 与 OpenAI 共用相同的系统提示、Tool 集合、销售统计接口、SSE 和 ChartSpec 协议。
- 保证业务数字由销售服务计算，模型只负责识别意图、选择 Tool 和解释结果。
- 保持模块和协议具备增加新模型、新 Tool 和长期记忆的扩展点，但不提前实现这些能力。

**Non-Goals:**

- 不实现登录、权限、租户、支付、退款、促销、采购、仓库、物流或完整 ERP 规则。
- 不实现销售管理页面、历史会话、pgvector、RAG、长期记忆、MCP、多 Agent 或模型计费。
- 不实现 Feign fallback、重试、熔断和业务降级，只保留把未处理异常转换为 SSE `error` 的最小收口。
- 不让模型生成或执行 SQL、URL、Feign 方法名、JavaScript 或 ECharts option。

## Decisions

### 1. 按业务域使用聚合模块，并单独发布 Client 契约

```text
agent-demo
├── pom.xml
├── agent-demo-sales
│   ├── pom.xml
│   ├── agent-demo-sales-client
│   └── agent-demo-sales-service
└── agent-demo-ai
    ├── pom.xml
    └── agent-demo-ai-service
```

根 POM 负责统一版本和聚合业务域；`agent-demo-sales`、`agent-demo-ai` 是 `pom` packaging 的领域聚合模块，不是运行进程。`agent-demo-sales-service` 和 `agent-demo-ai-service` 是一期两个可独立启动、注册到 Nacos 的应用。

`agent-demo-sales-client` 是轻量、可复用、可独立发布的调用契约模块，包含：

```text
agent-demo-sales-client
├── client
│   └── SalesStatisticsClient
├── dto
│   ├── request
│   └── response
└── model
    └── public enums
```

Client 模块只包含 Feign/Spring MVC 接口契约、请求与响应 DTO、必要的公共枚举和路径常量，不包含 Entity、Mapper、数据库配置、业务 Service 或 Controller 实现。`agent-demo-ai-service` 依赖 `agent-demo-sales-client`，不会依赖 `agent-demo-sales-service`，因此不会把销售实现细节带入 AI 服务。

一期按“Feign 接口与 Controller 使用同一份接口契约”实现，不额外拆分 API interface 与 Feign interface：`SalesStatisticsClient` 同时声明 `@FeignClient`、版本化 Spring MVC 映射、校验参数和返回类型；销售服务中的 `SalesStatisticsController` 直接实现该接口，只负责委托领域统计服务。销售服务依赖自己的 Client 模块以获得契约，但不扫描或实例化自身 Feign Client；AI 服务通过限定包范围的 `@EnableFeignClients` 创建调用代理。

这种设计减少 Demo 中重复的接口声明和 DTO 映射。代价是公共契约与 Spring Cloud OpenFeign 耦合，且升级 Spring MVC/OpenFeign 时需要验证接口注解继承行为；若未来需要非 Feign 调用方、OpenAPI 生成客户端或独立演进服务端契约，再拆为中立 `api` 接口和 Feign 适配接口。

如果后续增加财务系统，结构扩展为：

```text
agent-demo
├── pom.xml
├── agent-demo-sales
│   ├── pom.xml
│   ├── agent-demo-sales-client
│   └── agent-demo-sales-service
├── agent-demo-finance
│   ├── pom.xml
│   ├── agent-demo-finance-client
│   └── agent-demo-finance-service
└── agent-demo-ai
    ├── pom.xml
    └── agent-demo-ai-service
```

财务域在一期只用于说明扩展规则，不创建空模块、不实现财务能力。以后销售服务可以只依赖 `agent-demo-finance-client`，财务服务也可以只依赖 `agent-demo-sales-client`；Client 模块不得反向依赖任何 Service 模块，因此 Maven 依赖图保持无环。对于销售 → 财务 → 销售这类运行时 RPC 环路仍应避免，跨域组合查询优先由 AI 服务或独立编排层完成。

### 2. 使用订单头和订单明细，而不是单张宽订单表

核心表如下：

| 表 | 关键字段 | 说明 |
|---|---|---|
| `t_customer` | `id`, `customer_no`, `customer_name`, `department`, `status` | 不区分客户类型 |
| `t_product_category` | `id`, `category_code`, `category_name`, `parent_id`, `status` | 一期只生成一级分类 |
| `t_product` | `id`, `product_no`, `product_name`, `category_id`, `unit`, `sale_price`, `status` | 商品主数据 |
| `t_product_inventory` | `id`, `product_id`, `stock_quantity`, `locked_quantity`, `version` | 每个商品唯一库存 |
| `t_sales_order` | `id`, `order_no`, `customer_id`, `order_time`, `total_quantity`, `total_amount`, `status` | 订单整体信息 |
| `t_sales_order_item` | `id`, `order_id`, `product_id`, `product_name`, `category_id`, `unit_price`, `quantity`, `line_amount` | 历史交易快照 |

所有表使用 `BIGINT` 主键和 `DATETIME(3)` 时间，业务时间统一解释为 `Asia/Shanghai`。订单明细保留名称、分类和价格快照，防止商品主数据变化破坏历史统计。

物理表不建立外键或 `CHECK` 约束。主键、`NOT NULL`、默认值、唯一索引和普通索引保留；`customer_id`、`category_id`、`product_id`、`order_id` 等字段只表达逻辑关联，由业务服务在写入前校验引用存在性、金额和库存不变量。建议索引：

```text
t_customer(customer_no) UNIQUE
t_product_category(category_code) UNIQUE
t_product(product_no) UNIQUE
t_product(category_id)
t_product_inventory(product_id) UNIQUE
t_sales_order(order_no) UNIQUE
t_sales_order(order_time, status)
t_sales_order(customer_id, order_time)
t_sales_order_item(order_id)
t_sales_order_item(product_id)
t_sales_order_item(category_id)
```

### 3. MyBatis-Plus 处理基础数据，Spring JDBC 处理统计 SQL

客户、分类、商品、库存和订单的常规持久化使用 MyBatis-Plus。四种多表聚合使用 `NamedParameterJdbcTemplate`，使 SQL 分组、时间桶和排序逻辑可直接阅读和测试，避免为了 Demo 构造复杂 Wrapper。

统一有效订单条件为：

```text
t_sales_order.status IN ('PAID', 'COMPLETED')
```

统计口径：

| 分析 | 分组 | 默认排序/指标 |
|---|---|---|
| 客户购买排行 | `customer_id` | `SUM(item.quantity)` 降序 |
| 分类销售占比 | 明细快照 `category_id` | 分类销售额 / 总销售额 |
| 热门商品排行 | `product_id` | `SUM(item.quantity)` 降序 |
| 销售趋势 | 日、周、月时间桶 | 订单数、销售数量、销售金额 |

趋势查询先生成完整时间桶，再左连接统计结果，确保无交易日期返回零值。周从星期一开始，日期区间统一使用左闭右开 `[startDate, endDateExclusive)`。

本地或开发环境可以通过 Nacos 中的日志级别开启 Spring JDBC 的 SQL 模板与绑定参数日志：`JdbcTemplate=DEBUG`、`StatementCreatorUtils=TRACE`。关闭时将二者设为 `OFF`。一期不记录查询耗时、不引入 P6Spy，且生产环境默认关闭参数日志，避免输出敏感业务字段。

### 4. 每个模型拥有独立 ChatModel 和 ChatClient

配置注册表以稳定 `modelId` 为键：

```text
deepseek-v4-flash → DeepSeek ChatModel → DeepSeek ChatClient
gpt-5.6-terra    → OpenAI ChatModel   → OpenAI ChatClient
```

请求通过 `modelId` 读取不可变客户端，不在运行时修改全局默认模型或单例 ChatOptions，从而避免并发用户互相串模型。两个客户端复用相同的系统提示模板和 ToolCallback 列表，但可以保留 Provider 专属选项构建器。

一期默认模型为 `deepseek-v4-flash`。前端加载 `/api/models` 后显示两个选项；切换只影响随后提交的请求。

API Key 通过 `DEEPSEEK_API_KEY` 和 `OPENAI_API_KEY` 环境变量注入。Nacos 只保存模型 ID、展示名、模型名、基础地址、启用状态和默认状态。

### 5. 使用四个粗粒度统计 Tool

| Tool | 参数 | `sales-client` 契约中的 Feign 统计 API |
|---|---|---|
| `queryCustomerPurchaseRanking` | `startDate`, `endDate`, `limit` | `/internal/v1/statistics/customer-purchase-ranking` |
| `queryCategorySalesShare` | `startDate`, `endDate` | `/internal/v1/statistics/category-sales-share` |
| `queryPopularProducts` | `startDate`, `endDate`, `limit` | `/internal/v1/statistics/popular-products` |
| `querySalesTrend` | `startDate`, `endDate`, `granularity` | `/internal/v1/statistics/sales-trend` |

Tool 描述同时说明适用与不适用场景、统计口径、日期格式和参数限制。Tool 不直接暴露任意 URL、SQL 或通用 `invokeApi` 能力。

系统提示词每次提供当前日期、`Asia/Shanghai` 时区、相对日期解释和“业务数据必须调用 Tool”的约束。Spring AI `ToolCallingAdvisor` 执行模型 → Tool → Tool 结果 → 模型的循环。

### 6. 使用请求级 Tool 结果记录器生成图表

图表不由模型规划。Tool 成功后，AI 服务按当前请求 trace ID 保存结构化统计结果；最终模型文字完成时，再从记录器读取对应结果并构建固定 ChartSpec：

```text
customer ranking → bar
category share   → pie
popular products → bar
sales trend      → line
```

ChartSpec 只包含协议版本、图表 ID、白名单类型、标题、维度、数据行、语义编码、有限展示选项和来源 Tool。它不包含函数、HTML、URL 或完整 ECharts option。

这种方式保留了参考项目 `ToolResultRecorder + ChartSpec + ChartAdapter` 的安全边界，但删除通用 `plan_chart_visualization`、模型选图和多图表编译器。

### 7. 使用 POST SSE 和四类前端事件

接口：

```http
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

请求：

```json
{
  "conversationId": "optional-id",
  "modelId": "deepseek-v4-flash",
  "message": "查询最近一个月销售数量最多的商品"
}
```

事件：

```text
delta* → chart? → done
error  → end
```

`delta` 表示 Provider 返回的文本增量 chunk，不假定一个事件等于一个 token。服务端不把 Tool 开始、Tool 结束、Feign 参数或 reasoning content 发送给浏览器。

`chart` 在服务端最终收口时产生，前端先暂存；收到 `done` 才显示图表。流关闭但没有 `done` 时按失败处理。

### 8. 前端累积 Markdown 后节流重渲染

前端通过 `fetch` POST 并使用 `ReadableStream.getReader()` 解析 SSE，而不是使用只能方便发起 GET 的原生 `EventSource`。

每个 `delta` 只追加到 `fullText`，使用 `requestAnimationFrame` 或约 30 至 50ms 节流执行：

```text
marked.parse(fullText)
→ DOMPurify.sanitize(html)
→ bubble.innerHTML
```

`done` 时完整渲染一次并运行 highlight.js。Markdown 表格或代码围栏在流中尚未闭合时允许暂时显示为普通文本，最终完成状态必须正确。

ECharts 适配器只接受协议版本 `1.0` 及 `bar`、`pie`、`line` 白名单，并把声明式 ChartSpec 转为本地 option。

### 9. 初始化具有分析特征的最近三个月数据

空库初始化目标规模：

| 数据 | 数量 |
|---|---:|
| 客户 | 50 |
| 分类 | 8 至 10 |
| 商品 | 80 至 100 |
| 库存 | 每个商品一条 |
| 订单 | 800 至 1,200 |
| 订单明细 | 1,500 至 3,000 |

数据生成器使用固定随机种子和相对当前日期的 90 天窗口，制造高频客户、热门商品、主要分类、工作日与周末差异及近期高峰。状态比例约为 80% `COMPLETED`、10% `PAID`、5% `CREATED`、5% `CANCELLED`。

初始化继续由销售服务中的 Java 数据生成器完成，而不是维护体量较大的静态 `data.sql`。销售服务启动后先通过 `schema.sql` 创建表，再在显式开关启用且 `t_customer` 为空时使用固定随机种子和相对当前日期生成数据；重复启动不会追加数据。需要刷新最近 90 天数据时，由开发者显式清空本地 MySQL volume 后重新启动依赖与销售服务，不提供生产数据迁移能力。

### 10. Nacos 负责注册发现和非敏感配置

两个运行服务使用 Nacos Server 3.2.x。Spring Cloud Alibaba 2025.1.x 通过 `spring.config.import` 从 `application.yml` 导入配置，不使用 `bootstrap.yml`。

建议命名：

```text
service: agent-demo-sales-service
service: agent-demo-ai-service
group: AGENT_DEMO
dataId: agent-demo-sales-service-{profile}.yaml
dataId: agent-demo-ai-service-{profile}.yaml
```

AI 服务的 Feign Client 使用 `agent-demo-sales-service` 服务名。配置按以下层次管理：

```text
application.yml / 进程启动环境
└── application name、profile、Nacos 地址/namespace/group/凭据、spring.config.import、框架级自动配置开关

Nacos 普通配置
└── 端口、业务数据库 URL/用户名、模型 ID/名称/base URL、演示数据开关、SQL 参数日志级别等非敏感环境配置

服务进程环境变量
└── 共用 MySQL 密码、DeepSeek API Key、OpenAI API Key 等秘密值
```

Nacos 配置可以引用 `${MYSQL_PASSWORD}`、`${DEEPSEEK_API_KEY}` 和 `${OPENAI_API_KEY}` 占位符，但不得保存实际秘密值。Nacos 自身连接 MySQL 所需的地址、数据库名、账号和密码属于启动前置配置，只能由 Compose 环境变量或 Nacos 本地配置提供，不能存放在尚未启动的 Nacos 中。模型、数据库和其他启动期 Bean 的配置发生变化后，一期通过重启对应服务生效，不承诺所有 Nacos 变更自动热刷新。

### 11. 验证分为确定性业务测试和双模型冒烟测试

- 销售服务使用固定测试数据验证四类统计口径、无数据时间桶、状态排除和参数校验。
- AI 服务使用模拟 ChatModel 验证模型路由、Tool 注册、Tool 结果捕获、ChartSpec 和 SSE 顺序。
- 前端测试 SSE 跨网络分片解析、Markdown 节流、危险 HTML 清理、`done` 门控和 ChartSpec 白名单。
- DeepSeek 与 OpenAI 各运行一组真实 API 冒烟问题，验证四个 Tool 的选择、参数格式、流式输出和最终图表；真实 API 测试不作为默认离线构建的必要条件。

### 12. 使用 Docker Compose 启动本地依赖环境

项目根目录提供 `docker-compose.yml` 和 `.env.example`，只启动应用依赖，不把销售服务或 AI 服务容器化：

| 服务 | 镜像 | 用途 | 主机端口 |
|---|---|---|---|
| MySQL | `mysql:8.4.11` | 同时承载 `agent_demo_sales` 业务库和 `nacos_config` 配置库 | `3306` |
| Nacos | `nacos/nacos-server:v3.2.3-slim` | 使用外置 MySQL 的单机注册中心和配置中心 | Console `8080`、Server `8848`、gRPC `9848` |

MySQL 采用 8.4 LTS 并使用一个命名卷保存两个逻辑数据库。MySQL 首次初始化脚本创建 `agent_demo_sales` 和 `nacos_config`，两者使用 `.env` 提供的同一 MySQL 用户名和密码；初始化脚本向该用户授予两个数据库的权限。Nacos schema 必须固定为 Nacos Server 3.2.3 对应的官方 MySQL schema。销售表结构仍由销售应用的 `schema.sql` 初始化，不复制到 Compose 的数据库初始化目录。

本地 Nacos 不使用内置 Derby。Compose 通过 `SPRING_DATASOURCE_PLATFORM=mysql`、`MYSQL_SERVICE_HOST=mysql`、`MYSQL_SERVICE_DB_NAME=nacos_config` 及共用账号密码把 Nacos 指向同一个 MySQL 容器，并在 MySQL 健康后再启动 Nacos。数据库 host 必须使用 Compose service name `mysql`，不能使用容器内的 `127.0.0.1`。Compose 使用 `FUNCTION_MODE=microservice`，只启用本项目需要的服务发现和配置能力。为降低首次学习和启动成本，本地默认显式关闭 Nacos 客户端鉴权；该安全配置不得直接用于共享测试、预发布或生产环境。

两个容器都提供健康检查。环境变量可通过复制 `.env.example` 为 `.env` 覆盖，Compose 文件不得包含 DeepSeek 或 OpenAI API Key。正常停止保留 MySQL 命名卷；只有开发者明确执行带卷删除的命令时才同时清空销售数据和 Nacos 配置。MySQL 官方初始化目录只在空数据目录执行，因此从 Derby 切换外置 MySQL或调整初始化 schema 后，需要明确删除本地 volume 再重建。

### 13. 有边界地使用 Lombok 和学习型代码注释

`agent-demo-sales-service` 与 `agent-demo-ai-service` 均可将 Lombok 声明为可选编译依赖。Spring Bean 使用 `@RequiredArgsConstructor` 减少构造器注入样板，需要记录运行信息的类使用 `@Slf4j`；Entity 继续使用 `@Getter/@Setter`，不得使用会额外生成 `equals`、`hashCode` 和 `toString` 的 `@Data`。Client DTO 和内部不可变数据继续优先使用 Java `record`，不为此引入 Lombok。

注释服务于学习主流程，重点解释 `ChatController` 的 POST SSE 映射、`ChatService` 的 trace/事件收口/错误清理、`SpringAiChatGateway` 的 Prompt 和 Tool Context、双 ChatClient 隔离、`SalesTools` 的 Tool Schema/参数校验/Feign 委托/结果记录，以及 `BusinessDataTurnGuard` 的无数据保护。简单字段、getter 和显而易见的语句不逐行注释。

## Risks / Trade-offs

- [Spring Boot 4、Spring AI 2、Spring Cloud Alibaba 和 MyBatis-Plus Boot 4 仍在快速演进] → 通过父 POM BOM 锁定经过构建验证的具体版本，不在子模块随意覆盖。
- [DeepSeek 与 OpenAI 对相同 Tool 描述的选择行为可能不同] → 使用同一套明确描述和固定测试问题，并记录真实冒烟结果进行对比。
- [Tool Calling 期间 Feign 是阻塞调用，而模型流使用 Reactor] → 将阻塞 Tool 执行隔离到适合阻塞工作的调度器，避免占用响应式事件线程。
- [流式 Markdown 在表格或代码围栏闭合前会发生布局变化] → 节流重绘并在 `done` 后完整渲染一次。
- [一期不提供 Feign 重试或降级] → 接受内部服务短暂异常会终止本次回答，只保证发送安全 `error` 并正确关闭流。
- [一期没有会话持久化] → `conversationId` 只作为请求链路关联字段，服务重启后不承诺恢复历史上下文。
- [模型可能跳过必需 Tool 或生成不合法参数] → 在应用侧校验 Tool 结果和参数；没有成功业务结果时不返回带业务数字的成功回答。
- [Nacos 与销售数据共用一个 MySQL 实例和账号密码会扩大账号权限与故障影响范围] → 通过两个独立数据库保持表和初始化生命周期隔离；该共享凭据方案仅用于本地 Demo，生产环境应拆分账号和权限。
- [移除外键和 CHECK 后数据库允许写入脏数据] → 将引用存在性、金额和库存不变量迁移到业务服务校验，并用领域与集成测试覆盖所有写入入口。

## Migration Plan

这是一个全新项目，没有生产存量迁移。当前本地环境从 Derby 和旧销售表名切换时，先停止依赖并显式删除本地 Docker volume，再由 MySQL 初始化脚本创建两个数据库和 Nacos 3.2.3 schema，由销售服务创建新的 `t_` 表并动态生成最近 90 天数据。回滚方式为停止两个服务并移除本地 Demo volume，不影响其他系统。
