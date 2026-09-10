## Purpose

提供一个轻量、无前端框架的聊天页面，通过类型化 SSE 增量展示模型 Markdown 回答，并在回答完成后展示可信图表而不暴露内部 Tool 和思考过程。

## ADDED Requirements

### Requirement: POST streaming chat endpoint
系统 SHALL 提供接收 JSON 请求体的 POST 聊天接口，并以 `text/event-stream` 返回类型化事件。

#### Scenario: Start a streaming chat
- **WHEN** 前端提交包含消息、模型 ID 和可选会话 ID 的聊天请求
- **THEN** 服务端建立 SSE 响应并开始发送增量事件

### Requirement: Typed SSE lifecycle
成功响应 SHALL 由零个或多个 `delta`、至多一个 `chart` 和恰好一个 `done` 组成；失败响应 SHALL 发送 `error` 且不得再发送成功 `done`。

#### Scenario: Complete text and chart response
- **WHEN** 一次销售分析回答成功产生文字和图表
- **THEN** 前端依次收到文字增量、图表和完成事件

#### Scenario: Stream ends without done
- **WHEN** 网络流关闭但前端尚未收到 `done`
- **THEN** 前端把回答标记为未完整完成而不是成功

### Requirement: Incremental Markdown rendering
前端 SHALL 累积所有 `delta.text` 后使用完整累积文本重新渲染 Markdown，并支持表格、加粗、列表和代码块。

#### Scenario: Markdown table arrives in chunks
- **WHEN** Markdown 表格分多个增量到达
- **THEN** 前端持续保留累积文本，并在数据逐步完整后渲染为表格

### Requirement: Safe Markdown output
前端 MUST 在写入 DOM 前清理 Markdown 生成的 HTML，并阻止脚本、事件属性和危险链接协议执行。

#### Scenario: Model emits unsafe HTML
- **WHEN** 模型回答包含脚本标签或事件处理属性
- **THEN** 页面显示安全内容且不执行嵌入脚本

### Requirement: Final rendering and highlighting
收到 `done` 后，前端 SHALL 使用完整 Markdown 最终渲染一次，并对代码块执行一次语法高亮。

#### Scenario: Complete fenced code block
- **WHEN** 一个代码围栏在最后几个增量中闭合
- **THEN** 完成事件后页面显示完整代码块并应用语法高亮

### Requirement: Hide internal execution details
前端 SHALL 只展示用户消息、最终助手文字和图表，不展示模型思考内容、Tool 名称、Tool 参数、Feign 调用或中间 Tool 结果。

#### Scenario: Model invokes a sales tool
- **WHEN** 后端完成一次 Tool Calling 循环
- **THEN** 页面只出现最终回答和可选图表
