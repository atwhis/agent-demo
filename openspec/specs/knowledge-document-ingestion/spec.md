# knowledge-document-ingestion Specification

## Purpose

允许用户在已有助手页面中把纯文本资料加入持久化知识库，明确文本类型、章节来源、切片边界、去重和导入失败的行为。通过有限的结构识别和实际 token 预算保留可检索的完整内容，为后续带来源的知识问答提供可靠输入。

## Requirements

### Requirement: Plain text import contract
系统 SHALL 提供 POST /api/knowledge/documents/text 接收标题、正文、文本类型和 modelId，以及 POST /api/knowledge/documents/upload 接收同等元数据和单个 UTF-8 TXT 文件。正文大小 SHALL 限制为 2 MiB，标题 SHALL 为 1 至 80 个字符；空白正文、无效编码、非 TXT 上传或超限输入 MUST 在写入前拒绝。正文不得受聊天问题长度上限限制。

#### Scenario: Paste a knowledge document
- **WHEN** 用户提交有效标题及一段不超过限制的纯文本
- **THEN** 系统完成导入并返回 documentId、实际标题、类型、片段数量和是否重复的信息

#### Scenario: Reject invalid input
- **WHEN** 上传非 TXT、无效 UTF-8、空白或超过 2 MiB 的正文
- **THEN** 系统返回可展示的输入错误，且不留下文档或向量片段

### Requirement: Content purpose classification
文档类型 SHALL 只允许制度规则、产品说明、操作指南和其他。用户指定有效类型时 SHALL 直接采用；选择自动识别时 SHALL 依据标题和有限正文识别整份文档的用途。自动识别无法确定或失败时 SHALL 以其他类型继续导入，并在结果中说明最终类型；未知显式类型 MUST 拒绝。

#### Scenario: Explicit document type
- **WHEN** 用户指定制度规则后导入销售统计口径
- **THEN** 文档和全部片段保存制度规则标签，不被自动分类覆盖

#### Scenario: Automatic classification cannot decide
- **WHEN** 自动分类不能得到有效的指定类型
- **THEN** 导入结果明确显示实际类型为其他，文本仍可被检索

### Requirement: Structure first and token bounded chunks
系统 SHALL 优先按明确标题、章节、条款和段落组织片段，在同一章节内合并短段落，超长内容再按句子和 token 拆分。片段 SHALL 保留标题／章节上下文并纳入长度预算；仅拆分超长内容时保留少量重叠，完整小章节不得机械重复。所有文本 MUST 通过实际模型 tokenizer 的预算检查，不得靠静默截断丢弃入库正文。

#### Scenario: Preserve a short section
- **WHEN** 一节包含标题及若干短段落且未超预算
- **THEN** 该节内容按原顺序组成片段，不混入另一个顶层章节

#### Scenario: Split an oversized clause
- **WHEN** 单条条款超过可用 token 预算
- **THEN** 以句子和 token 继续切分，保留少量上下文重叠，全部正文均被覆盖且每片段未超限

### Requirement: Source metadata and complete import
每个文档 SHALL 有稳定 documentId，每个片段 SHALL 有 chunkId、标题、类型、章节路径和顺序编号。只有整份导入成功后 SHALL 返回成功；导入失败 MUST 不留下本次不完整文档片段。文档及向量 SHALL 在应用重启后保留。

#### Scenario: One document fails during import
- **WHEN** 一份新文本在向量生成或写入过程中失败
- **THEN** 接口返回导入失败，知识库中不存在本次半份文档，已有文档不受影响

### Requirement: Content based deduplication
系统 SHALL 按规范化正文的内容标识去重，重复导入不得重复创建文档或向量，并返回原文档的实际标题、类型与片段数量。重复请求不得悄悄修改已存在文档的元数据；相同文件名但不同正文 SHALL 可作为不同文档导入。

#### Scenario: Import identical content twice
- **WHEN** 已成功导入的正文再次通过粘贴或 TXT 上传
- **THEN** 返回相同 documentId 和重复标记，向量数量不增加且既有元数据保持不变
