## Why

当前接口虽然返回 conversationId，但模型只收到当前问题，无法连续修改查询条件；同轮再次调用销售 Tool 还会覆盖此前结果。先补齐有界的内存会话和独立调用记录，使现有销售助手能够独立完成多轮查询，并为后续知识库路由提供基础。

## What Changes

- 按 conversationId 保存最近 10 轮完整用户／助手消息，仅在内存中保留，服务重启清空；失败或取消的半截回答不进入有效记忆。
- 支持同一会话连续修改时间、排行数量、粒度和展示方式；切换模型仍使用该会话的消息窗口，业务数据每轮重新查询。
- 为每个请求分配 turnId，traceId 沿用该轮标识；每次业务或展示 Tool 调用分配独立 callId，保存参数、结果、状态和耗时。
- 提供内存调用记录查询和会话清理接口；记录按会话保留最近 10 次请求，不建设追踪页面。
- 前端增加新会话操作，停止旧请求并清理旧会话；SSE 增加 start 事件提前返回会话和轮次标识。
- 一轮仍最多展示一个图表，使用当前轮最后完成的成功且非空销售调用作为数据源，并关联 sourceCallId。
- 区分工具成功空结果与调用失败；空结果可正常说明无数据，缺少应执行的查询或发生调用失败时不得返回虚构业务数字的成功结果。
- 本项只完善现有销售助手，不引入 RAG、意图分类、PgVector、本地 ONNX、长期记忆、用户画像或摘要。

## Capabilities

### New Capabilities

- `conversation-memory`: 最近 10 轮内存消息、会话隔离、连续追问、成功提交和新会话清理。
- `tool-call-tracing`: 单轮多次调用的独立记录、有限保留、只读查询及取消后的清理。

### Modified Capabilities

- `multi-model-chat`: 每轮选定模型读取同一会话窗口，切换 provider 不丢失对话条件。
- `sales-tool-calling`: 连续追问重新查询，使用当前轮独立执行记录验证回答，正确处理成功空集。
- `streaming-chat-ui`: 增加 start 标识事件和新会话操作，完善失败、取消及旧流隔离。
- `chart-visualization`: 明确多次调用时的唯一图表来源，并将图表关联到具体 callId。

## Impact

- 主要修改 AI 服务的 ChatService、ChatGateway／SpringAiChatGateway、ToolResultRecorder、SalesTools／ChartTools、ChartSpec 和 ChartCompiler；销售服务及其统计 API 不变。
- 保留 POST /api/chat/stream；扩展 start／done 数据，增加 DELETE /api/conversations/{conversationId} 和 GET /api/conversations/{conversationId}/turns/{turnId}。
- 前端 index.html、app.js、styles.css 只增加会话操作和协议支持，不新增页面或面板；扩展现有离线测试。
- 复用 Spring AI 消息记忆能力，不新增数据库或模型服务依赖。
- 后续 `add-text-rag-and-intent-routing` 依赖本项完成实现、验收和规范同步；本项可单独交付、单独归档。
