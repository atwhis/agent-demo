## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: New conversation within the existing page
前端 SHALL 在现有助手主页面提供新会话操作，停止当前请求、请求清理旧会话并重置本地聊天与会话标识；清理失败 SHALL 友好提示，不能宣称服务端清理成功。系统 MUST 使用请求归属标识隔离旧流，旧流的任何迟到事件不得写入新会话界面。

#### Scenario: Start a new conversation during streaming
- **WHEN** 用户在回答尚未完成时点击新会话
- **THEN** 页面回到空会话状态并停止旧流，旧回答或完成事件不会再次出现在新会话

#### Scenario: Clear fails remotely
- **WHEN** 新会话操作中的服务端清理请求失败
- **THEN** 前端提示未能清理旧会话，同时保持新旧请求隔离，不恢复旧流
