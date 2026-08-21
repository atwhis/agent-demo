## Purpose

提供结构清晰且可扩展的销售业务数据能力，使 AI Tool 能通过稳定的内部接口读取客户、商品、库存和订单数据，并以最近三个月的演示交易支撑统计分析。

## ADDED Requirements

### Requirement: Reusable sales client contract
系统 SHALL 在独立的销售 Client 模块中提供可由多个消费者复用的 Feign/Spring MVC 接口、请求响应 DTO 和必要公共枚举，销售 Controller SHALL 实现同一份接口契约，且 Client 模块 MUST NOT 依赖销售 Service 模块或暴露持久化实体。

#### Scenario: AI service consumes the sales contract
- **WHEN** AI 服务需要调用销售统计接口
- **THEN** AI 服务只依赖销售 Client 模块即可获得 Feign 代理、映射定义和类型化 DTO，无需依赖销售 Service 实现

#### Scenario: Sales service implements the shared contract
- **WHEN** 销售服务暴露统计 Controller
- **THEN** Controller 直接实现 Client 模块中的共享接口，并通过契约测试验证继承的映射、参数校验和序列化行为

### Requirement: Sales domain resources
系统 SHALL 维护客户、商品分类、商品、商品库存、销售订单和销售订单明细六类核心资源，并为每类资源提供稳定标识和创建、更新时间。物理表名 SHALL 分别为 `t_customer`、`t_product_category`、`t_product`、`t_product_inventory`、`t_sales_order` 和 `t_sales_order_item`。

#### Scenario: Order contains multiple products
- **WHEN** 一张销售订单包含多个不同商品
- **THEN** 系统在一条订单记录下保存多条订单明细，而不是为每个商品复制订单头

#### Scenario: Product belongs to a category
- **WHEN** 创建或读取一个商品
- **THEN** 商品关联一个有效商品分类，并可通过该关联参与分类统计

### Requirement: Application-managed relational integrity
销售物理表 MUST NOT 定义外键或 `CHECK` 约束；系统 SHALL 保留主键、`NOT NULL`、默认值、唯一索引和普通索引，并由业务服务在写入前校验逻辑引用、订单金额和库存不变量。

#### Scenario: Reject an invalid logical reference
- **WHEN** 业务服务收到引用不存在客户、分类、商品或订单的写入请求
- **THEN** 应用在写入数据库前拒绝该请求，而不是依赖数据库外键报错

#### Scenario: Reject an invalid inventory or amount
- **WHEN** 写入数据会导致负库存、锁定库存大于库存、非正数量或订单金额与明细汇总不一致
- **THEN** 业务服务拒绝写入，数据库 DDL 中不需要 `CHECK` 约束

### Requirement: Historical order snapshots
系统 SHALL 在订单明细中保存下单时的商品名称、商品分类、成交单价、数量和行金额，以保证商品主数据变化后历史订单仍保持原始交易语义。

#### Scenario: Product price changes after sale
- **WHEN** 商品当前价格在订单完成后发生变化
- **THEN** 历史订单仍展示下单时的单价和行金额

### Requirement: Order amount consistency
系统 SHALL 由业务服务计算每条订单明细金额和订单总金额，并保证订单总数量、总金额与明细汇总结果一致。

#### Scenario: Calculate multi-line order totals
- **WHEN** 创建一张包含多条有效明细的订单
- **THEN** 每条行金额等于成交单价乘以数量，订单总金额等于所有行金额之和

### Requirement: Inventory consistency
系统 SHALL 为每个商品维护唯一库存记录，并保证库存数量和锁定数量均不为负数，可用数量等于库存数量减去锁定数量。

#### Scenario: Read available inventory
- **WHEN** 下游请求某个商品的库存信息
- **THEN** 系统返回库存数量、锁定数量和计算后的可用数量

### Requirement: Three-month demo data
系统 SHALL 通过销售服务中的 Java 数据生成器，在业务库为空且演示数据开关启用时生成相对当前日期最近 90 天的演示销售数据，且重复启动不得重复插入；系统 MUST NOT 维护固定日期的大体量静态历史数据 SQL。

#### Scenario: Initialize an empty database
- **WHEN** 业务库没有演示数据且初始化开关启用
- **THEN** 系统生成客户、分类、商品、库存、订单和订单明细，并使交易时间覆盖最近三个月

#### Scenario: Restart with existing data
- **WHEN** 系统重启且业务库已经存在演示数据
- **THEN** 系统保留现有数据且不重复生成演示订单
