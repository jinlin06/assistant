# Assistant —— 私人学习助手（Spring Boot 3.3.5 + Spring AI + 智谱 GLM）

一个可插拔大模型的「私人学习助手」后端：统一接入大模型（当前为智谱 GLM），
提供多用户持久化对话记忆、私有知识库 RAG 检索增强、Agent 工具调用（学习任务 / 日程），
以及供前端看板使用的学情分析接口。

> 业务代码只面向 Spring AI 的 `ChatClient` 抽象编程，切换通义 / DeepSeek 等只需替换一个 Starter 依赖并改配置。

---

## 1. 功能模块

| 模块 | 说明 | 关键类 |
| --- | --- | --- |
| 对话入口 | 同步 / 流式（SSE）对话，按 `userId` 隔离上下文 | `ChatController` |
| 持久化记忆 | PostgreSQL 存储对话上下文，重启不丢、多实例共享，滑动窗口 20 条 | `PostgresChatMemoryRepository`、`ChatMessageEntity` |
| RAG 私有知识库 | 上传 PDF/Word/TXT → Tika 解析 → 切分 → 向量化 → 检索增强 | `DocumentService`、`RagConfig` |
| Agent 工具 | 自然语言建任务 / 查日程 / 标记完成，`userId` 经 `ToolContext` 透传 | `StudyTools`、`TaskController` |
| 学情看板 | 大模型结构化提取学情，供雷达图 / 概览 / 趋势图 | `LearningAnalyticsService`、`DashboardController` |

---

## 2. 技术栈

- Java 17，Spring Boot（父 POM 见 `pom.xml`），Spring AI 1.0.0
- 智谱 GLM：`spring-ai-starter-model-zhipuai`（对话 + Embedding）
- PostgreSQL + Spring Data JPA（Hibernate `ddl-auto=update`，首次启动自动建库建表）
- 文档解析：Apache Tika / PDF Reader
- 向量库：开发环境 `SimpleVectorStore`（内存 + 本地 JSON 持久化）

---

## 3. 环境变量（必须先配置）

| 变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `ZHIPUAI_API_KEY` | 是 | 无（留空） | 智谱开放平台 API Key。**切勿硬编码进仓库**，一律用环境变量注入 |
| `PG_HOST` | 否 | `localhost` | PostgreSQL 主机 |
| `PG_PORT` | 否 | `5432` | PostgreSQL 端口 |
| `PG_DB` | 否 | `assistant` | 业务库名（不存在时启动会自动创建） |
| `PG_USERNAME` | 否 | `postgres` | 数据库用户名 |
| `PG_PASSWORD` | 否 | `postgres` | 数据库密码 |
| `APP_TIME_ZONE` | 否 | `Asia/Shanghai` | 业务时区，统一用于落库时间戳与按天聚合（见 `AppTime`） |

PowerShell 示例：

```powershell
$env:ZHIPUAI_API_KEY = "你的智谱Key"
$env:PG_PASSWORD     = "你的数据库密码"
```

---

## 4. 启动步骤

```powershell
# 1) 确保本机 PostgreSQL 已运行（默认端口 5432）
# 2) 配置环境变量（见上）
# 3) 启动
.\mvnw.cmd spring-boot:run
```

服务默认监听 `http://localhost:8080`。首次启动会自动连接维护库 `postgres`，
若业务库 `assistant` 不存在则创建，再由 Hibernate 建出各模块数据表。

---

## 5. 接口清单

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/chat` | `userId`, `message` | 同步对话 |
| GET | `/api/chat/stream` | `userId`, `message` | 流式对话（SSE） |
| DELETE | `/api/chat/memory` | `userId` | 清空该用户对话记忆 |
| GET | `/api/model-info` | — | 健康检查 + 当前模型信息 |
| GET | `/api/tasks/{userId}` | 可选 `date`(yyyy-MM-dd) | 任务列表 |
| POST | `/api/tasks` | `userId`, `content`, `planDate` | 新建任务 |
| PUT | `/api/tasks/{id}/complete` | — | 标记任务完成 |
| DELETE | `/api/tasks/{id}` | — | 删除任务 |
| POST | `/api/documents` | multipart `file` | 上传文档入知识库 |
| DELETE | `/api/documents` | `source` | 按文档名删除向量片段 |
| GET | `/api/dashboard/radar/{userId}` | — | 学习雷达图数据 |
| GET | `/api/dashboard/trend/{userId}` | 可选 `days`(默认14) | 该用户近 N 天提问趋势 |
| GET | `/api/dashboard/overview/{userId}` | — | 私人定制概览卡片 |
| GET | `/api/dashboard/records/{userId}` | — | 最近 20 条学习明细 |

参数校验：`userId`/`message`/`content`/`source` 等已加 `@Valid` + Bean Validation，
非法请求返回 400 及结构化错误体（见全局异常处理）。

---

## 6. 安全说明（重要，请务必阅读）

### 6.1 API Key 管理
- 代码库已改为 `spring.ai.zhipuai.api-key=${ZHIPUAI_API_KEY:}`，不再硬编码。
- **历史遗留泄露处置**：旧 Key 曾随提交进入 Git 历史，必须：
  1. 立即到智谱开放平台**吊销并重建**该 Key（改代码不能撤销已泄露的旧值）；
  2. 用 `git filter-repo` / BFG 清除历史中的旧 Key（本仓库已提供操作步骤，属破坏性历史重写，需本人确认后执行并强制推送）。

### 6.2 身份认证与越权（当前为已知风险，尚未实现）
> ⚠️ 当前所有接口的 `userId` 由客户端直接传参，服务端**不做身份认证、不校验归属**，
> 任何知道他人 `userId` 的人都能读取/篡改/删除其任务、清空其记忆、查看其学情。
> **仅可用于本地演示，严禁直接暴露到公网。**

生产环境上线前的整改方案（本次按需求暂缓实现）：
1. 引入 `spring-boot-starter-security`，采用 HTTP Session 或 JWT 完成登录鉴权；
2. 新增注册 / 登录接口，用户凭证（session / token）中携带可信的 `userId`；
3. **服务端从凭证解析 `userId`，不再信任请求参数**；所有按 `userId` / 任务 `id` 的操作，
   先校验目标资源属于当前登录用户（所有权校验），越权返回 403；
4. `/api/chat`、`/api/dashboard/**`、`/api/tasks/**` 等均纳入鉴权过滤链。

---

## 7. 架构扩展方向

### 7.1 向量库
当前 `SimpleVectorStore` 为内存实现 + 本地 JSON 持久化，适合开发/单机演示；
文档量大或需多实例共享时不够用。由于业务只依赖 `VectorStore` 抽象，
切换到 Redis / Milvus / Qdrant 只差「引入对应 Starter + 改配置」，`RagConfig` 无需改动业务代码。

### 7.2 前端
本仓库**仅包含后端**。注释中提及的 Vue 3 + ECharts 看板（对话面板、资料上传、
学习雷达图 / 趋势图 / 概览卡片）为独立前端工程，不在本仓库内。
前端通过上表 `/api/**` 接口取数；本地联调时 CORS 已放行 `http://localhost:*` / `http://127.0.0.1:*`。
若需跑通全链路，请另行获取/搭建配套前端并在其 `.env` 中配置后端地址。
