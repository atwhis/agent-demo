## MODIFIED Requirements

### Requirement: Local dependency environment
项目 SHALL 提供 Docker Compose 配置，使开发者能够启动带健康检查和持久化卷的 MySQL 8.4 LTS、单机 Nacos 3.2.3 以及知识库 PostgreSQL／PgVector。销售服务和 AI 服务 SHALL 保持为宿主机运行的独立应用；知识文档和向量 SHALL 存放在独立于销售库与 Nacos 库的 PostgreSQL 数据库及数据卷中。

#### Scenario: Start local dependencies
- **WHEN** 开发者复制示例环境变量并执行 Docker Compose 启动命令
- **THEN** MySQL 使用同一个账号密码创建相互隔离的 agent_demo_sales 和 nacos_config 数据库，Nacos 使用 nacos_config 而非内置 Derby，并在 MySQL 健康后提供配置与注册发现端口；PgVector 独立启动并提供知识库存储

#### Scenario: Restart without losing local state
- **WHEN** 开发者正常停止后重新启动 Compose 环境
- **THEN** MySQL 业务数据和 Nacos 配置通过同一个 MySQL 命名卷保留但分别位于两个数据库中，知识文档与向量通过独立 PostgreSQL 命名卷保留，内存会话不会因此变为持久化

#### Scenario: Initialize an empty MySQL volume
- **WHEN** MySQL 数据目录为空且 Compose 首次启动
- **THEN** 初始化脚本创建两个数据库、授予同一个非 root 用户访问两库的权限，并向 nacos_config 导入与 Nacos Server 3.2.3 完全匹配的官方 MySQL schema

#### Scenario: Recreate local dependency data
- **WHEN** 开发者明确删除 Compose volume 后重新启动环境
- **THEN** 被删除卷中的销售数据、Nacos 配置或知识库按各自初始化流程重建，文档明确提示相应数据清理不可恢复

### Requirement: External configuration import
两个服务 SHALL 通过应用配置导入 Nacos 配置，并支持按服务名和环境隔离配置。应用本地配置 SHALL 只保留连接 Nacos 所需的启动信息和框架级不变量；端口、业务数据库位置、模型元数据、演示数据开关、SQL 日志级别，以及 AI 知识库连接、模型资源位置、切片和检索参数等普通环境配置 SHALL 放入 Nacos。

#### Scenario: Load service configuration at startup
- **WHEN** 服务启动且 Nacos 中存在对应环境配置
- **THEN** 服务加载数据库、模型元数据和业务开关等非敏感配置，AI 服务同时获得知识库及本地推理参数

#### Scenario: Bootstrap before Nacos is available
- **WHEN** 任一服务或 Nacos Server 自身开始启动
- **THEN** application name、profile、Nacos 地址／namespace／group／凭据、配置导入声明及 Nacos 自身 MySQL 连接信息可从本地配置或环境变量取得，不依赖尚未启动的 Nacos

### Requirement: Secret isolation
共用 MySQL 密码、知识库 PostgreSQL 密码、DeepSeek API Key 和 OpenAI API Key MUST 由服务端环境变量或等效秘密注入机制提供，不得以实际值存储在普通 Nacos 配置、代码仓库或静态前端资源中。Nacos 配置可以保存指向环境变量的占位符。

#### Scenario: Inspect Nacos model configuration
- **WHEN** 运维人员查看 Nacos 中的模型配置
- **THEN** 配置可包含模型 ID、显示名称、模型名称、基础地址和环境变量占位符，但不包含实际 API Key

#### Scenario: Inspect knowledge connection configuration
- **WHEN** 查看知识库 Nacos 配置、静态前端或接口返回
- **THEN** 可以核对非敏感连接或模型信息，但不能获得 PostgreSQL 密码或模型凭据
