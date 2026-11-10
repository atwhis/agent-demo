# knowledge-retrieval Specification

## Purpose

对用户知识问题完成从候选召回到重排筛选、证据约束生成及来源展示的完整流程，区分知识缺失与系统故障。检索和重排在本地执行，生成继续复用用户所选对话模型，使学习者能够查看每轮候选及最终证据而无需新建追踪面板。

## Requirements

### Requirement: Local inference and persistent vector retrieval
系统 SHALL 使用已确认的本地中文 Embedding 和 ARM64 量化重排模型，不在请求中调用远程 Embedding／Rerank 服务或自动下载权重。文档和查询 SHALL 使用同一 Embedding 配置；向量存储 SHALL 为 512 维，使用 HNSW 索引和余弦距离。缺少模型或外部权重时 MUST 返回明确的配置故障，不得生成随机向量或伪装为无资料。

#### Scenario: Retrieve with installed local models
- **WHEN** 已有文档且本地推理资源可用
- **THEN** 检索和重排在本地完成，查询使用与已存文档一致的向量空间

#### Scenario: Required local asset is missing
- **WHEN** 本地模型、tokenizer 或模型引用的外部权重缺失
- **THEN** 系统明确报告模型不可用，不返回成功的空知识答案

### Requirement: Recall fifteen then rerank five
每次 RAG 请求 SHALL 使用结合历史补全后的独立问题，召回最多 15 个候选；对候选逐一或分批执行问题／片段配对重排，按分数降序保留最多 5 个相关片段。候选不足时 SHALL 使用实际数量，空候选不得调用重排。默认 SHALL 搜索全部文档类型，不得仅因文档用途标签而排除相关资料。

#### Scenario: Rerank a full candidate set
- **WHEN** 检索得到 15 个候选片段
- **THEN** 全部候选参与重排，只有排序后的最多 5 个相关片段进入本轮生成上下文

#### Scenario: Retrieve fewer candidates
- **WHEN** 知识库只能返回 3 个候选
- **THEN** 只重排这 3 个候选，不补造其他片段

### Requirement: Pair token budget and independent scores
重排输入 SHALL 使用重排模型自身 tokenizer 编码问题与片段，并使整对输入连同特殊 token 不超过 512。过长问题 SHALL 请求用户精简，不能默默改变核心条件。向量分与重排分 SHALL 分别记录，不混用阈值，不将原始重排输出宣称为可信概率。

#### Scenario: Pair approaches model input limit
- **WHEN** 对候选片段构造问题／片段配对输入
- **THEN** 使用实际重排 tokenizer 检查完整长度，标题及特殊标记也计入预算

### Requirement: Evidence constrained answer and friendly no-answer
RAG 回答 SHALL 仅依据本轮最终片段生成，知识库为空、无相关候选或片段不能支持答案时 SHALL 友好说明资料不足并正常完成，提示用户导入相关文本或调整问题。检索、推理或数据库故障 MUST 作为受控 error，不得伪装成无资料；无资料时不得自动切换销售查询或让模型补编事实。

#### Scenario: No relevant knowledge exists
- **WHEN** 用户询问的规则在本轮检索资料中没有依据
- **THEN** 返回友好的资料不足说明，不编造规则，不调用销售工具，并正常结束本轮

#### Scenario: Vector database fails
- **WHEN** 向量数据库不可用
- **THEN** 返回检索服务失败的 error，不发送成功 done，也不声称知识库没有资料

### Requirement: Verifiable sources and retrieval trace
成功的有依据回答 SHALL 返回本轮实际使用且经校验的来源，包含 documentId、chunkId、标题、章节及引用片段；回答不得引用未提供的来源。系统 SHALL 在对应 turnId 的有限内存记录中附加独立问题、候选 ID、向量分、重排分和最终片段 ID，不将整批候选自动写入聊天消息。

#### Scenario: Inspect answer evidence
- **WHEN** RAG 成功生成带引用的回答并查询该轮记录
- **THEN** 页面来源与记录中的最终片段对应，所有来源均来自本轮实际检索证据

#### Scenario: Import then start a new conversation
- **WHEN** 用户导入知识后清理会话并新建会话
- **THEN** 新会话不继承旧对话消息，但仍可检索已持久化知识
