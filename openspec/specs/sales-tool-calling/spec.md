# sales-tool-calling Specification

## Purpose

将自然语言销售问题转换为受控的业务统计调用，使 DeepSeek 和 OpenAI 都只能通过已注册 Tool 获取真实销售数据，并基于相同结果生成最终回答。

## Requirements

### Requirement: Four sales analysis tools
系统 SHALL 向所选模型提供客户购买排行、分类销售占比、热门商品排行和销售趋势四个名称唯一、描述明确且参数结构化的 Tool。

#### Scenario: Ask for popular products
- **WHEN** 用户询问最近一个月销售数量最多的商品
- **THEN** 模型可选择热门商品排行 Tool，并提供确定的开始日期、结束日期和排行数量

#### Scenario: Ask for sales trend
- **WHEN** 用户询问最近三个月按周销售趋势
- **THEN** 模型可选择销售趋势 Tool，并提供 `WEEK` 粒度和确定日期范围

### Requirement: Deterministic relative dates
系统 SHALL 在模型上下文中提供当前日期和 `Asia/Shanghai` 时区，并要求模型将“最近一个月”“上个月”“最近三个月”等表达转换为确定日期参数。

#### Scenario: Resolve recent month
- **WHEN** 用户在 2026-08-20 询问最近一个月的销售排行
- **THEN** Tool 请求使用明确的开始日期和结束日期，而不是把“最近一个月”原文传给业务服务

### Requirement: Tool input validation
系统 MUST 在调用销售服务前验证 Tool 名称、必填参数、日期范围、排行数量和趋势粒度，未知 Tool 不得执行。

#### Scenario: Model supplies invalid arguments
- **WHEN** 模型生成开始日期晚于结束日期的 Tool 参数
- **THEN** 系统阻止业务调用并使本次请求以受控错误结束

### Requirement: Complete tool calling loop
当模型请求调用 Tool 时，系统 SHALL 执行对应销售统计、把结构化结果返回同一模型，并继续调用模型直至获得不含 Tool 请求的最终回答。

#### Scenario: Tool result becomes final answer context
- **WHEN** 销售统计 Tool 成功返回客户排行数据
- **THEN** 所选模型基于该数据生成面向用户的 Markdown 总结

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
