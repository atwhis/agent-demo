## Why

销售助手当前只能通过 Tool 查询实时统计，不能回答用户文本中的制度、说明和操作知识。基于先行变更提供的多轮会话和调用记录，补齐纯文本导入、向量召回、本地重排及单一路由，形成同一主页面内的知识问答闭环。

## What Changes

- 前置依赖：`add-conversation-memory-and-tool-traces` 完成实现和验收，并先同步其 delta specs；本项在该结果之上增量实现。
- 支持 UTF-8 TXT 和粘贴纯文本；按标题、条款、段落优先切分，超长内容按实际 token 预算兜底，保留少量重叠。
- 使用现有 resources/models/embedding 下的 BGE small 中文 ONNX 生成 512 维向量，PgVector 使用 HNSW 和余弦距离，召回最多 15 个片段。
- 使用现有 resources/models/reranker 下的 mMARCO mMiniLMv2 ARM64 INT8 ONNX 对问题／片段配对重排，保留最多 5 个片段，由所选 Spring AI ChatModel 生成带来源回答。
- 使用所选 LLM 结合最近会话输出 SALES／RAG／CLARIFY 和独立问题；每轮只执行一条查询链路，混合需求先追问。
- 导入文本按制度规则／产品说明／操作指南／其他分类，允许指定类型或自动识别；类型是知识标签，不是销售／RAG 路由的硬性依据。
- 知识库为空或资料不相关时友好提示；模型、数据库或重排故障返回受控错误，不伪装成没有资料。
- 保持一个助手主页面，仅在输入区增加导入文本入口，复用输入区填写标题、类型和正文或读取 TXT，完成后恢复聊天；不增加侧栏、管理面板或新页面。
- 知识和向量持久化到 PostgreSQL；会话及调用记录继续仅内存保留。新增 sources SSE 事件及本轮检索记录，不建设评测或追踪平台。

## Capabilities

### New Capabilities

- `knowledge-document-ingestion`: 纯文本导入、结构与 token 切片、用途分类、元数据、内容去重和完整入库。
- `knowledge-retrieval`: 本地 ONNX 适配、512 维 PgVector 召回、Top15→Top5 重排、来源约束及无资料回答。
- `chat-intent-routing`: 基于历史的结构化三类路由、独立问题补全、混合需求澄清及链路隔离。

### Modified Capabilities

- `multi-model-chat`: 请求所选模型同时用于意图识别与最终生成，销售 Tools 仅按 SALES 路由装配。
- `sales-tool-calling`: 只在 SALES 路由执行销售数据守卫，RAG 与澄清不得触发销售查询。
- `streaming-chat-ui`: 增加输入区复用导入、来源展示及 sources 事件，保留前置变更的会话与 start 约定。
- `service-discovery-config`: Compose 增加持久化 PgVector，Nacos 增加 AI 知识库和模型资源配置，保持秘密通过服务端注入。

## Impact

- 仅扩展 AI 服务及开发环境，复用现有销售 Client、销售统计服务、模型选择和安全 Markdown 渲染。
- 增加纯文本导入接口 POST /api/knowledge/documents/text 和 POST /api/knowledge/documents/upload；不增加文档管理页面。
- 在 AI 服务内增加本地模型适配、文档入库、检索重排、意图分类；将 sources 和检索证据附加到前置变更的轮次记录。
- 需要 PostgreSQL／PgVector、Java ONNX Runtime 和 tokenizer 依赖；使用已放好的模型，不另建 Python 推理服务，不在本变更重新选择模型。
- 实现任务首先验证 M1 原生 ARM64 上两个 ONNX 模型的实际运行，包括 Embedding 外部权重 model.onnx_data 的装载和 Reranker 成对输入。
- 本项不包含混合查询、OCR、PDF／Word 解析、Hybrid Search、多 Agent、长期记忆或模型计费。
