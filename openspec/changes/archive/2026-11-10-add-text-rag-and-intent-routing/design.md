## Context

动机和功能边界见 proposal.md。本项依赖 `add-conversation-memory-and-tool-traces` 的已实现会话窗口、轮次记录、清理接口和 start／done 契约；实现前先验收并同步前置变更。当前只创建提案，不把 CLI 的文档完成状态当作依赖代码已实现。

AI 服务目前给两个 ChatClient 默认注册销售和展示工具，并以业务关键词判断是否必须有 Tool 结果。这会误伤含“销售”等词的制度问题，需要在本项改为路由后装配能力。

本地资源已静态检查：
- `src/main/resources/models/embedding/` 为 BGE small 中文导出，model.onnx 引用同目录 model.onnx_data，输出包含 `[batch,512]` 的 sentence_embedding；计算图已包含 CLS pooling 和归一化。
- `src/main/resources/models/reranker/model_qint8_arm64.onnx` 与 cross-encoder/mmarco-mMiniLMv2-L12-H384-v1 的 ARM64 INT8 发布文件 SHA256 一致，配套 tokenizer 和配置已齐全；输入为 input_ids／attention_mask，输出为 `[batch,1]` logits。
- 上述事实不代表已经运行成功。Embedding 导出包含 opset 21 与扩展算子，必须先验证所选 Java Runtime 和 M1 本地库支持。

## Goals / Non-Goals

**Goals:**
- 在现有 AI 进程中完成本地向量化／重排，沿用用户已经准备的模型和一个助手页面。
- 明确路由、文档用途、检索证据和会话消息四种不同数据，降低相互污染的风险。
- 让 Top15→Top5 以及无资料、系统故障、取消等分支可通过固定小样例验证。

**Non-Goals:**
- 不选择新的 reranker，不引入 Python 常驻服务、LLM 本地化、混合查询或独立文档管理界面。
- 不实现 OCR、复杂 Markdown／表格解析、Hybrid Search、自动摘要、评测平台或会话持久化。
- 忽略 doc/rag-system-design.md，该未实现的旧设计不是本变更的实现依据。

## Decisions

### 1. 本地模型适配先做可运行验证

使用 Java ONNX Runtime 和能读取 tokenizer.json 的 Java tokenizer；先锁定实际可在 M1 原生 ARM64 JDK 上加载两个图的依赖版本，再接业务链路。CPU 推理作为本期基线，Embedding、重排和 JDBC 等阻塞工作放到有界工作线程，不占 SSE 事件循环。

Embedding 提供 Spring AI EmbeddingModel 适配，直接读取 sentence_embedding，不对该二维输出再做 mean pooling。查询使用 BGE 推荐检索前缀，文档不加查询前缀；入库和查询共用同一模型及编码约定。

Reranker 使用自己的 Unigram tokenizer，按配对模板编码问题与片段，只传模型声明的 input_ids 和 attention_mask，读取单个原始 logit 排序；不复用 Embedding tokenizer，也不把 logit 当概率。运行时显式配置 padding 和长度控制，不能假设 tokenizer_config.json 会自动生效。

当前资源保留在 resources/models 下；文件系统运行可直接按路径加载，打包 JAR 时把模型所需文件完整展开到应用管理的模型目录后以路径创建 Session，model.onnx_data 与模型保持相对位置。资源只加载一次，推理张量／结果及时关闭，应用关闭时释放 Session 和 tokenizer。不移动用户现有模型，不在请求中下载文件。

替代方案是直接改通用 Transformers 模型路径或另起 Python 服务；前者不能保证本导出的输出和外部权重装载正确，后者增加进程与部署成本，因此做两块小型 Java 适配。

### 2. 单份小文本同步导入，文档与向量共同持久化

提供 text JSON 和单文件 upload 两个入口，元数据为 title、documentType（AUTO 或四个类型）、modelId，正文上限 2 MiB，标题上限 80 字。校验 UTF-8、文本内容和显式类型后，去掉 BOM、统一换行并计算内容 SHA256；相同正文返回已存在文档的实际元数据，不默默改标题或标签。

选择 AUTO 时使用当前选择的无 Tool ChatClient，根据标题和有界正文样本识别文档用途；失败回退其他，导入结果说明实际类型。用户指定类型则跳过自动分类。分类与文档用途标签不决定问答路由。

在 PostgreSQL 中保留轻量文档表（ID、标题、正文、类型、内容哈希、片段数、时间）和 PgVector 片段表。片段元数据包含 documentId、chunkId、title、documentType、sectionPath、chunkIndex。内容哈希有唯一约束，处理重复或并发导入；文档记录和全部向量写入同一数据源的单文档事务，失败回滚，已有资料不受影响。小文本同步入库期间的本地计算成本本期可接受，不引入后台队列。

### 3. 结构优先的有限切片规则

识别明确的 `#` 标题、中文章节标题、条款／编号和空行段落，在同一章节内拼接短段落；超过预算时按中英文句末标点，再按 tokenizer 边界细分，保留原文顺序。没有结构的 TXT 直接按段落与 token 处理，不做复杂语义分段。

初始切片目标 320、上限 400、长段重叠 40 个 Embedding token，标题／章节前缀也计入。最终片段同时检查 Embedding 与 Reranker tokenizer 的正文预算，任一不满足即继续拆分；重叠只发生于被拆开的长段／条款，不跨顶层章节机械复制。

Reranker 的配对上限为 512，给独立问题预留至多 96 个重排 token、片段至多 400 个重排 token，并计入 4 个特殊标记。问题本身或加上 BGE 查询前缀后超出各自预算时，提示精简问题而不截断关键条件。入库计数禁用静默截断，实际推理前再检查整体长度。

替代方案是始终固定字符数切片；这不能满足结构保留，也不能可靠约束两个模型的输入，因此采用少量规则和实际 token 兜底。

### 4. 显式检索与重排流水线

链路为独立问题 → 本地 Embedding → PgVector 512 维余弦／HNSW Top15 → 本地配对重排 → 最多 Top5 → 证据上下文 → 当前 ChatModel。继续复用 Spring AI VectorStore；重排封装为可接 DocumentPostProcessor 的小型处理类，由知识问答服务显式调用，便于查看每个阶段结果。

默认检索全部类型。向量分和重排分分别保存；使用固定正负中文样例校准可配置的最低相关度门槛，不直接照搬 0.5 概率阈值。没有合格片段直接返回资料不足说明；片段存在但不足以回答时，生成提示词要求明确不足。缺少模型、重排或数据库故障则 error，不静默退回未重排答案。

上下文为每个最终片段分配可引用编号并附标题／章节。来源事件只使用服务端实际片段，校验回答引用与该集合的关系，不接受模型编造的路径或 ID。把独立问题、候选 ID 与分数、最终片段 ID 和引用映射附加到已有轮次记录；仅将用户问题和最终助手回答写入会话窗口。

### 5. 同一所选 LLM 完成路由与问题补全

分类模型读取最近完整消息、当前问题、业务日期和四个销售能力说明，返回 `{route, standaloneQuestion, clarificationQuestion}`。合法 route 为 SALES／RAG／CLARIFY；格式错误最多修复一次，再失败则 error，不能默认猜一个路由继续执行。内部分类消息不追加到会话。

| 路由 | 执行边界 |
|---|---|
| SALES | 只注册现有销售和展示工具；使用第一项的本轮数据守卫、独立调用记录与单图规则 |
| RAG | 执行知识流水线；生成客户端无销售工具，资料缺失正常提示，不自动转 SALES |
| CLARIFY | 只发送澄清问题，不查询销售或向量库，完整澄清对话正常进入消息窗口 |

混合请求先问“先了解规则还是查询数据”，用户选择后通过历史恢复原条件，只执行那一部分。上一轮查数据、下一轮问“该指标怎么定义”时允许转 RAG，不用上一轮路由硬锁当前问题。问候或无法确定的表达可在 CLARIFY 下给简短回应并引导明确问题，不扩大查询范围。

ModelRegistry 按请求选择不可变的基础无 Tool ChatClient；只有 SALES 请求装配销售／展示回调。导入 AUTO 分类使用导入请求 modelId，意图与最终生成使用聊天请求 modelId；不引入第三个分类模型，不创建可执行 Skill 或多 Agent。

### 6. 同一输入区承载导入，增量扩展 SSE

前端仅有聊天／导入两个输入状态。导入按钮在输入区左下；点击后保留提问草稿，原输入区显示标题、自动／指定类型、正文和选择 TXT 操作。取消恢复问题；失败保留导入草稿；成功显示实际标题、类型、片段数与重复状态，再恢复问题。导入正文不提交聊天接口，不新增弹窗、侧栏、独立面板或问答模式选择器。

沿用第一项 start／done 的 conversationId 和 turnId，done 增加 route。SALES 为 start → delta* → chart? → done；RAG 为 start → delta* → sources? → done；CLARIFY 为 start → delta* → done。任意链路失败都 error 且无 done。sources 暂存至 done，与图表一样受请求归属校验保护，来源文本安全显示。

原型只作为交互参考，示例中的本地规则分类和示例回答不能复制为正式模型路由／检索实现。新会话只清理会话和记录，不删除 PostgreSQL 资料。保留原模型选择、停止生成、安全 Markdown 与图表展示。

### 7. 配置与部署

Compose 新增带健康检查和独立命名卷的 PgVector，MySQL 与 Nacos 结构不变。AI 服务显式装配唯一本地 EmbeddingModel，防止 OpenAI starter 意外启用远程向量化。运行参数、模型资源位置、切片预算、TopK=15、TopN=5、相关度门槛和知识库开关通过 AI Nacos 配置提供；连接秘密只保留服务端环境变量占位符。

测试配置可关闭知识库自动装配，使现有离线测试继续不依赖模型、Nacos 或 PostgreSQL；本地 ONNX 与真实 PgVector 测试使用显式环境条件启用，分别提供运行说明。销售服务不直接访问知识库，AI 服务也不绕开 Feign 读取销售表。

## Risks / Trade-offs

- [新导出图、量化算子或 tokenizer 与 M1 本地库不兼容] → 第一组任务先做真实 CPU 推理；验证失败先修正 Runtime／装载适配，不跳过重排或擅自换模型。
- [本地重排增加时延和原生内存占用] → Session 复用、有界批处理和工作线程；测量 Top15 实际耗时，不预先承诺响应时间。
- [小模型不能召回所有领域知识] → 固定小样例检查召回与重排，保留来源与友好拒答，不声称仅凭分数保证事实正确。
- [取消导入或 SSE 断开不能保证撤销已经提交的知识文档] → 导入事务成功与否由服务端决定，重试按哈希去重；会话取消禁止迟到结果提交消息。
- [切片或模型变更影响现有向量] → 仅换 reranker 不重建向量；更换 Embedding 即使维度相同也需重建，切片变更需重导受影响资料。
- [与前置变更的 delta 同时修改同一主规范] → 本项以第一项结果为基线，保留其所有相关场景，严格按依赖顺序同步和归档，不并行覆盖主规范。

## Migration Plan

1. 确认前置变更实现验收并同步规范，随后通过本地模型冒烟，记录实际 Runtime／tokenizer 版本与平台。
2. 启动新的 PgVector 数据卷，初始化文档及向量结构；不改动既有 MySQL 销售数据。
3. 分别完成导入、知识流水线、三类路由和输入区交互，最后运行端到端验收与既有销售回归。
4. 回滚时回退 AI 与前端并关闭知识库能力，保留 PostgreSQL 卷；不自动删除用户资料或模型文件。
