# chart-visualization Specification

## Purpose

以版本化、声明式且可验证的图表协议承载销售统计结果，确保前端图表完全来自业务 Tool 的可信数据，并避免执行模型生成的任意 ECharts JavaScript。

## Requirements

### Requirement: Fixed chart mapping
当用户没有表达明确展示意图时，系统 SHALL 将客户购买排行和热门商品排行默认映射为横向柱状图，将分类销售占比默认映射为饼图，将销售趋势默认映射为折线图。

#### Scenario: Render customer ranking
- **WHEN** 客户购买排行 Tool 返回成功且非空结果，且用户没有指定展示方式
- **THEN** 系统生成横向柱状图 ChartSpec

#### Scenario: Render category share
- **WHEN** 分类销售占比 Tool 返回成功且非空结果，且用户没有指定展示方式
- **THEN** 系统生成饼图 ChartSpec

#### Scenario: Render sales trend
- **WHEN** 销售趋势 Tool 返回成功且非空结果，且用户没有指定展示方式
- **THEN** 系统生成折线图 ChartSpec

### Requirement: Independent presentation intent
系统 SHALL 将业务查询意图与展示意图分别识别；用户明确指定 `bar`、`pie` 或 `line` 时，系统 SHALL 使用指定图表类型展示同一份可信业务结果，而不得改变所调用的销售统计能力。

#### Scenario: Request category sales as bars
- **WHEN** 用户请求查询分类销售占比并明确要求使用柱状图展示
- **THEN** 系统调用分类销售统计并将其可信结果编译为柱状图，而不是默认饼图

#### Scenario: Request product ranking as pie
- **WHEN** 用户请求热门商品排行并明确要求使用饼图展示
- **THEN** 系统调用热门商品统计并将其可信结果编译为饼图，而不是默认柱状图

#### Scenario: Request text only
- **WHEN** 用户明确要求只返回文字或不要图表
- **THEN** 系统返回业务回答且不发送 `chart` 事件

### Requirement: Presentation metric selection
用户明确指定销售额、销售数量、购买数量、订单数或销售占比作为图表指标时，系统 SHALL 在业务结果包含该指标的情况下使用它；未指定、无法识别或结果不包含该指标时，系统 SHALL 使用该业务统计的默认指标。

#### Scenario: Chart sales amount instead of quantity
- **WHEN** 用户请求热门商品排行并明确要求用柱状图展示销售额
- **THEN** 图表以商品为分类维度并以 Tool 返回的销售额为数值指标

#### Scenario: Requested metric is unavailable
- **WHEN** 展示意图指定的指标不在本次业务 Tool 结果中
- **THEN** 系统使用该业务统计的默认指标，且不得生成或推算缺失数据

### Requirement: Safe presentation fallback
系统 MUST 只接受 `AUTO`、`BAR`、`PIE`、`LINE`、`NONE` 展示意图；模型未提供展示意图时 SHALL 等同于 `AUTO`，无效展示参数 SHALL 安全回退到默认图表且不得影响业务结果。

#### Scenario: Model omits presentation intent
- **WHEN** 模型正确取得销售数据但没有提供独立展示意图
- **THEN** 系统按默认映射生成图表并正常完成回答

#### Scenario: Invalid presentation parameter
- **WHEN** 模型提供不受支持的图表类型或指标
- **THEN** 系统忽略无效展示参数并根据可信业务结果生成默认图表

### Requirement: Trusted chart data
ChartSpec 中的维度和值 SHALL 直接来自成功 Tool 结果，系统 MUST NOT 从模型生成的自然语言中提取、推算或改写统计数字。

#### Scenario: Model summary rounds a number
- **WHEN** 模型文字对销售金额进行了概括或四舍五入
- **THEN** 图表仍使用 Tool 返回的原始数值

### Requirement: Versioned declarative chart protocol
ChartSpec SHALL 包含协议版本、图表 ID、白名单类型、标题、维度、数据行、语义编码、安全展示选项、来源 Tool 和 sourceCallId，且不得包含可执行函数或完整 ECharts option。sourceCallId SHALL 对应当前轮一条具体的成功且非空销售调用，协议版本继续使用 1.0。

#### Scenario: Reject unsupported chart type
- **WHEN** 前端收到类型不在 bar、pie、line 白名单中的 ChartSpec
- **THEN** 前端拒绝渲染该图表且不执行其中任何内容

#### Scenario: Identify a repeated tool invocation
- **WHEN** 同一销售工具在当前轮调用两次并产生图表
- **THEN** 图表 sourceCallId 能唯一定位实际使用的那次调用结果

### Requirement: Chart completion gating
前端 SHALL 暂存 `chart` 事件，并且只有在随后收到成功 `done` 时才把图表加入最终回答。

#### Scenario: Stream fails after chart preparation
- **WHEN** 图表事件到达后流以错误结束且没有成功 `done`
- **THEN** 前端不把暂存图表显示为成功结果

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
