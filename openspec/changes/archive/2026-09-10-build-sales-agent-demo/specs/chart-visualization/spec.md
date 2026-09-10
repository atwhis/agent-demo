## Purpose

以版本化、声明式且可验证的图表协议承载销售统计结果，确保前端图表完全来自业务 Tool 的可信数据，并避免执行模型生成的任意 ECharts JavaScript。

## ADDED Requirements

### Requirement: Fixed chart mapping
系统 SHALL 将客户购买排行和热门商品排行映射为横向柱状图，将分类销售占比映射为饼图，将销售趋势映射为折线图。

#### Scenario: Render customer ranking
- **WHEN** 客户购买排行 Tool 返回成功且非空结果
- **THEN** 系统生成横向柱状图 ChartSpec

#### Scenario: Render category share
- **WHEN** 分类销售占比 Tool 返回成功且非空结果
- **THEN** 系统生成饼图 ChartSpec

#### Scenario: Render sales trend
- **WHEN** 销售趋势 Tool 返回成功且非空结果
- **THEN** 系统生成折线图 ChartSpec

### Requirement: Trusted chart data
ChartSpec 中的维度和值 SHALL 直接来自成功 Tool 结果，系统 MUST NOT 从模型生成的自然语言中提取、推算或改写统计数字。

#### Scenario: Model summary rounds a number
- **WHEN** 模型文字对销售金额进行了概括或四舍五入
- **THEN** 图表仍使用 Tool 返回的原始数值

### Requirement: Versioned declarative chart protocol
ChartSpec SHALL 包含协议版本、图表 ID、白名单类型、标题、维度、数据行、语义编码、安全展示选项和来源 Tool，且不得包含可执行函数或完整 ECharts option。

#### Scenario: Reject unsupported chart type
- **WHEN** 前端收到类型不在 `bar`、`pie`、`line` 白名单中的 ChartSpec
- **THEN** 前端拒绝渲染该图表且不执行其中任何内容

### Requirement: Chart completion gating
前端 SHALL 暂存 `chart` 事件，并且只有在随后收到成功 `done` 时才把图表加入最终回答。

#### Scenario: Stream fails after chart preparation
- **WHEN** 图表事件到达后流以错误结束且没有成功 `done`
- **THEN** 前端不把暂存图表显示为成功结果
