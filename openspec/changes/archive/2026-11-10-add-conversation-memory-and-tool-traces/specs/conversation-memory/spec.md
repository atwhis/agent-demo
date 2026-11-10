## Purpose

让用户能够在同一助手会话中连续补充或修改销售查询条件，同时明确消息窗口、会话隔离以及成功、失败和取消时的保存边界。全部记忆只在当前应用进程内保留，为后续知识问答复用相同会话提供基础，不形成长期用户记忆。

## ADDED Requirements

### Requirement: Bounded in-memory conversation window
系统 SHALL 按 conversationId 在内存中保存最近 10 轮完整用户／助手消息，每轮由用户问题与最终助手回答组成，最多 20 条；超出窗口时 SHALL 移除最旧完整轮次，不生成摘要。服务重启后 MUST 清空记忆，模型上下文不得包含其他会话的消息。

#### Scenario: Evict the oldest complete turn
- **WHEN** 在模型上下文预算充足时，同一会话完成第 11 轮回答
- **THEN** 后续请求的历史只包含第 2 至第 11 轮，且不存在只剩用户问题或助手回答的孤立半轮

#### Scenario: Restart the application
- **WHEN** 应用重启后用户继续使用此前的 conversationId
- **THEN** 系统将其作为空上下文处理，不恢复之前的消息

### Requirement: Follow-up context without stale business data
系统 SHALL 在调用所选模型时提供同一会话最近的完整消息，使用户能继承并修改时间范围、排行数量、趋势粒度和展示偏好；历史消息中的业务数字 MUST NOT 代替当前轮销售查询。

#### Scenario: Refine a previous ranking
- **WHEN** 用户先查询上个月热门商品，再追问“只看前三名”，随后要求“改成最近三个月”
- **THEN** 每轮继承未修改的条件并执行新的销售查询，最终查询使用最近三个月和前三名

### Requirement: Commit only complete answers
系统 SHALL 只在本轮满足成功条件时将用户问题和完整助手回答成对提交到记忆；失败、取消或不完整流 MUST NOT 把半截回答写入有效窗口。系统 SHALL 按完整轮次控制模型上下文预算，必要时进一步移除最旧轮次。

#### Scenario: Cancel a partial answer
- **WHEN** 用户在部分文字已经到达后取消生成
- **THEN** 下一次请求不读取本次未完成的用户／助手消息对，之前成功轮次保持有效

### Requirement: New conversation clears prior session state
系统 SHALL 提供幂等的 DELETE /api/conversations/{conversationId} 清理接口；清理成功后 MUST 移除该会话消息、保留的调用记录和活动轮次的写入资格。新会话 SHALL 使用新的 conversationId，旧请求的迟到结果不得重新创建已清理状态。

#### Scenario: Clear a session while work is finishing
- **WHEN** 用户新建会话并成功清理旧会话，而旧工具调用稍后返回
- **THEN** 新会话看不到旧消息，旧会话记忆和调用记录也不会被迟到结果重新写回

#### Scenario: Clear an already absent session
- **WHEN** 重复清理同一 conversationId 或清理不存在的会话
- **THEN** 清理接口仍成功结束且不创建任何会话数据

### Requirement: One active turn per conversation
系统 MUST 在同一 conversationId 上同时只接纳一个活动轮次；不同会话 SHALL 可以独立执行。被拒绝的重复请求不得调用模型或追加会话消息。

#### Scenario: Concurrent submissions for one session
- **WHEN** 同一会话已有未终止的请求，又收到第二次提交
- **THEN** 第二次提交得到可展示的会话忙错误，第一轮的消息和调用记录不受影响
