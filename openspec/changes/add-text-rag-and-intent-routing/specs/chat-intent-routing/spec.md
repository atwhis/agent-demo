## Purpose

在一个助手入口中区分实际销售数据查询和用户知识资料问答，并结合最近会话恢复省略条件，避免只靠“销售”等关键词误判规则问题。混合需求或信息不足时通过追问明确单一目标，使每轮只执行一条查询链路且保留可解释的路由结果。

## ADDED Requirements

### Requirement: Structured three-way intent decision
系统 SHALL 使用请求所选 LLM，结合当前问题、最近有效会话、业务日期和已有销售统计能力，输出并校验 SALES、RAG 或 CLARIFY，以及独立问题；需要追问时 SHALL 提供可展示的澄清问题。非法结构 SHALL 最多进行一次格式纠正，仍非法或分类服务失败时 MUST 返回受控错误，不得静默执行任意链路。

#### Scenario: Ask for live sales figures
- **WHEN** 用户询问上个月热门商品前三名
- **THEN** 选择 SALES，保留明确的排行目标与时间条件

#### Scenario: Ask about a non-business document
- **WHEN** 用户询问明确的知识问题，例如《西游记》中唐僧有几个徒弟
- **THEN** 选择 RAG 并保留书名和人物等检索条件，由检索结果判断资料是否足够

#### Scenario: Ask for sales definitions
- **WHEN** 用户询问哪些订单状态计入销售额
- **THEN** 选择 RAG，不因问题包含“销售额”就调用销售工具

### Requirement: Resolve follow-ups from conversation
系统 SHALL 为追问补全当前问题需要的主体、时间和指代，不添加用户未提出的业务条件；路由不得简单固定为上一轮类别。RAG 检索 SHALL 使用补全问题，SALES 模型 SHALL 同时保留原问法及必要历史。

#### Scenario: Switch from figures to a definition
- **WHEN** 用户看完销售额趋势后追问“这个指标是怎么定义的”
- **THEN** 恢复指标主体并路由 RAG，不重复查询实际销售趋势

#### Scenario: Missing antecedent
- **WHEN** 新会话仅输入“还是看前三名”且没有可用上下文
- **THEN** 返回 CLARIFY 请求补充查询对象，而不猜测统计类型

### Requirement: Clarify mixed or unsupported requests
同时要求资料解释和实际销售数据的问题 SHALL 路由 CLARIFY，先询问用户选择哪一部分；本期 MUST NOT 同轮执行混合查询。用户选择后 SHALL 从原问题恢复该部分的条件，再仅执行选定链路。超出已提供销售统计能力且无法明确回答的问题 SHALL 澄清支持范围。

#### Scenario: Mixed request then sales selection
- **WHEN** 用户要求“按销售统计制度解释上个月销售趋势”，随后选择“先查实际数据”
- **THEN** 第一轮只追问并保存完整澄清消息，下一轮只查询上个月销售趋势，不检索制度

### Requirement: Route execution isolation
SALES SHALL 仅启用销售及展示意图工具，RAG SHALL 仅检索资料并使用无销售工具的生成模型，CLARIFY SHALL 不调用销售或向量检索。分类模型 SHALL 不注册执行工具；导入文本和检索片段只能作为资料，不得提升为系统指令或赋予执行工具权限。

#### Scenario: RAG document includes tool instructions
- **WHEN** 检索片段包含要求执行内部销售工具的文字
- **THEN** 该轮仍只进行知识问答，不执行文档中的工具指令

### Requirement: Document type is not query route
系统 SHALL 把制度规则、产品说明、操作指南和其他作为知识内容用途标签，不把这些标签直接映射为 SALES 或 RAG。分类／改写产生的内部消息 SHALL 不进入用户聊天历史；路由结果 SHALL 作为轮次元数据保存。

#### Scenario: Import sales-related rules
- **WHEN** 用户导入一份销售制度并询问其条款
- **THEN** 文档标签为制度规则且问题走 RAG，不把销售相关资料等同于实时销售查询
