## Why

当前图表类型由销售 Tool 名称固定决定，用户即使明确要求“用饼图展示”或“改成柱状图”，系统也无法体现该展示意图。需要把“查询什么业务数据”和“如何展示结果”分开，同时继续保证图表数据只能来自可信 Tool 结果。

## What Changes

- 在同一轮模型调用中分别捕获业务查询结果和展示意图，不增加第二次模型调用。
- 展示意图支持 `AUTO`、`BAR`、`PIE`、`LINE`、`NONE` 以及可选指标；用户明确指定时优先，未指定时沿用当前默认图表。
- 服务端根据展示意图和业务 Tool 返回的字段生成 ChartSpec；模型不得提供图表数据、任意 ECharts option 或可执行代码。
- 展示意图缺失或无效时安全回退到默认图表；`NONE` 明确禁止生成图表。
- 保持现有 `delta`、`chart`、`done`、`error` SSE 协议和 ChartSpec 白名单不变。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `chart-visualization`: 将固定的 Tool 到图表类型映射改为“默认映射 + 用户展示意图覆盖”，并保持数据可信和声明式安全边界。

## Impact

- 影响 AI 服务的系统提示词、模型 Tool 注册、请求级结果记录、ChartCompiler、聊天收口逻辑及相应测试。
- 不修改销售服务、销售 Tool 返回结构、前端 SSE 事件类型或现有依赖。
- 不支持一次回答生成多个图表；仍以当前单次业务 Tool 结果生成至多一个图表。
