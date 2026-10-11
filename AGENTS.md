# AGENTS.md

## 项目概览

研发任务优先级看板（Trello 风格）。Monorepo：`server/`（Spring Boot 3.3 模块化单体）+ `desktop/`（Electron 33 + React 18 + TypeScript + Vite）+ `docs/`。

- **改业务逻辑前先读设计文档**：[docs/superpowers/specs/2026-10-11-priority-flow-design.md](docs/superpowers/specs/2026-10-11-priority-flow-design.md)
- 详细功能清单、核心机制、实现决策偏差见 [README.md](README.md)

## 常用命令

### server/（Spring Boot，端口 8080）

```bash
export JAVA_HOME="E:/Java/jdk-21.0.10"          # 必须 JDK 21，默认 JAVA_HOME 是 JDK 8，不设会编译失败
cd server
E:/apache-maven-3.8.8/bin/mvn spring-boot:run   # 启动（首次自动建库 + Flyway V1__init.sql + 种子账号 admin/admin123）
E:/apache-maven-3.8.8/bin/mvn test              # 70 个测试（单测 + 全链路冒烟 + 全功能）
```

### desktop/（Electron + React）

```bash
cd desktop
npm install          # npm 拦截安装脚本时：npm install-scripts approve electron esbuild && npm rebuild electron esbuild
npm run dev          # Vite 5173 代理 8080 + Electron 窗口；npm run dev:web 为纯浏览器调试模式
npm run build        # tsc --noEmit + 渲染进程 + 主进程编译（TypeScript 类型检查在这里）
npm test             # vitest（前端测试，如 dragBucket.test.ts）
```

## 环境与已知坑

- MySQL：`priority_flow` 库，root/renyangming@localhost:3306；连接、MinIO、JWT 密钥等配置在 `server/src/main/resources/application.yml`（生产须环境变量覆盖）。
- **MinIO 桶名是 `priority-flow`**：配置文件里的 `priority_flow` 含下划线不符合 S3 规范（启动校验会拒绝），实现已改用连字符，**不要"修正"回下划线**。
- 本目录当前**不是 git 仓库**（无 `.git`），提交规范见 `.rules/commit.md`，仅在仓库初始化且用户明确要求时才提交。
- Swagger UI：应用启动后 http://localhost:8080/swagger-ui.html

## 服务端业务域

`com.sdecloud.priorityflow` 下 7 个域：`auth`（认证/JWT/用户）、`project`（项目/成员/角色权限）、`task`（任务/状态机/排序/队列）、`collab`（评论/附件）、`notification`（站内通知/WS 推送/逾期扫描）、`system`（管理员）、`common`（R/异常/BaseEntity/权限切面/LexoRankUtil/WS 配置）。

核心机制（改动需谨慎，详见 README「核心机制」）：

- **状态机**：`TaskStateMachine` + 8 个独立 `TaskActionHandler`；非法流转返回 3xxx 业务码；每次流转统一写操作日志并发布 `TaskChangedEvent`（AFTER_COMMIT 驱动 WS 推送与站内通知）。
- **排序**：优先级分组 + `LexoRankUtil`（base-26 字符串排序键）；显示顺序 = 优先级降序 + 同优先级桶内 sort_rank 升序；拖拽仅限同优先级桶内（跨级拒绝，业务码 3004，服务端 `TaskQueueOrder` 兜底 + 前端 `dragBucket` 镜像校验）；服务端只收 beforeId/afterId 计算 rank，均空为本桶尾。
- **权限**：`@RequireProjectPermission` 切面校验；成员多角色权限并集；创建者特权只认 `creator_id`。

## DDD 三层架构（业务模块规范）

```
业务域/
  controller/     → *Controller.java      # REST 接口
  service/    → *Service.java (接口) + impl/*ServiceImpl.java   # 应用服务（编排、事务）
  bean/           → dto/ (入参) + vo/ (出参)                                # 数据传输对象
```

### persistence/ 持久化层结构

```
persistence/[domain]/
  po/                              → XxxDo.java                         @TableName + @Data 实体类
  repository/                      → XxxRepository.java                 extends IService<XxxDo>
    impl/                          → XxxRepositoryImpl.java             extends ServiceImpl<Mapper, XxxDo>
  mapper/                          → XxxMapper.java                     extends EfpBaseMapper<XxxDo>
  es/                              → po/, config/, repository/          Elasticsearch 索引（可选）
```

| 层级 | 目录 | 接口 | 实现 | 说明 |
|------|------|------|------|------|
| **Entity** | `po/` | — | `XxxDo.java` | 以 `Do` 结尾，字段名与数据库列对应 |
| **Mapper** | `repository/mapper/` | `XxxMapper.java` | — | 继承 `EfpBaseMapper<T>` 或 `BaseMapper<T>` |
| **Repository** | `repository/` | `XxxRepository.java` | `impl/XxxRepositoryImpl.java` | 继承 `IService<T>`，声明自定义查询方法 |
| **ES** | `es/repository/` | `XxxEsRepository.java` | — | Elasticsearch 专用持久化 |

## 桌面端结构（desktop/src）

| 目录 | 内容 |
|------|------|
| `api/` | axios 请求封装与接口定义 |
| `components/` | `ui/` 自绘 shadcn 风格组件 + 业务组件（TaskCard/TaskDrawer 等） |
| `features/` | 按功能域组织：auth / board / drafts / my-task / notification / settings / workload |
| `stores/` | Zustand 状态；`ws/` STOMP 实时对账（按 operatorId 去重回显） |
| `electron/` | 主进程 `main.ts` + `preload.ts`（token 经 safeStorage 加密存储） |

## 专项规则（触发式加载）

执行以下操作时，**必须先读取对应规则文件，再动手**：

| 触发场景 | 规则文件 | 核心约束 |
|----------|----------|----------|
| 创建 VO / DTO / Entity / Controller、写建表 SQL | [.rules/coding-patterns.md](.rules/coding-patterns.md) | VO 继承 PO、主表 Entity 继承 BaseEntity、列顺序、子表不含审计字段 |
| 执行 `git commit` | [.rules/commit.md](.rules/commit.md) | 关键词格式 `feat:` / `fix:` / `refactor:` |
