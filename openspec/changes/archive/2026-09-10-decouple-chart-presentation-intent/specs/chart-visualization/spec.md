## MODIFIED Requirements

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

## ADDED Requirements

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
