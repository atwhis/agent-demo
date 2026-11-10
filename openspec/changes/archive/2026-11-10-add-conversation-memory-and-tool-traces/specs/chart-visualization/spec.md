## MODIFIED Requirements

### Requirement: Versioned declarative chart protocol
ChartSpec SHALL 包含协议版本、图表 ID、白名单类型、标题、维度、数据行、语义编码、安全展示选项、来源 Tool 和 sourceCallId，且不得包含可执行函数或完整 ECharts option。sourceCallId SHALL 对应当前轮一条具体的成功且非空销售调用，协议版本继续使用 1.0。

#### Scenario: Reject unsupported chart type
- **WHEN** 前端收到类型不在 bar、pie、line 白名单中的 ChartSpec
- **THEN** 前端拒绝渲染该图表且不执行其中任何内容

#### Scenario: Identify a repeated tool invocation
- **WHEN** 同一销售工具在当前轮调用两次并产生图表
- **THEN** 图表 sourceCallId 能唯一定位实际使用的那次调用结果

## ADDED Requirements

### Requirement: One chart from the last eligible sales call
每轮 SHALL 最多生成一个图表，使用本轮最后完成的成功且非空销售调用作为来源；完成顺序 SHALL 可确定。展示意图沿用现有规则，NONE 不生成图表。展示工具、失败调用、成功空集及其他轮次记录 MUST NOT 覆盖合格来源，图表不得拼接多次调用的业务数值。

#### Scenario: Multiple sales tools return data
- **WHEN** 本轮多个销售调用成功且非空
- **THEN** 图表仅使用最后完成的合格调用，其他调用仍独立保留

#### Scenario: Empty result follows a valid result
- **WHEN** 一次非空销售查询之后另一销售查询成功返回空集
- **THEN** 在整轮满足成功条件时，空集不覆盖此前合格图表来源

#### Scenario: Presentation tool executes last
- **WHEN** 销售查询完成后最后执行的是展示意图工具
- **THEN** 图表仍来自合格销售调用，并按展示偏好选择类型或抑制展示
