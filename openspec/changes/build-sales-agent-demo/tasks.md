## 1. Project and dependency baseline

- [x] 1.1 Create the root Maven project, the peer `agent-demo-sales` and `agent-demo-ai` aggregator modules, and the `agent-demo-sales-client`, `agent-demo-sales-service`, and `agent-demo-ai-service` child modules; do not create a finance module in phase one.
- [x] 1.2 Lock compatible JDK 21, Spring Boot 4.0.x, Spring AI 2.0.x, Spring Cloud 2025.1.x, Spring Cloud Alibaba 2025.1.x, and MyBatis-Plus Boot 4 dependency versions in the parent POM.
- [x] 1.3 Add module-specific dependencies for Web, WebFlux streaming, OpenFeign, Nacos discovery/config, MySQL, Spring JDBC, MyBatis-Plus, DeepSeek, and OpenAI.
- [x] 1.4 Add shared Maven compiler, test, encoding, and reproducible build configuration and verify all empty modules compile.
- [x] 1.5 Add a root `docker-compose.yml` and `.env.example` for MySQL 8.4 LTS and standalone Nacos 3.2.3, with configurable ports, health checks, named volumes, Apple Silicon compatible images, and no model API Keys.
- [x] 1.6 Validate the Compose model and document startup, readiness inspection, normal shutdown, and explicit volume-reset commands.

## 2. Sales database and domain model

- [x] 2.1 Create MySQL DDL for customer, product category, product, inventory, sales order, and sales order item tables with the specified constraints and indexes.
- [x] 2.2 Implement sales entities, status enums, mapper interfaces, and basic persistence services using MyBatis-Plus Boot 4.
- [x] 2.3 Implement order amount calculation and validation so line amounts, total quantity, and total amount are derived from order items.
- [x] 2.4 Implement inventory reads and consistency checks for stock, locked, and available quantities.
- [x] 2.5 Add database tests for relational constraints, historical item snapshots, amount consistency, and inventory invariants.

## 3. Internal sales APIs and AI Feign adapter

- [x] 3.1 Define the versioned sales statistics request/response DTOs, public enums, and path constants in `agent-demo-sales-client` without exposing persistence entities.
- [x] 3.2 Define `SalesStatisticsClient` in `agent-demo-sales-client` as the shared Feign and Spring MVC contract, using the `agent-demo-sales-service` discovery name and versioned internal API paths.
- [x] 3.3 Make `agent-demo-sales-service` depend on its Client module and implement the shared contract in sales controllers for customer purchase ranking, category sales share, popular products, and sales trend, without enabling a local proxy for its own Feign Client.
- [x] 3.4 Make `agent-demo-ai-service` depend on `agent-demo-sales-client` and restrict Feign scanning to external Client packages.
- [x] 3.5 Add contract tests that verify inherited endpoint mappings, request validation, JSON fields, validation errors, and Feign-compatible serialization between the shared interface and controller implementation.

## 4. Sales analytics

- [x] 4.1 Implement the shared effective-order predicate that includes only `PAID` and `COMPLETED` orders.
- [x] 4.2 Implement customer purchase ranking with quantity ranking and order-count and amount companion metrics.
- [x] 4.3 Implement category sales-amount share using order-item category snapshots and deterministic percentage precision.
- [x] 4.4 Implement popular-product ranking by summed sales quantity across orders with deterministic tie ordering.
- [x] 4.5 Implement daily, weekly, and monthly sales trend queries with continuous zero-filled time buckets.
- [x] 4.6 Add deterministic JDBC integration tests for all four analytics and invalid parameter cases.

## 5. Demo data initialization

- [x] 5.1 Implement an opt-in, empty-database-only demo data initializer using a fixed random seed and a current-date-relative 90-day window.
- [x] 5.2 Generate the specified customer, category, product, inventory, order, and order-item volumes with internally consistent amounts and statuses.
- [x] 5.3 Shape the generated data to include high-frequency customers, popular products, dominant categories, weekday differences, and a recent sales peak.
- [x] 5.4 Add tests proving initialization is repeat-safe and generated data produces non-empty, meaningfully distributed results for all four analytics.

## 6. Nacos registration and configuration

- [x] 6.1 Configure both running services for Nacos 3.2.x discovery using environment-specific service names and the `AGENT_DEMO` group.
- [x] 6.2 Configure Nacos imports through `spring.config.import` in `application.yml` and avoid `bootstrap.yml`.
- [x] 6.3 Add example Nacos configuration files containing non-secret database, model metadata, and demo-data settings.
- [x] 6.4 Read DeepSeek and OpenAI API Keys only from environment variables and add startup validation that does not print secret values.
- [x] 6.5 Verify the AI service can discover and invoke the sales service through its Feign service name.

## 7. Dual-model chat routing

- [x] 7.1 Configure independent DeepSeek V4 Flash and OpenAI GPT-5.6 Terra ChatModel instances.
- [x] 7.2 Build independent immutable ChatClient instances and route requests by stable `modelId` without mutating shared defaults.
- [x] 7.3 Implement the model-list endpoint with exactly the two enabled model metadata records and no credentials.
- [x] 7.4 Implement unknown and disabled model validation before any provider request.
- [x] 7.5 Add concurrent routing tests proving DeepSeek and OpenAI selections cannot leak across requests.

## 8. Sales Tool Calling

- [x] 8.1 Implement the four sales analysis Tools with unique names, detailed use and non-use descriptions, typed parameters, and Feign delegation.
- [x] 8.2 Add the current date, `Asia/Shanghai` timezone, relative-date rules, effective-order semantics, and no-fabrication rules to the shared system prompt.
- [x] 8.3 Add application-side Tool name and argument validation before executing Feign calls.
- [x] 8.4 Configure the Spring AI Tool Calling loop so both ChatClients receive the same Tool callbacks and Tool results return to the selected model.
- [x] 8.5 Implement request-scoped Tool result capture keyed by trace and request context.
- [x] 8.6 Add a current-business-data guard that prevents successful numeric answers when no successful non-empty sales Tool result exists.
- [x] 8.7 Add mocked-model tests for correct Tool selection, arguments, Feign delegation, result return, and no-result guard behavior.

## 9. Chart protocol and compilation

- [x] 9.1 Define the versioned ChartSpec DTO with only `bar`, `pie`, and `line` types, bounded dimensions and rows, semantic encoding, safe options, and source Tool metadata.
- [x] 9.2 Compile customer ranking and popular products into horizontal bar ChartSpecs directly from captured Tool results.
- [x] 9.3 Compile category sales share into a pie ChartSpec and sales trend into a line ChartSpec directly from captured Tool results.
- [x] 9.4 Validate protocol version, chart type, dimensions, numeric values, row limits, and source metadata before emitting a chart.
- [x] 9.5 Add tests proving chart numbers remain identical to Tool results and no executable ECharts option or function is serialized.

## 10. SSE chat backend

- [x] 10.1 Implement the POST `/api/chat/stream` request contract with message, model ID, and optional conversation ID.
- [x] 10.2 Convert Spring AI response chunks into typed `delta` SSE events without assuming one chunk equals one token.
- [x] 10.3 Finalize successful streams with an optional captured `chart` followed by exactly one `done` event.
- [x] 10.4 Convert uncaught model, Tool, and internal-call failures into a safe `error` event without sending a success `done`.
- [x] 10.5 Clear request-scoped Tool and chart data on completion, error, and cancellation.
- [x] 10.6 Add tests for event order, split chunks, completion, failure, cancellation, and stream closure without `done`.

## 11. Native chat frontend

- [x] 11.1 Create the single-page chat layout with the two-model selector, message list, prompt input, send control, and stop control.
- [x] 11.2 Implement POST `fetch` streaming and a robust SSE parser that handles CRLF normalization, multiple data lines, and network chunk boundaries.
- [x] 11.3 Accumulate `delta.text` and throttle marked.js rendering with `requestAnimationFrame` or an equivalent 30–50 ms schedule.
- [x] 11.4 Sanitize rendered Markdown with DOMPurify before DOM insertion and run highlight.js once after successful completion.
- [x] 11.5 Implement pending-chart gating so a chart is displayed only after a success `done` event.
- [x] 11.6 Implement the ECharts adapter with ChartSpec version checks and a strict `bar`, `pie`, `line` whitelist.
- [x] 11.7 Ensure the page never displays reasoning content, Tool names, Tool arguments, Feign details, or intermediate Tool results.
- [x] 11.8 Add frontend tests or browser fixtures for Markdown tables, lists, code blocks, unsafe HTML, SSE interruption, model switching, and all three chart types.

## 12. End-to-end validation and documentation

- [x] 12.1 Add local startup documentation for MySQL, Nacos 3.2.x, Nacos config imports, environment variables, service order, and frontend access.
- [x] 12.2 Add example questions and expected Tool and chart mappings for the four required sales analyses.
- [x] 12.3 Run the complete offline Maven test suite and resolve dependency convergence or Boot 4 compatibility failures.
- [ ] 12.4 Run real DeepSeek V4 Flash smoke tests for all four sales Tools, streaming Markdown, and charts.
- [ ] 12.5 Run real OpenAI GPT-5.6 Terra smoke tests for all four sales Tools, streaming Markdown, and charts.
- [ ] 12.6 Compare the two model traces for Tool choice and argument correctness and document any provider-specific options required to preserve the common behavior contract.

## 13. Confirmed local-infrastructure and learning-code refinements

- [x] 13.1 Change all six sales physical tables, MyBatis-Plus mappings, handwritten SQL, initializer SQL, fixtures, and tests to the `t_`-prefixed names.
- [x] 13.2 Remove foreign-key and `CHECK` constraints from the sales DDL, retain primary keys, non-null columns, defaults, unique indexes, and query indexes, and move every affected relational, amount, and inventory invariant into application validation tests.
- [x] 13.3 Keep the current Java-based, fixed-seed, empty-database-only initializer for current-date-relative 90-day demo data and document why no large static historical `data.sql` is maintained.
- [x] 13.4 Add switchable Spring JDBC SQL-template and bound-parameter logging controlled from Nacos using `JdbcTemplate=DEBUG` and `StatementCreatorUtils=TRACE`, default it to off outside local/development use, and do not add P6Spy or query timing.
- [x] 13.5 Add Lombok as an optional dependency to the AI service, use `@RequiredArgsConstructor` and `@Slf4j` only where they reduce Spring Bean and logging boilerplate, retain `@Getter/@Setter` for entities, and do not use `@Data` or replace Java records.
- [x] 13.6 Add learning-oriented comments to the Chat controller/service, per-model gateway and registry, model configuration, Tool definitions, request-scoped result recorder, business-data guard, Chart compilation, and SSE cleanup without commenting trivial statements.
- [x] 13.7 Change local Nacos 3.2.3 from embedded Derby to the same MySQL 8.4 instance and the same non-root username/password used by sales, while isolating data in `nacos_config` and `agent_demo_sales` databases.
- [x] 13.8 Add empty-volume MySQL initialization assets that create both databases, grant the shared user access to both, and import the exact official Nacos Server 3.2.3 MySQL schema only into `nacos_config`; make Nacos wait for MySQL health and configure it with the official Docker MySQL environment variables.
- [x] 13.9 Reduce application-local configuration to Nacos bootstrap information and framework invariants, move ordinary non-secret runtime settings into per-service Nacos data IDs, and keep the shared MySQL password and both model API Keys as environment-supplied placeholders rather than actual Nacos values.
- [x] 13.10 Update local reset/startup documentation, validate Compose from an empty volume, verify Nacos configuration persists in MySQL across normal restarts, rerun the complete Maven package, and ensure no secret value is present in tracked or example files.
