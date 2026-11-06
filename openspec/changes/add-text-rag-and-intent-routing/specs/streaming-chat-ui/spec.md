## MODIFIED Requirements

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

### Requirement: Hide internal execution details
前端 SHALL 只展示用户消息、最终助手文字、可选图表、可核验知识来源和必要的导入结果，不展示模型思考内容、Tool 名称、Tool 参数、Feign 调用、分类过程或中间 Tool 结果。

#### Scenario: Model invokes a sales tool
- **WHEN** 后端完成一次 Tool Calling 循环
- **THEN** 页面只出现最终回答和可选图表，不展示内部工具信息

#### Scenario: Show a knowledge source
- **WHEN** RAG 回答成功带有引用
- **THEN** 页面显示文档标题、章节和引用片段，不展示向量分、重排分或内部查询实现

## ADDED Requirements

### Requirement: Import text using the existing composer
助手 SHALL 保持一个主页面，在输入区左下方增加导入文本入口；点击后 SHALL 复用同一输入区填写文档标题、类型和正文，或选择 TXT 文件。界面 MUST NOT 为导入新增页面、侧栏、弹窗或独立面板，且不得让用户手动切换销售／RAG 问答模式。

#### Scenario: Open import mode with a question draft
- **WHEN** 用户已输入问题但尚未发送，随后点击导入文本
- **THEN** 同一输入区切换为导入状态并保留原问题草稿，不跳转页面

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
