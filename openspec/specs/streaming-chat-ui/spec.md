# streaming-chat-ui Specification

## Purpose

提供一个轻量、无前端框架的聊天页面，通过类型化 SSE 增量展示模型 Markdown 回答，并在回答完成后展示可信图表而不暴露内部 Tool 和思考过程。

## Requirements

### Requirement: POST streaming chat endpoint
系统 SHALL 提供接收 JSON 请求体的 POST 聊天接口，并以 `text/event-stream` 返回类型化事件。

#### Scenario: Start a streaming chat
- **WHEN** 前端提交包含消息、模型 ID 和可选会话 ID 的聊天请求
- **THEN** 服务端建立 SSE 响应并开始发送增量事件

### Requirement: Typed SSE lifecycle
通过参数和会话接纳校验的响应 SHALL 首先发送恰好一个 start，包含 conversationId 和 turnId。成功响应随后 SHALL 由零个或多个 delta、至多一个 chart 和恰好一个 done 组成；done SHALL 带同一 conversationId 和 turnId。失败响应 SHALL 发送 error 且不得再发送成功 done；接纳前错误可以不包含 start。取消请求不得产生成功 done。

#### Scenario: Complete text and chart response
- **WHEN** 一次销售分析回答成功产生文字和图表
- **THEN** 前端依次收到 start、文字增量、图表和完成事件，标识始终属于同一轮

#### Scenario: Stream ends without done
- **WHEN** 网络流关闭但前端尚未收到 done
- **THEN** 前端把回答标记为未完整完成而不是成功

#### Scenario: First turn fails after start
- **WHEN** 首次提问已收到 start，但模型随后失败
- **THEN** 前端已知本次会话标识且展示失败，不把部分文字保存为成功回答

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

### Requirement: New conversation within the existing page
前端 SHALL 在现有助手主页面提供新会话操作，停止当前请求、请求清理旧会话并重置本地聊天与会话标识；清理失败 SHALL 友好提示，不能宣称服务端清理成功。系统 MUST 使用请求归属标识隔离旧流，旧流的任何迟到事件不得写入新会话界面。

#### Scenario: Start a new conversation during streaming
- **WHEN** 用户在回答尚未完成时点击新会话
- **THEN** 页面回到空会话状态并停止旧流，旧回答或完成事件不会再次出现在新会话

#### Scenario: Clear fails remotely
- **WHEN** 新会话操作中的服务端清理请求失败
- **THEN** 前端提示未能清理旧会话，同时保持新旧请求隔离，不恢复旧流
