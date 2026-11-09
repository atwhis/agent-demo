# tool-call-tracing Specification

## Purpose

使学习者能够检查单轮模型实际执行过的每一次工具调用，区分重复调用、不同工具、成功空集、失败及取消，避免最后一次结果覆盖整轮执行证据。记录按会话有界保留且独立于发送给模型的聊天消息，不建设额外管理页面。

## Requirements

### Requirement: Independent records for every tool invocation
系统 SHALL 为每轮分配 turnId 并以该标识关联 traceId，为每次业务或展示工具调用分配唯一 callId。每条记录 SHALL 包含 conversationId、turnId、callId、工具名、参数、开始与结束时间、耗时、状态以及成功结果或安全错误信息；同一工具重复调用 MUST 保留为不同记录。

#### Scenario: Call the same tool twice
- **WHEN** 同一轮对热门商品工具使用两个日期范围分别查询
- **THEN** 两次调用各有独立 callId、参数和结果，任一记录不会覆盖另一条

#### Scenario: Record presentation separately
- **WHEN** 一轮同时调用销售查询工具和展示意图工具
- **THEN** 两者均有独立调用记录，展示意图结果不会替代业务数据

### Requirement: Observable empty failure and cancellation states
系统 MUST 区分运行中、成功、失败和取消的调用状态；成功返回空结果 SHALL 记录为成功并标明零行，而不是记录为错误。轮次终止后未完成调用 SHALL 收口为取消或失败状态，迟到回调不得改变已关闭轮次的图表来源或重新创建记录。

#### Scenario: Successful query returns no rows
- **WHEN** 销售服务成功返回空列表
- **THEN** 对应调用记录为成功且结果行数为零，可以与网络失败明确区分

#### Scenario: Cancel an in-flight request
- **WHEN** 用户取消时本轮仍有工具未完成
- **THEN** 本轮和未完成调用记录反映取消，不产生成功 done，且迟到结果不重新激活该轮

### Requirement: Bounded per-conversation trace access
系统 SHALL 在内存中按会话保留最近 10 次已接纳请求的轮次记录，包括失败和取消轮次；工具结果不得自动加入聊天记忆。系统 SHALL 提供只读 GET /api/conversations/{conversationId}/turns/{turnId}，返回该会话中对应轮次和全部调用记录；记录不存在、已淘汰或会话不匹配时 SHALL 返回未找到。

#### Scenario: Inspect retained calls
- **WHEN** 通过正确 conversationId 和 turnId 查询最近一次轮次
- **THEN** 返回该轮的全部独立调用记录，且查询本身不触发模型或销售接口

#### Scenario: Inspect an evicted or mismatched turn
- **WHEN** 请求已淘汰的 turnId 或使用另一个 conversationId 查询
- **THEN** 返回未找到且不暴露其他会话的记录

### Requirement: Trace data excludes internal secrets
调用记录 MUST NOT 包含模型凭据、系统提示词或模型思维内容；错误信息 SHALL 可供调试且不泄露连接秘密。普通助手页面 SHALL 不直接显示工具名称、参数或原始中间结果。

#### Scenario: Provider or downstream request fails
- **WHEN** 保存和查询失败调用记录
- **THEN** 可以看到安全错误类型与耗时，但不包含 API Key、数据库密码或系统提示词
