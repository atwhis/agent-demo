## MODIFIED Requirements

### Requirement: No fabricated business result
仅 SALES 路由 SHALL 执行本轮销售调用证据守卫；该路由的问题及连续追问 SHALL 有对应的本轮成功销售调用证据，系统 MUST NOT 将历史结果或未执行查询时模型臆测的业务数字作为成功结果。成功空集 SHALL 允许正常说明没有数据并完成回答；调用失败或缺少所需销售查询时 MUST 以受控错误结束，不发送成功 done。RAG 与 CLARIFY 不得被要求调用销售工具才能完成。

#### Scenario: Model skips required sales tool
- **WHEN** 用户询问当前销售排行且路由 SALES，但模型没有产生销售 Tool 结果
- **THEN** 系统不返回包含臆测销售数字的成功回答，并以 error 结束而不是发送 done

#### Scenario: Follow-up omits business keywords
- **WHEN** 用户在销售排行之后仅追问“只看前三名”，并被判定为 SALES
- **THEN** 本轮仍重新查询，并按本轮调用记录验证成功，不因当前文本缺少“销售”等关键词而放行猜测结果

#### Scenario: Empty sales result is a valid outcome
- **WHEN** SALES 所需销售查询全部成功但返回空集
- **THEN** 助手明确说明对应范围没有数据，不编造数字、不生成图表，并正常完成本轮

#### Scenario: One required sales call fails
- **WHEN** SALES 一轮需要多个销售查询而其中一个调用失败
- **THEN** 本轮以受控错误结束，其他成功记录仍可供调试，但不显示为完整成功的报告

#### Scenario: Knowledge response requires no sales call
- **WHEN** 含有“销售”或“商品”的知识问题被路由 RAG
- **THEN** 仅按知识证据判定结果，不因没有销售调用而触发业务数据未验证错误
