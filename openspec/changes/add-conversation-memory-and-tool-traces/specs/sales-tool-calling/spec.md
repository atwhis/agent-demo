## MODIFIED Requirements

### Requirement: No fabricated business result
需要销售数据的本轮问题及其连续追问 SHALL 有对应的本轮成功销售调用证据；系统 MUST NOT 将历史结果或未执行查询时模型臆测的业务数字作为成功结果。成功空集 SHALL 允许正常说明没有数据并完成回答；调用失败或缺少所需销售查询时 MUST 以受控错误结束，不发送成功 done。

#### Scenario: Model skips required sales tool
- **WHEN** 用户询问当前销售排行但模型没有产生销售 Tool 结果
- **THEN** 系统不返回包含臆测销售数字的成功回答，并以 error 结束而不是发送 done

#### Scenario: Follow-up omits business keywords
- **WHEN** 用户在销售排行之后仅追问“只看前三名”
- **THEN** 本轮仍重新查询，并按本轮调用记录验证成功，不因当前文本缺少“销售”等关键词而放行猜测结果

#### Scenario: Empty sales result is a valid outcome
- **WHEN** 所需销售查询全部成功但返回空集
- **THEN** 助手明确说明对应范围没有数据，不编造数字、不生成图表，并正常完成本轮

#### Scenario: One required sales call fails
- **WHEN** 一轮需要多个销售查询而其中一个调用失败
- **THEN** 本轮以受控错误结束，其他成功记录仍可供调试，但不显示为完整成功的报告
