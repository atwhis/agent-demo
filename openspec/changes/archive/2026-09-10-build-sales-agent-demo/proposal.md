## Why

需要一个范围可控、业务语义直观的 Spring AI Agent 示例，用于学习大模型切换、Tool Calling、Feign 业务调用、SSE 流式输出和结构化图表渲染的完整链路。销售分析比图书借还减少了实体副本和归还状态等额外概念，同时仍能提供足够丰富的聚合查询来验证 Agent 与业务系统的交互。

## What Changes

- 创建名为 `agent-demo` 的 Maven 多模块项目，包含销售业务域和 AI 服务域。
- 按业务域组织 Maven 聚合模块：销售域包含可复用的 `sales-client` 契约模块和独立运行的 `sales-service`，AI 域包含独立运行的 `ai-service`。
- 销售域提供客户、商品分类、商品、库存、订单和订单明细的数据模型及内部 API；`sales-client` 的 Feign 接口、Spring MVC 映射和 DTO 可由 AI 服务及未来其他业务服务共同依赖。
- 初始化最近三个月、具有明显统计分布特征的演示销售数据，不提供销售管理页面。
- 提供客户购买排行、分类销售占比、热门商品排行和销售趋势四类统计能力。
- 在 AI 服务中接入 DeepSeek V4 Flash 和 OpenAI GPT-5.6 Terra，前端可在两者之间切换。
- 将销售服务 Feign Client 封装成 Spring AI Tool，由模型选择并调用受控的业务统计能力。
- 使用 POST SSE 输出 `delta`、`chart`、`done`、`error` 事件；前端实时累积并渲染 Markdown。
- 使用 marked.js、DOMPurify、highlight.js 和 Apache ECharts 展示最终文字和固定类型图表，不展示模型思考或 Tool 调用过程。
- 使用 Nacos 3.x 作为注册中心和配置中心，采用 `spring.config.import` 接入配置；应用本地配置只保留连接 Nacos 所需的启动信息和框架级不变量，普通环境配置放入 Nacos，数据库密码和模型 API Key 仍由环境变量注入。
- 提供根目录 `docker-compose.yml` 和 `.env.example`，一键启动本地 MySQL 8.4 LTS 与单机 Nacos 3.2.3；Nacos 不使用内置 Derby，而与销售服务共用同一个 MySQL 实例和账号密码，并分别使用 `nacos_config` 与 `agent_demo_sales` 两个数据库。
- 销售物理表统一使用 `t_` 前缀，数据库只保留主键、非空约束、唯一索引和普通索引，不建立外键或 `CHECK` 约束；跨表关系和金额、库存一致性由业务代码校验。
- 在销售服务中提供可关闭的 Spring JDBC SQL 模板及绑定参数日志，不引入 P6Spy，也不记录查询耗时。
- 在销售与 AI 服务中合理使用 Lombok 减少构造器和日志样板代码，并为 Chat、双模型路由、Tool Calling、结果捕获及 SSE 收口等学习主流程增加解释设计原因的代码注释。
- 一期不包含登录权限、销售管理前端、Feign 重试/熔断/降级、pgvector、RAG、长期记忆、计费和多 Agent。

## Capabilities

### New Capabilities

- `sales-data-service`: 销售领域数据模型、基础内部 API、库存和订单数据约束，以及三个月演示数据初始化。
- `sales-analytics`: 四类销售统计查询及其统一统计口径和结构化响应。
- `multi-model-chat`: DeepSeek 与 OpenAI 两个 ChatModel 的配置、发现、选择和按请求路由。
- `sales-tool-calling`: 将销售统计 Feign API 封装为 Spring AI Tool，并完成模型请求、Tool 执行和最终回答循环。
- `streaming-chat-ui`: 基于 POST SSE 的聊天接口和原生前端增量 Markdown 渲染。
- `chart-visualization`: 从可信 Tool 结果生成受控 ChartSpec，并通过 ECharts 展示柱状图、饼图和折线图。
- `service-discovery-config`: 两个运行服务通过 Nacos 3.x 完成注册发现和外部配置加载。

### Modified Capabilities

无。

## Impact

- 新增父 Maven 工程、平级的 `agent-demo-sales` 与 `agent-demo-ai` 聚合模块，以及其下的 `agent-demo-sales-client`、`agent-demo-sales-service`、`agent-demo-ai-service` 子模块；一期仍只有销售服务和 AI 服务两个运行进程。
- 新增带 `t_` 前缀且不含外键和 `CHECK` 约束的 MySQL 表结构、索引、演示数据生成器及销售统计 SQL。
- 新增 Spring Cloud OpenFeign、Spring AI 2.0、DeepSeek、OpenAI、WebFlux、Nacos 和 MyBatis-Plus Boot 4 依赖。
- 新增销售内部 REST API、聊天 SSE API、模型列表 API和前后端 ChartSpec 协议。
- 新增仅供本地开发使用的 Docker Compose 依赖环境；同一 MySQL 容器承载相互隔离的销售库和 Nacos 库，不在 Compose 中运行销售服务、AI 服务或模型代理。
- 需要通过环境变量提供 `DEEPSEEK_API_KEY` 与 `OPENAI_API_KEY`，密钥不得存入 Nacos 或前端。
