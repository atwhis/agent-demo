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
通过参数和会话接纳校验的响应 SHALL 首先发送恰好一个 start，包含 conversationId 和 turnId。成功响应随后 SHALL 由零个或多个 delta、至多一个 chart、至多一个 sources 和恰好一个 done 组成；done SHALL 带同一 conversationId、turnId 和 route。SALES 仅可附加 chart，RAG 仅可附加 sources，CLARIFY 两者均不附加；附加事件在文字完成后、done 前发送。失败响应 SHALL 发送 error 且不得再发送成功 done；接纳前错误可以不包含 start。取消请求不得产生成功 done。

#### Scenario: Complete text and chart response
- **WHEN** 一次 SALES 回答成功产生文字和图表
- **THEN** 前端依次收到 start、文字增量、图表和完成事件，标识始终属于同一轮且不包含 sources

#### Scenario: Stream ends without done
- **WHEN** 网络流关闭但前端尚未收到 done
- **THEN** 前端把回答标记为未完整完成而不是成功

#### Scenario: First turn fails after start
- **WHEN** 首次提问已收到 start，但模型随后失败
- **THEN** 前端已知本次会话标识且展示失败，不把部分文字保存为成功回答

#### Scenario: Complete knowledge response with sources
- **WHEN** RAG 成功回答且有有效引用
- **THEN** 事件顺序为 start、文字增量、sources、done，来源只在收到成功 done 后展示

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
前端 SHALL 只展示用户消息、最终助手文字、可选图表、可核验知识来源和必要的导入结果，不展示模型思考内容、Tool 名称、Tool 参数、Feign 调用、分类过程或中间 Tool 结果。

#### Scenario: Model invokes a sales tool
- **WHEN** 后端完成一次 Tool Calling 循环
- **THEN** 页面只出现最终回答和可选图表，不展示内部工具信息

#### Scenario: Show a knowledge source
- **WHEN** RAG 回答成功带有引用
- **THEN** 页面显示文档标题、章节和引用片段，不展示向量分、重排分或内部查询实现

### Requirement: New conversation within the existing page
前端 SHALL 在现有助手主页面提供新会话操作，停止当前请求、请求清理旧会话并重置本地聊天与会话标识；清理失败 SHALL 友好提示，不能宣称服务端清理成功。系统 MUST 使用请求归属标识隔离旧流，旧流的任何迟到事件不得写入新会话界面。

#### Scenario: Start a new conversation during streaming
- **WHEN** 用户在回答尚未完成时点击新会话
- **THEN** 页面回到空会话状态并停止旧流，旧回答或完成事件不会再次出现在新会话

#### Scenario: Clear fails remotely
- **WHEN** 新会话操作中的服务端清理请求失败
- **THEN** 前端提示未能清理旧会话，同时保持新旧请求隔离，不恢复旧流

### Requirement: Import text using the existing composer
助手 SHALL 保持一个主页面，在输入区左下方增加导入文本入口；点击后 SHALL 复用同一输入区填写文档标题、类型和正文，或选择 TXT 文件。界面 MUST NOT 为导入新增页面、侧栏、弹窗或独立面板，且不得让用户手动切换销售／RAG 问答模式。

#### Scenario: Open import mode with a question draft
- **WHEN** 用户已输入问题但尚未发送，随后点击导入文本
- **THEN** 同一输入区切换为导入状态并保留原问题草稿，不跳转页面

#### Scenario: Select a TXT file without opening its body in the composer
- **WHEN** 用户选择一个合规的 TXT 文件
- **THEN** 页面显示文件名与大小并隐藏正文框，确认导入时通过 multipart 提交文件。移除文件后恢复正文框和原有粘贴草稿

### Requirement: Import completion and cancellation restore chat
导入成功 SHALL 显示实际标题、类型、片段数量和重复状态，并恢复聊天及原问题草稿；提交前取消导入 SHALL 不提交正文并恢复草稿，导入失败 SHALL 保留已填内容便于修改或重试。提交后停止等待不得被宣称为已撤销服务端入库，重试 SHALL 遵循内容去重结果。导入正文不得当作用户聊天问题保存；新会话不得删除已导入知识。

#### Scenario: Finish a successful import
- **WHEN** 用户在输入区完成文本导入
- **THEN** 当前页面显示导入结果并恢复提问，正文不出现在聊天消息中

#### Scenario: Cancel or fail import
- **WHEN** 用户在提交前取消导入或服务端返回导入失败
- **THEN** 取消时恢复原问题且不写知识库，失败时保留导入内容并显示可理解的错误

### Requirement: Safe source rendering and completion gating
前端 SHALL 将来源作为本轮回答的附加资料，安全显示标题、章节和片段；未经成功 done 的 sources SHALL 不作为成功结果展示。来源文本 MUST 使用安全文本节点或等效清理，不执行文档中包含的 HTML／脚本，旧流来源不得进入新会话。

#### Scenario: Malicious source text
- **WHEN** 引用标题或正文包含 HTML 事件或脚本
- **THEN** 内容仅作为文本资料展示，不能执行脚本

#### Scenario: Sources arrive before an error
- **WHEN** 收到 sources 后流以 error 结束而未收到 done
- **THEN** 不展示为成功引用，旧轮来源也不会残留到新会话
