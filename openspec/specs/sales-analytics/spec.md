# sales-analytics Specification

## Purpose

向 AI 服务提供口径明确、结果可复现的销售统计能力，使客户排行、分类占比、热门商品和销售趋势中的数字全部由业务系统计算而非由大模型推断。

## Requirements

### Requirement: Effective-order scope
所有销售统计 SHALL 仅包含状态为 `PAID` 或 `COMPLETED` 的订单，并排除 `CREATED` 和 `CANCELLED` 订单。

#### Scenario: Cancelled order is excluded
- **WHEN** 查询范围内同时存在已完成订单和已取消订单
- **THEN** 统计结果只包含已支付或已完成订单的明细

### Requirement: Customer purchase ranking
系统 SHALL 按指定时间范围统计客户购买商品总数量，并按商品数量降序返回限定数量的客户排行，同时返回订单数和销售金额作为补充指标。

#### Scenario: Query recent customer ranking
- **WHEN** 请求指定开始日期、结束日期和排行数量
- **THEN** 系统按有效订单的商品数量降序返回客户名称、购买数量、订单数和销售金额

### Requirement: Category sales share
系统 SHALL 按指定时间范围统计商品分类销售额占全部有效销售额的比例，并同时返回分类销售数量、销售金额和百分比。

#### Scenario: Query category share
- **WHEN** 请求最近三个月商品分类销售占比
- **THEN** 系统按订单明细分类快照汇总销售额，并返回可组成完整总体的分类占比数据

### Requirement: Popular product ranking
系统 SHALL 按指定时间范围统计具体商品的销售数量排行，同一商品在不同订单中的明细 SHALL 合并统计。

#### Scenario: Aggregate the same product across orders
- **WHEN** 同一个商品出现在多个有效订单中
- **THEN** 系统合计该商品的销售数量、销售金额和订单数后参与排行

### Requirement: Sales trend
系统 SHALL 支持按日、周或月统计订单数、商品销售数量和销售金额，并为范围内无交易的时间桶返回零值。

#### Scenario: Query daily sales trend
- **WHEN** 请求按日统计指定日期范围的销售趋势
- **THEN** 系统按日期升序返回连续时间桶及每个时间桶的订单数、销售数量和销售金额

#### Scenario: Query weekly or monthly sales trend
- **WHEN** 请求粒度为 `WEEK` 或 `MONTH`
- **THEN** 系统按统一周起始日或自然月聚合并返回有序结果

### Requirement: Analytics parameter validation
系统 MUST 拒绝开始日期晚于结束日期、排行数量超出 1 至 50、或趋势粒度不属于 `DAY`、`WEEK`、`MONTH` 的请求。

#### Scenario: Reject invalid time range
- **WHEN** 请求的开始日期晚于结束日期
- **THEN** 系统返回明确的参数错误且不执行统计查询

### Requirement: Development SQL and parameter logging
销售服务 SHALL 能够通过 Nacos 中的日志配置开启或关闭 Spring JDBC 统计 SQL 模板和绑定参数日志，一期 MUST NOT 为此引入 P6Spy，且不要求记录查询耗时。

#### Scenario: Enable SQL diagnostics
- **WHEN** 本地或开发环境将 `JdbcTemplate` 日志设为 `DEBUG` 且将 `StatementCreatorUtils` 日志设为 `TRACE`
- **THEN** 四类统计调用输出所执行的 SQL 模板和绑定参数，便于观察 Tool 到业务查询的映射

#### Scenario: Disable SQL diagnostics
- **WHEN** 对应 JDBC 日志级别设置为 `OFF`
- **THEN** 系统不输出 SQL 模板和绑定参数，生产环境默认采用该状态
