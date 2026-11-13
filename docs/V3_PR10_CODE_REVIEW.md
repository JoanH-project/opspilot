# OpsPilot V3 — PR #10 代码审阅报告

审阅日期：2026-11-13
仓库：[`JoanH-project/opspilot`](https://github.com/JoanH-project/opspilot)
PR：[#10 — Feat/v3 knowledge rag](https://github.com/JoanH-project/opspilot/pull/10)
原始待审阅 head：`dfc324fe8e1b376654f42d22280c3027f76a2b0b`
修复后审阅代码 SHA：`5ec5a08596255d6073597c76eaf4e8fc4866f7d3`
PR 状态：OPEN，未批准、未合并。

## 结论

发现一项 HIGH 级 Worker 状态一致性问题，已在本 PR 内进行最小修复，并通过新增单元和 MySQL Testcontainers 回归覆盖。修复后代码 SHA 的 Backend、AI Service、Frontend CI 均通过。

**建议 Owner 在确认下述非阻塞运行与质量限制后合并。** 本报告是 Codex 自检，不能代替独立人工审阅或 Owner 决定；本次没有提交 GitHub Approve，也没有合并 PR。

## 审阅范围

以 `origin/main`（`f1e38a0b339f763368a63d2c6a5a297f0f8f11ee`）为基线，检查 PR 原始 head `dfc324f` 上的变更；随后检查本报告列出的 Worker 修复及回归测试，最终按 `5ec5a08` 验证代码状态。原始 PR 有 81 个变更文件；Worker 修复增加 V9 迁移和测试后共 82 个变更文件。

- **Backend / 安全与 API：** `SecurityConfig`、JWT 请求过滤器、Workspace 访问与角色检查、错误处理、Knowledge Controller、请求/响应 DTO、AI HTTP Client、`WorkspaceKnowledgeService`、重建限流器。
- **Backend / 数据与 Worker：** Document revision 事务、Document Index State/Repository/Service/Worker、V8 迁移及新增 V9、Flyway 与 Hibernate 配置。
- **AI Service：** FastAPI 路由与请求模型、内部 Token 校验、KnowledgeService、E5 embedding、Markdown chunker、Qdrant vector store，以及对应 API/service/chunking/vector-store 测试。
- **Frontend：** Knowledge route、API client 与类型、React Query workspace/user key、登出处理、AppShell 集成及 Knowledge 页面测试。
- **交付与验证：** Compose、环境示例、Dockerfile、Backend/AI/Frontend GitHub Actions、V3 API/架构/验收/评估文档。

## 发现

### HIGH — Worker 过期租约可能永久卡住或被旧执行覆盖（已修复）

**原始位置：** `backend/src/main/java/com/opspilot/knowledge/DocumentIndexStateRepository.java:29-31`；`DocumentIndexJobService.java:62-85`（均指原始 `dfc324f`）。

**复现与影响：** `MAX_ATTEMPTS` 为 8。第 8 次 claim 后若进程在写入最终失败状态前崩溃，数据库留下 `INDEXING / attempts=8 / lease_until < now`。原 claim 查询要求 `attempts < maxAttempts`，因此之后永远不会再选中该行；状态永久显示 `INDEXING`。另一个并发窗口是 AI 请求耗时可超过 90 秒租约：第二个 Worker 可重新领取同一行，原执行之后仍能按 document revision 相同的条件写入 `READY` 或 `FAILED`，覆盖较新的执行结果。仅比较 revision 或 attempts 无法覆盖手动 reindex 将 attempts 归零的情况。

**修复：** 在 `V9__fence_index_worker_claims.sql` 添加可空 `claim_token`；每次 claim 生成唯一 UUID 并传入内部 `IndexJob`。过期 claim 和重新排队通过悲观锁读状态，完成/失败更新必须匹配当前 `INDEXING` 状态及 claim token。过期的第 8 次 claim 现在会被 Worker 选中并收敛为 `FAILED`，清空 lease/claim token，不再发起第 9 次 AI 调用。V8 和 V1–V7 均未修改。

**回归覆盖：**

- `DocumentIndexJobServiceTest.staleWorkerCannotOverwriteAReclaimedAttempt`：旧 token 不能改变新 claim 的状态。
- `DocumentIndexJobServiceTest.expiredEighthLeaseBecomesTerminalWithoutClaimingAttemptNine`：服务状态转换终止于 attempts=8。
- `V3KnowledgeHttpBoundaryIT.realWorkerMakesExpiredEighthClaimTerminalWithoutCallingAiAgain`：真实 MySQL 查询和调度 Worker 处理模拟崩溃后的过期状态，验证最终 `FAILED`、attempts=8、`next_attempt_at=NULL`、`lease_until=NULL`、没有新增 AI 请求。

**状态：** 已修复并由修复后 SHA 的 CI 验证。

### MEDIUM — Qdrant revision 检查不是跨 AI Service 实例的原子操作（非阻塞，当前部署为单 worker）

**位置：** `ai-service/app/service.py:52,55-60`；`ai-service/app/vector_store.py:65-103`。

**推导与影响：** revision 检查、删除旧点和 upsert 新点是三个独立 Qdrant 操作；同步保护使用进程内 `asyncio.Lock`。若未来同时运行多个 AI Service 进程/副本，旧 revision 与新 revision 请求可能交错，旧请求在新请求写入后删除并覆盖其点。当前 `docker-compose.v3.yml` 只启动一个 AI Service，Dockerfile 的 Uvicorn 配置为 `--workers 1`，因此当前受支持的本地部署路径由单进程锁串行化；此风险在横向扩容时才出现。

**最小建议：** 在增加 AI Service 副本前，为每个 document revision 实现跨进程原子 fencing/CAS 或共享锁，并加入并发集成测试。当前阶段不需要为未启用的扩容提前增加基础设施。

### LOW — RAG excerpt 的数据来源只校验元数据，没有与 MySQL 正文逐段比对

**位置：** `backend/src/main/java/com/opspilot/knowledge/WorkspaceKnowledgeService.java:118-150`。

**推导与影响：** Backend 会验证 hit 的 workspace、当前 ACTIVE document 和 revision，并从 MySQL 取 document 标题；但 excerpt 文本及 chunk/source offsets 来自 AI/Qdrant 响应，没有与该 document 的原文内容或持久化 chunk hash 比对。正常链路由私有 AI Service 写入派生向量数据，且 Qdrant 没有主机端口映射，所以这不构成当前 API 的跨 Workspace 读取路径；若内部向量 payload 被篡改或损坏，仍可能显示与有效文档身份关联的错误引用/回答。

**最小建议：** 若后续需要更强的引用来源保证，为 chunk 建立可校验的原文 hash/版本映射，或在服务端验证引用片段；当前 V3 将私有 AI/Qdrant 派生数据视为受信任的内部依赖。

### LOW — Knowledge 页面不持续刷新索引状态，冷却反馈也未显示剩余时间

**位置：** `frontend/src/features/knowledge/KnowledgeRoute.tsx:49-53,91-101`；`backend/src/main/java/com/opspilot/knowledge/WorkspaceKnowledgeService.java` 的固定 300 秒限流响应；`GlobalExceptionHandler.java:102-107`。

**复现与影响：** 状态查询配置了 15 秒 `staleTime`，但没有轮询；重建请求成功后只立即 invalidate 一次，因此长任务运行期间页面可能保留旧的 queued/processing 数字，直到用户再次触发查询或重新进入页面。429 返回 `Retry-After: 300`，Frontend 显示服务端错误消息但不读取该 header；若用户在冷却即将结束时重试，仍会被告知等待完整 300 秒。

**最小建议：** 后续可加有界状态轮询，并根据 `Retry-After` 展示等待时间；这不影响当前后端权限、任务持久化或 V1 API 契约。

### LOW — 生成式回答是否被引用内容蕴含，仍依赖模型遵循提示

**位置：** `ai-service/app/service.py:120-197`；`backend/src/main/java/com/opspilot/knowledge/WorkspaceKnowledgeService.java:66-99`。

**推导与影响：** AI Service 限制引用 ID 必须来自给定 evidence，Backend 再验证引用 chunk ID；但两层都不能证明自然语言回答中的每个事实确实由引用文本支持。无检索证据时的拒答路径和小规模注入/负向查询测试已覆盖，但有效 citation 不等于语义蕴含证明。

**最小建议：** 面向真实用户扩大领域评估并保留人工核验预期；不要把当前小型合成评估解释为生产答案准确率保证。该限制不阻塞本 PR 的代码合并。

## 重点检查结果

| 领域 | 审阅结论 |
|---|---|
| JWT 与 Workspace RBAC | Knowledge 路由要求已认证用户；搜索/问答通过数据库 Workspace membership 检查，问答在生成后再次检查成员身份；reindex 限 OWNER/ADMIN；AI/Qdrant 均不发布主机端口。未发现跨租户绕过路径。 |
| Document revision 与引用隔离 | Backend 以数据库 ACTIVE 状态、Workspace 和 revision 过滤命中；答复生成前后均有当前文档校验。Worker 旧执行覆盖状态问题已由 claim fencing 修复。 |
| Worker claim/retry | 悲观锁 claim、90 秒 lease、5/10/20/40/80/160/300 秒退避和最多 8 次执行符合实现；发现并修复最终 lease 崩溃恢复与过期执行 fencing。 |
| RAG/chunking | E5 query/passage 前缀、token bounded chunks、相似度阈值、Qdrant tombstone/revision marker、空证据拒答均有实现/测试。source offsets 因清洗/合并/overlap 是近似值，目前 UI 只链接文档、不依靠 offset 定位，因此仅作为低风险准确性限制。 |
| AI Service 安全 | 内部 Token 使用 `hmac.compare_digest`；模型输入有长度限制；提示把用户问题和 evidence 当作不可信文本；异常返回通用错误。Qdrant 仅暴露于 Compose 网络。未发现未经授权的公开 Qdrant 访问路径。 |
| Frontend | Query key 包含 user/workspace，workspace 离开时取消并清除知识查询；请求使用 AbortSignal，401 会登出。未发现缓存跨 workspace 展示问题；状态轮询和 Retry-After 呈现列为 LOW。 |
| 数据库/部署 | V8 的 outbox/限流/存量 ACTIVE 文档初始化未改；新增 V9 是 nullable additive 列。Compose 使用独立命名的 V3 服务/卷，MySQL 端口绑定 localhost。 |
| CI/测试质量 | 新增的最终租约集成测试第一次运行失败，原因是测试夹具把新 Document 的实际 revision=1 错设为 0，Worker 正确将其重排队并处理为 READY。夹具现读取实际 revision；修正后的最终 SHA CI 全绿。测试 Clock 只加速已有退避时间，不改变生产重试上限/时间参数；HTTP 边界仍通过真实监听端口、真实后端、MySQL/Redis 和调度 Worker，AI HTTP stub 仅替代 AI 依赖。 |

## 验证与 CI

### 本地

- `cd backend && mvn -q -Dtest=DocumentIndexJobServiceTest test`：通过。
- `cd backend && mvn -q test`：57 tests，0 failures、0 errors、0 skipped；同时编译测试源，包括 Failsafe 集成类。
- `git diff --check`：通过。
- 本轮没有在本机重跑 Docker 集成测试；此前已记录的本机 Docker Desktop daemon 问题仍独立存在。它不影响下列 GitHub Actions 隔离 runner 结果。

### GitHub Actions（修复后审阅代码 SHA `5ec5a08596255d6073597c76eaf4e8fc4866f7d3`）

- [Backend CI run 43383580031](https://github.com/JoanH-project/opspilot/actions/runs/43383580031)：SUCCESS。Ubuntu 24.04 / Java 21；`mvn clean verify` 使用 MySQL 8.4 和 Redis Testcontainers；Flyway 成功验证并应用 9 个迁移；`V3KnowledgeHttpBoundaryIT` 为 4 tests、0 failures/errors/skipped；后端镜像构建及镜像 Java 21 检查通过。
- [V3 HTTP boundary JUnit artifact](https://github.com/JoanH-project/opspilot/actions/runs/43383580031/artifacts/14017318590)：非敏感 JUnit XML，保留至 2026-11-20。
- [AI Service CI run 43383580032](https://github.com/JoanH-project/opspilot/actions/runs/43383580032)：SUCCESS。
- [Frontend CI run 43383580035](https://github.com/JoanH-project/opspilot/actions/runs/43383580035)：SUCCESS。

此前的 V3 HTTP 边界验收仍有三个真实 HTTP 测试：MEMBER/非成员授权、501 文档的 413 与无限流副作用、HTTP reindex 后实际 Worker 八次失败并最终 FAILED/无第九次请求及故障恢复。新增的第 4 项是 MySQL 与调度 Worker 的最终租约崩溃恢复测试，不冒充 HTTP 测试。

## 非阻塞限制与后续事项

- 小型合成检索集的 Recall@5、Hit@5、MRR@5 和负向查询结果是回归评估，不是生产质量保证；评估语料/查询规模有限。
- 只有一个 AI Service 进程时，Qdrant 的本地锁足够串行化；横向扩容前需解决跨进程 revision 原子性。
- 本机 Docker Desktop 仍有问题；云端 CI 使用隔离 Testcontainers，不依赖本机或现有 `opspilot-mysql` 数据卷。
- 需要 Owner 独立代码审阅及最终合并决定；本报告不构成独立审批。

## 合并建议

**建议合并：是，待 Owner 完成人工审阅。** 已发现的 HIGH 问题已修复，修复后 SHA 的三条 CI 全部 SUCCESS；无未解决的 BLOCKER/HIGH。MEDIUM/LOW 项均为当前单实例部署或引用质量的非阻塞限制。PR #10 应保持 OPEN，直到 Owner 独立决定；本轮未合并，也未启动 V4。
