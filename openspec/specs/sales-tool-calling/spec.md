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
需要销售数据的问题在未获得成功且非空的销售 Tool 结果时，系统 MUST NOT 把模型生成的数字性回答作为成功结果返回。

#### Scenario: Model skips required sales tool
- **WHEN** 用户询问当前销售排行但模型没有产生销售 Tool 结果
- **THEN** 系统不返回包含臆测销售数字的成功回答
