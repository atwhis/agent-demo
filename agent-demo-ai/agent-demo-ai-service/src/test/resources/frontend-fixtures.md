# 前端手工回归夹具

Markdown 增量内容覆盖表格、列表、代码高亮和不安全 HTML：

| 商品 | 数量 |
|---|---:|
| 机械键盘 | 18 |

- 第一项
- 第二项

```java
record Item(String name, int quantity) {}
```

<script>alert('unsafe')</script>

SSE 需要分别验证 LF、CRLF、多行 data、网络分块和收到 `chart` 后连接中断；中断时不得渲染待定图表，也不得视为成功。

模型切换夹具：先选择 `deepseek-v4-flash` 发起请求，完成后切换 `gpt-5.6-terra`，确认下一请求 JSON 的 `modelId` 改变且进行中的选择框被禁用。

允许的图表类型夹具：

```json
{"version":"1.0","type": "bar","rows":[]}
{"version":"1.0","type": "pie","rows":[]}
{"version":"1.0","type": "line","rows":[]}
```
