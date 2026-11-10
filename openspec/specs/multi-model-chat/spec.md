# multi-model-chat Specification

## Purpose

允许同一个聊天前端在 DeepSeek 与 OpenAI 两个大模型之间显式切换，同时保持统一的聊天请求、Tool 能力和 SSE 响应协议，便于对比模型行为。

## Requirements

### Requirement: Two selectable chat models
系统 SHALL 对前端公开两个启用模型：`deepseek-v4-flash` 和 `gpt-5.6-terra`，并为每个模型返回稳定 ID、展示名称和默认标记。

#### Scenario: Load model options
- **WHEN** 前端请求可用模型列表
- **THEN** 系统返回 DeepSeek V4 Flash 和 OpenAI GPT-5.6 Terra 两个可选择项

### Requirement: Per-request model routing
每个聊天请求 SHALL 携带 modelId，系统 SHALL 使用该模型完成意图识别及所选链路的最终生成，读取该 conversationId 最近完整消息，且不得修改会影响其他并发请求的全局模型状态。SALES 路由 SHALL 使用该模型完成 Tool Calling，RAG 和 CLARIFY 不注册销售工具；同一会话切换模型 SHALL 保持对话条件，不把历史业务数字作为本轮查询结果。

#### Scenario: Switch from DeepSeek to OpenAI
- **WHEN** 用户先使用 DeepSeek 提问，再把模型切换为 OpenAI 后提交新问题
- **THEN** 两次请求分别由所选模型处理，并使用统一输出协议与相同的路由能力；在 SALES 下提供相同销售 Tool，后一次请求仍可理解同一会话的查询条件

#### Scenario: Choose a model for knowledge answer
- **WHEN** 用户选择 OpenAI 后提交知识问题
- **THEN** 意图识别和最终知识回答均使用本次所选模型，本地 Embedding 和重排资源保持不变

### Requirement: Unknown model rejection
系统 MUST 在调用模型前拒绝未知、禁用或空的非默认 `modelId`。

#### Scenario: Submit unknown model id
- **WHEN** 聊天请求携带未注册的模型 ID
- **THEN** 系统返回可展示的模型选择错误且不调用任何模型

### Requirement: Server-side model credentials
模型 API Key MUST 仅从服务端安全配置获取，不得通过模型列表接口、SSE 事件、前端资源或日志返回。

#### Scenario: Inspect model list response
- **WHEN** 前端获取模型列表
- **THEN** 响应只包含模型元数据，不包含 API Key 或其他凭据
