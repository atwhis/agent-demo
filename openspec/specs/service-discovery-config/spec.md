# service-discovery-config Specification

## Purpose

通过统一的注册发现和外部配置机制连接 AI 服务与销售服务，使运行地址和非敏感模型配置可以独立于应用包管理，同时避免凭据进入共享配置。

## Requirements

### Requirement: Local dependency environment
项目 SHALL 提供 Docker Compose 配置，使开发者能够启动带健康检查和持久化卷的 MySQL 8.4 LTS 与单机 Nacos 3.2.3，且销售服务与 AI 服务 SHALL 保持为宿主机运行的独立应用。

#### Scenario: Start local dependencies
- **WHEN** 开发者复制示例环境变量并执行 Docker Compose 启动命令
- **THEN** MySQL 使用同一个账号密码创建相互隔离的 `agent_demo_sales` 和 `nacos_config` 数据库，Nacos 使用 `nacos_config` 而非内置 Derby，并在 MySQL 健康后提供配置与注册发现端口

#### Scenario: Restart without losing local state
- **WHEN** 开发者正常停止后重新启动 Compose 环境
- **THEN** MySQL 业务数据和 Nacos 配置通过同一个 MySQL 命名卷保留，但分别位于两个数据库中

#### Scenario: Initialize an empty MySQL volume
- **WHEN** MySQL 数据目录为空且 Compose 首次启动
- **THEN** 初始化脚本创建两个数据库、授予同一个非 root 用户访问两库的权限，并向 `nacos_config` 导入与 Nacos Server 3.2.3 完全匹配的官方 MySQL schema

#### Scenario: Recreate local dependency data
- **WHEN** 开发者明确删除 Compose volume 后重新启动环境
- **THEN** 销售数据和 Nacos 配置同时被清空并按初始化流程重建，文档明确提示该操作不可恢复

### Requirement: Service registration and discovery
销售服务和 AI 服务 SHALL 注册到同一个 Nacos 3.x 环境，AI 服务 SHALL 通过销售服务名发现并调用销售内部 API。

#### Scenario: AI service discovers sales service
- **WHEN** 两个服务均成功启动并注册
- **THEN** AI 服务无需硬编码销售服务实例地址即可完成内部调用

### Requirement: External configuration import
两个服务 SHALL 通过应用配置导入 Nacos 配置，并支持按服务名和环境隔离配置。应用本地配置 SHALL 只保留连接 Nacos 所需的启动信息和框架级不变量；端口、业务数据库位置、模型元数据、演示数据开关和 SQL 日志级别等普通环境配置 SHALL 放入 Nacos。

#### Scenario: Load service configuration at startup
- **WHEN** 服务启动且 Nacos 中存在对应环境配置
- **THEN** 服务加载数据库、模型元数据和业务开关等非敏感配置

#### Scenario: Bootstrap before Nacos is available
- **WHEN** 任一服务或 Nacos Server 自身开始启动
- **THEN** application name、profile、Nacos 地址/namespace/group/凭据、配置导入声明及 Nacos 自身的 MySQL 连接信息可从本地配置或环境变量取得，不依赖尚未启动的 Nacos

### Requirement: Secret isolation
共用 MySQL 密码、DeepSeek API Key 和 OpenAI API Key MUST 由服务端环境变量或等效秘密注入机制提供，不得以实际值存储在普通 Nacos 配置、代码仓库或静态前端资源中。Nacos 配置 MAY 保存指向环境变量的占位符。

#### Scenario: Inspect Nacos model configuration
- **WHEN** 运维人员查看 Nacos 中的模型配置
- **THEN** 配置可包含模型 ID、显示名称、模型名称、基础地址和 `${DEEPSEEK_API_KEY}` / `${OPENAI_API_KEY}` 占位符，但不包含实际 API Key
