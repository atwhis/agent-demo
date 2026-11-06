## MODIFIED Requirements

### Requirement: Per-request model routing
每个聊天请求 SHALL 携带 modelId，系统 SHALL 使用该模型完成意图识别及所选链路的最终生成，读取该 conversationId 最近完整消息，且不得修改会影响其他并发请求的全局模型状态。SALES 路由 SHALL 使用该模型完成 Tool Calling，RAG 和 CLARIFY 不注册销售工具；同一会话切换模型 SHALL 保持对话条件，不把历史业务数字作为本轮查询结果。

#### Scenario: Switch from DeepSeek to OpenAI
- **WHEN** 用户先使用 DeepSeek 提问，再把模型切换为 OpenAI 后提交新问题
- **THEN** 两次请求分别由所选模型处理，并使用统一输出协议与相同的路由能力；在 SALES 下提供相同销售 Tool，后一次请求仍可理解同一会话的查询条件

#### Scenario: Choose a model for knowledge answer
- **WHEN** 用户选择 OpenAI 后提交知识问题
- **THEN** 意图识别和最终知识回答均使用本次所选模型，本地 Embedding 和重排资源保持不变
