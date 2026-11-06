## MODIFIED Requirements

### Requirement: Per-request model routing
每个聊天请求 SHALL 携带 modelId，系统 SHALL 使用该模型完成本次模型调用和 Tool Calling 循环，读取该 conversationId 的最近完整消息，且不得修改会影响其他并发请求的全局模型状态。同一会话切换模型 SHALL 保持对话条件，不把历史业务数字作为本轮查询结果。

#### Scenario: Switch from DeepSeek to OpenAI
- **WHEN** 用户先使用 DeepSeek 提问，再把模型切换为 OpenAI 后提交新问题
- **THEN** 两次请求分别由所选模型处理，并使用同一组销售 Tool 和输出协议，后一次请求仍可理解同一会话此前的查询条件
