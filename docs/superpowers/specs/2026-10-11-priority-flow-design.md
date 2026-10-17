# 高颜值 Trello 风格研发任务优先级看板 · 详细设计

> 依据：`PRD/权限矩阵/状态机/页面结构/交互流程`（PRD 文档）+ 2026-10 设计评审确认记录
> 状态：已评审确认，待实施

---

## 目录

1. [范围与既定决策](#一范围与既定决策)
2. [总体架构](#二总体架构)
3. [数据模型](#三数据模型)
4. [权限模型](#四权限模型)
5. [排序机制（优先级分组 + LexoRank）](#五排序机制优先级分组--lexorank)
6. [API 设计](#六api-设计)
7. [状态机实现](#七状态机实现)
8. [实时同步](#八实时同步)
9. [通知与逾期](#九通知与逾期)
10. [认证与安全](#十认证与安全)
11. [桌面端设计](#十一桌面端设计)
12. [测试策略](#十二测试策略)
13. [一期范围边界](#十三一期范围边界)
14. [项目规划（里程碑）（增补）](#十四项目规划里程碑增补)

---

## 一、范围与既定决策

本设计覆盖 PRD 一期核心功能：登录认证、项目列表、项目看板、任务创建（草稿/发布）、草稿管理、待分发撤回、任务分发、执行人等待队列、开始/切换/完成、关闭/重新打开、优先级设置与修改、拖拽排序、项目经理视图、人员当前任务可见性、跨项目脱敏、站内+桌面通知、评论/附件/工时字段。

设计评审已确认的关键决策：

| 决策点 | 结论 |
| --- | --- |
| 项目内角色 | **完全自定义角色**：分发者可创建自定义角色并逐权限点配置 |
| 账号体系 | 自建账号密码，系统管理员创建用户并初始化密码 |
| 技术栈版本 | JDK 21 + Spring Boot 3.x（开启虚拟线程） |
| 部署形态 | **单实例**，<100 人；不引入 Redis/MQ |
| 子任务 | 一期**不做**（卡片不显示子任务进度） |
| 项目创建 | 系统管理员创建项目并指定第一任项目分发者 |
| 分发者人数 | 一个项目可绑定**多个**分发者（内置最高权限角色，多成员可绑） |
| 仓库结构 | Monorepo：`server/` + `desktop/` + `docs/` |
| 排序键算法 | **LexoRank**（字符串排序键，中值插入，按作用域重整） |
| ORM | MyBatis-Plus |
| 拖拽库 | dnd-kit |
| UI 方案 | TailwindCSS + shadcn/ui 风格自绘组件 |
| 数据库 | MySQL 8.0（utf8mb4）；项目规则文件中瀚高 SQL 管理路径不适用本项目 |
| 基础类 | 自建（BaseEntity、R、BaseMapper、权限注解），不依赖 EFP 脚手架；但项目 AGENTS.md 的 DDD 三层、BaseEntity 模式、建表规范、VO 继承 PO 等模式照常遵守 |

二期范围（本期不实现）：转派拖拽、甘特图、日历视图、工时统计、多端同步、邮件/飞书/钉钉/企业微信通知。

---

## 二、总体架构

```
┌────────────────────────────────────────────────────┐
│  Electron 桌面客户端（Windows/macOS）                  │
│  React 18 + TypeScript + Vite + React Router        │
│  TailwindCSS + shadcn/ui 自绘组件 + dnd-kit 拖拽      │
│  Zustand 状态管理 / TanStack Query 服务端状态          │
│  REST(axios) + STOMP over WebSocket                  │
│  Electron 主进程：系统通知、托盘、主题跟随系统            │
└───────────────┬────────────────────────────────────┘
                │ HTTP / WS
┌───────────────▼────────────────────────────────────┐
│  Spring Boot 3.x 模块化单体（JDK 21 + 虚拟线程）        │
│  MySQL 8.0（utf8mb4）+ MinIO（附件预签名直传）          │
└────────────────────────────────────────────────────┘
```

### 2.1 服务端模块划分

模块化单体，按业务域分包，每个业务域内遵循项目 AGENTS.md 的 DDD 三层结构：

```
com.sdecloud.priorityflow
├─ auth/          认证、JWT、用户
├─ project/       项目、成员、角色权限
├─ task/          任务、状态机、排序、队列
├─ collab/        评论、附件
├─ notification/  站内通知 + WS 推送
├─ system/        系统管理（用户/项目/配置）
└─ common/        R 统一响应、全局异常、BaseEntity、权限切面、LexoRankUtil、WS 配置

业务域/
  controller/     → TaskController                        REST 接口
  service/        → TaskService + impl/TaskServiceImpl    编排、事务、业务规则
  bean/           → dto/（入参）+ vo/（出参，VO 继承 PO 模式）
  persistence/    → po/（TaskInfoDo，@TableName + @Data，继承 BaseEntity）
                    repository/（TaskRepository extends IService<TaskInfoDo> + impl/TaskRepositoryImpl）
                    mapper/（TaskMapper extends BaseMapper<TaskInfoDo>）
```

### 2.2 框架约定

- **BaseMapper**：自建 `com.sdecloud.priorityflow.common.persistence.BaseMapper<T>`，对 MyBatis-Plus `BaseMapper` 薄封装，预留扩展点，不依赖 EFP。
- **统一响应**：自建 `R<T>`；错误码分段：1xxx 认证 / 2xxx 权限 / 3xxx 任务规则 / 4xxx 参数。
- **接口文档**：springdoc（Spring Boot 3 不支持 springfox），注解用 `@Tag/@Operation/@Schema`，模式与项目规则文件示例一致。
- **权限校验**：自建 `@RequireProjectPermission` 注解 + 切面（见第四节），风格对齐 `hasPermi`。
- **建表 SQL**：Flyway 管理（`V1__init.sql` 起步），MySQL 8.0 语法。
- **并发**：`spring.threads.virtual.enabled=true`，应对 WebSocket 长连接与并发写。
- **副作用解耦**：所有任务写操作由 service 层发进程内领域事件（Spring `ApplicationEventPublisher`），驱动 WS 推送、通知、操作日志三个监听器，避免 service 相互调用。

---

## 三、数据模型

### 3.1 公共字段（主表继承 BaseEntity，列顺序按项目规范）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(128) | UUID 主键（`IdType.ASSIGN_UUID`），第一列 |
| create_by | varchar(128) | 创建者（自动填充） |
| create_time | timestamp(6) | 创建时间（自动填充） |
| update_by | varchar(128) | 更新者（自动填充） |
| update_time | timestamp(6) | 更新时间（自动填充） |
| remark | text | 备注 |
| del_flag | varchar(2) DEFAULT '0' | 删除标记（`@TableLogic`），最后一列 |

关联表（project_member、project_member_role）按规范"子表可不继承"，精简为业务字段 + create_by/create_time + del_flag。

**枚举一律用 varchar 存字符串常量（禁用 tinyint），Java 侧定义枚举类，MyBatis-Plus `@EnumValue` 映射。**

### 3.2 表清单

| 表 | 业务字段 | 说明 |
| --- | --- | --- |
| `sys_user` | username(唯一), password_hash(BCrypt), display_name, email, avatar_url, status(0启用/1禁用), is_admin(0否/1是) | 自建账号；is_admin 标记系统管理员 |
| `project_info` | project_name, project_key(任务编号前缀如 PF，唯一), status(0正常/1归档) | 管理员创建，创建时指定第一任分发者 |
| `project_member` | project_id, user_id | 唯一约束(project_id, user_id) |
| `project_role` | project_id, role_name, role_code, permissions(text, JSON 权限点数组), is_builtin | 内置 4 角色，见第四节 |
| `project_member_role` | project_id, user_id, role_id | 成员可绑多角色，权限取并集 |
| `task_info` | 见 3.3 展开 | 核心表 |
| `task_operation_log` | task_id, project_id, operator_id, action, from_status, to_status, reason, detail(text/JSON), ip, device | 一张表同时满足「状态历史」与「操作日志」，覆盖 PRD 要求的 7 项记录（任务ID/原状态/新状态/操作人/操作时间/原因备注/来源IP设备） |
| `task_comment` | task_id, author_id, content, mentions(text/JSON，@ 解析的 user_id 数组) | |
| `task_attachment` | task_id, file_name, object_key, file_size, content_type, uploader_id | MinIO object_key + 预签名 URL |
| `notification_info` | user_id, notify_type, title, content, ref_type, ref_id, is_read, read_time | 桌面通知由客户端收 WS 推送后调 Electron API |

### 3.3 task_info 展开（一期，不含子任务）

```
id varchar(128) PK
project_id varchar(128)                    所属项目，不能为空
seq_no int                                 项目内递增序号，任务编号 = {project_key}-{seq_no}
title varchar(255)                         标题
description text                           富文本，存储前 XSS 清洗
priority varchar(16)                       URGENT / HIGH / MEDIUM / LOW（PriorityEnum）
task_type varchar(16)                      REQUIREMENT / DEVELOP / TEST / BUG /
                                           DESIGN / OPS / OTHER（TaskTypeEnum）
task_status varchar(32)                    DRAFT / PENDING_DIST / WAITING /
                                           IN_PROGRESS / COMPLETED / CLOSED（TaskStatusEnum）
creator_id varchar(128)                    创建人=草稿归属人（业务字段，草稿归属永不变更；
                                           与审计字段 create_by 语义分离）
assignee_id varchar(128)                   执行人，分发后必填
distributor_id varchar(128)                分发操作人
labels text                                JSON 数组，自由标签（一期不做标签管理页）
estimated_hours decimal(6,1)               预计工时
remaining_hours decimal(6,1)               剩余工时
expected_finish_at datetime                预计完成时间
deadline datetime                          截止时间
sort_rank varchar(50)                      LexoRank 排序键；草稿/进行中/已完成/已关闭为 NULL
published_time datetime                    发布时间
distributed_time datetime                  分发时间
started_time datetime                      开始时间
completed_time datetime                    完成时间
closed_time datetime                       关闭时间
overdue_notified varchar(2) DEFAULT '0'    逾期通知已发标记（防重复轰炸）
version int                                乐观锁（@Version）
create_by → del_flag                       BaseEntity 公共字段

索引：(project_id, task_status, sort_rank)、(assignee_id, task_status, sort_rank)、
     (creator_id, task_status)、UNIQUE(project_id, seq_no)
```

### 3.4 VO 规范

按"继承 PO"模式：`TaskInfoVo extends TaskInfoDo`，仅追加翻译字段（`assigneeName`、`projectKey`、`waitingAheadCount` 队列前序数量等）与关联对象（评论列表、附件列表）。

---

## 四、权限模型

### 4.1 权限点

**角色权限点**（冒号风格命名，可配置到任意角色）：

```
pf:task:createDraft    创建草稿        pf:task:publish       发布任务
pf:task:viewProject    查看项目任务     pf:task:viewWorkload  查看人员当前任务
pf:task:viewMasked     查看跨项目脱敏   pf:task:distribute    分发任务
pf:task:changePriority 修改优先级      pf:task:dragSort      拖拽排序
pf:task:dragCross      跨执行人拖拽    pf:task:start         开始任务
pf:task:switchBack     切换回等待中     pf:task:complete      完成任务
pf:task:close          关闭任务        pf:task:reopen        重新打开
pf:comment:create      评论            pf:attachment:upload  上传附件
pf:member:manage       管理项目成员     pf:role:manage        管理项目角色
pf:log:view            查看操作日志
```

**创建者特权**（不走角色，只认 `creator_id`）：撤回待分发、编辑/删除/查看自己的草稿、查看自己创建任务的队列前序数量。

### 4.2 校验实现

- 自建 `@RequireProjectPermission("pf:task:distribute")` 注解 + `ProjectPermissionAspect` 切面：从方法参数解析 projectId/taskId，查出用户在该项目的多角色权限并集后校验，无权限抛业务异常（R 返回 2xxx 权限码）。
- 开始/切换/完成任务额外校验"当前用户 = assignee"。
- 前端登录后拉取 `GET /me/permissions` 缓存同一份权限点集合，控制按钮显隐；前后端同一套权限点常量。

### 4.3 角色规则

- 内置 4 角色：
  - `DISTRIBUTOR`（项目分发者）：权限全集，**不可编辑、不可删除**。
  - `PM`（项目经理）、`EXECUTOR`（执行人员）、`OBSERVER`（观察者）：内置但权限集可由分发者编辑——即 PRD"项目经理分发/关闭等可配置"的实现方式。
- 自定义角色：分发者创建，权限集 ⊆ DISTRIBUTOR 全集，可删除。
- 成员绑定多角色，最终权限 = 并集；一个项目可绑定多个 DISTRIBUTOR（避免单点）。
- 创建草稿/发布默认给 PM 与 DISTRIBUTOR（与 PRD 权限矩阵一致），EXECUTOR/OBSERVER 默认不含。

---

## 五、排序机制（优先级分组 + LexoRank）

> 2026-10-11 重构：原方案「显示顺序 = sort_rank 全序，允许越级拖拽（dragCross 打破不变式）」
> 按产品要求调整为「按优先级分组排序，桶内拖拽，禁止跨级」。

### 5.1 核心结论

**显示顺序 = 优先级降序（URGENT → LOW）分组 + 同优先级桶内 `sort_rank` 升序**。priority 的作用：

1. 决定显示分组与分组间顺序（紧急任务始终排在前面）；
2. 决定新任务入队插入位置（同优先级桶尾）；
3. 约束拖拽边界：**拖拽仅允许在同一优先级桶内调整顺序，跨优先级拖拽一律拒绝**（前端先拦截提示，服务端兜底校验，所有操作人生效）；
4. 卡片优先级色条展示。

桶边界与落点校验收敛在 `TaskQueueOrder`（纯函数）：桶首下标、桶尾插入位（紧急度不低于目标优先级的任务数）、落点越界即拒绝（业务码 3004）。

### 5.2 sort_rank 生命周期

| 状态 | sort_rank | 列表顺序 |
| --- | --- | --- |
| 草稿 | NULL | update_time 倒序（不参与排序） |
| 待分发 | 有，作用域=项目 | 优先级降序 + 桶内 sort_rank 升序 |
| 等待中 | 有，作用域=执行人 | 优先级降序 + 桶内 sort_rank 升序 |
| 进行中 | NULL | started_time 倒序 |
| 已完成 | NULL | completed_time 倒序 |
| 已关闭 | NULL | closed_time 倒序 |

入队与重置规则：

- 发布 → 待分发：rank = 同优先级桶尾（LexoRank 尾插）。
- 分发 → 等待中：rank = 该执行人队列同优先级桶尾（新作用域，重新分配）。
- 切换回等待中：**保留原 rank**（PRD 明确要求；进行中期间优先级不可改，原 rank 必在本优先级桶内）。
- 修改优先级：rank 重置为新优先级桶尾。
- 重新打开（执行人忙碌改判等待中）：rank 置顶到同优先级桶首。
- 拖到第 1 位确认切换：原进行中任务切回等待中保留 rank，新任务 rank 置 NULL 进入进行中。

### 5.3 拖拽约束（同优先级桶内）

- **所有操作人**（执行人本人 / 持 `pf:task:dragSort` 的管理者 / 持 `pf:task:dragCross` 的跨执行人操作者）一律只能把任务拖到**本优先级桶内**的空位：落点插入下标必须落在 [桶首, 桶尾] 闭区间内，越界返回 3004「不允许跨优先级拖拽」。
- 落点引用（beforeId/afterId）均空 = 插入本优先级桶尾（拖到列空白处即归位到本组末尾）。
- `pf:task:dragCross` 语义收敛为**跨执行人队列拖拽**（管理者整理执行人队列），仍受桶约束。
- **跨执行人拖拽**：前后端双重拒绝（等待中列按执行人分组，前端隔离；服务端校验目标作用域，业务码 3005）。
- 拖拽只改 `sort_rank`，不改 priority；跨组调整请用「修改优先级」（rank 自动重排到新桶尾）。

### 5.4 LexoRankUtil

- 自研工具类放 `common` 包，约 200 行：base 26 字符（a-z）中值计算 + 深度增长 + 重整。
- 重整策略：中值插入达到最大深度时，对当前作用域（如某执行人的等待中队列）按当前显示顺序重新均匀分布；<100 人规模下重整极低频。
- **服务端计算 rank，不信任前端提交的 rank 值**；接口只收目标位置（beforeId/afterId）。
- 并发拖拽冲突用 `version` 乐观锁兜底：更新失败返回业务码，前端提示刷新重拖。

---

## 六、API 设计

REST 前缀 `/api`，统一 `R<T>` 出参，分页 `pageNum/pageSize`。

| 模块 | 端点（示例） | 说明 |
| --- | --- | --- |
| 认证 | `POST /auth/login`、`POST /auth/refresh`、`POST /auth/logout` | 登录发 access(JWT 2h) + refresh token(7d) |
| 当前用户 | `GET /me/profile`、`GET /me/permissions`、`PUT /me/password`、`PUT /me/settings` | 权限并集一次性下发 |
| 项目 | `GET /projects`、`POST /projects`(管理员)、`GET /projects/{id}/members`、`POST /projects/{id}/members`、`PUT /projects/{id}/members/{userId}/roles`、`GET|POST|PUT /projects/{id}/roles` | 角色 CRUD 限分发者 |
| 任务 | `POST /tasks`（body 带 `action=DRAFT/PUBLISH`）、`PUT /tasks/{id}`、`POST /tasks/{id}/publish`、`POST /tasks/{id}/withdraw`、`POST /tasks/{id}/distribute`、`POST /tasks/{id}/start`、`POST /tasks/{id}/switch-back`、`POST /tasks/{id}/complete`、`POST /tasks/{id}/close`、`POST /tasks/{id}/reopen`、`POST /tasks/{id}/priority` | 每个状态流转独立端点，入参显式、审计清晰 |
| 排序 | `PUT /tasks/{id}/sort-rank`（body: beforeId/afterId，均空为桶尾） | 仅限同优先级桶内，服务端算 LexoRank |
| 看板 | `GET /projects/{id}/board?filters...` | 一次性返回六列结构化数据（含按执行人分组的等待中/进行中） |
| 我的任务 | `GET /tasks/mine?scope=waiting/in_progress/completed` | |
| 草稿 | `GET /tasks/my-drafts`、`DELETE /tasks/{id}` | |
| 负载 | `GET /projects/{id}/workload`、`GET /workload/cross-project` | 后者做脱敏（只回 project+priority） |
| 协作 | `GET|POST /tasks/{id}/comments`、`POST /tasks/{id}/attachments/presign`、`POST /tasks/{id}/attachments`（登记）、`GET /tasks/{id}/logs` | MinIO 预签名直传 |
| 通知 | `GET /notifications`、`PUT /notifications/{id}/read`、`PUT /notifications/read-all` | |
| 系统管理 | `GET|POST|PUT /system/users`、`GET /system/projects`、`GET /system/configs` | 管理员专用 |

筛选参数（看板/列表通用）：执行人、优先级、类型、状态、截止时间区间、是否逾期；搜索：标题、描述、任务编号。

---

## 七、状态机实现

### 7.1 代码结构

- `TaskStatusEnum` 定义状态；`TaskActionEnum` 定义动作（publish/withdraw/distribute/start/switchBack/complete/close/reopen）。
- **核心组件 `TaskStateMachine`**（task 域内独立类）：`execute(TaskInfoDo task, TaskActionEnum action, UserContext user, ActionContext ctx)`——内含「动作 → 允许的前置状态 + 角色权限点/创建者特权 + 额外条件」校验表，非法流转抛业务异常。
- 每个动作对应一个 `TaskActionHandler` 实现类（独立文件，无内部类），handler 负责：状态变更、时间戳、sort_rank 生命周期调整、乐观锁落库、发布领域事件。
- 每次 `execute` 成功后统一写 `task_operation_log` 并发布 `TaskChangedEvent`。

### 7.2 动作特殊条件（含 PRD 补漏）

| 动作 | 条件与行为 |
| --- | --- |
| publish | 必填项完整（标题/项目/优先级）；rank 尾插待分发桶；记 published_time |
| withdraw | 仅 creator_id 本人；目标回 DRAFT |
| distribute | 必须指定执行人；执行人必须为项目成员且其权限并集包含 `pf:task:start`（具备执行能力，否则拒绝分发）；写 distributor_id、distributed_time；rank 分配到执行人队列尾；通知执行人 |
| start | 校验该执行人当前无 IN_PROGRESS 任务，否则返回 3xxx 业务码，前端引导走切换流程 |
| switchBack | 保留原 sort_rank |
| complete | 记 completed_time；通知创建者/项目经理/分发者 |
| close | 需 `pf:task:close`；记 closed_time |
| reopen | 需 `pf:task:reopen`，必填 reason；**若执行人已有进行中任务 → 目标状态改判为 WAITING（rank 置顶到该执行人队列最前），否则 IN_PROGRESS**（PRD 状态表补漏点，已评审确认）；写日志 |

---

## 八、实时同步

- 端点 `/ws`，SockJS 回退 + STOMP；服务端 `SimpleBroker`（单实例，不引 MQ）。
- 订阅模型：
  - `/topic/project.{projectId}` —— 看板级任务变更（卡片增删改、排序、状态流转）
  - `/user/queue/notify` —— 个人通知（站内 + 触发桌面通知）
  - `/topic/project.{projectId}/presence` —— 成员在线/正在查看（**一期不实现**，仅预留主题约定）
- 订阅鉴权：`ChannelInterceptor` 在 SUBSCRIBE 时校验用户对目标项目的可见性，无权拒绝订阅。
- 推送消息体只含 `taskId + changeType + operatorId`，客户端收到后重新拉取对应列的看板数据（<100 人规模下全列刷新开销可忽略）；本机触发的变更按 `operatorId` 去重回显。
- 断线重连：STOMP 心跳 + 指数退避重连，重连成功后刷新当前看板。

---

## 九、通知与逾期

- 触发矩阵按 PRD 交互流程第 9 节实现：`TaskChangedEvent` 监听器统一查收件人列表（创建者/执行人/项目经理们/分发者们，去重、排除操作人），写 `notification_info` 并向 `/user/queue/notify` 推送；`channel=DESKTOP` 的通知由客户端收到后调 Electron `Notification` API。
- 评论 @：前端选择器插入 user id 标记，mentions 存 user_id 数组，额外通知被 @ 人。
- **逾期扫描**：`@Scheduled` 每小时扫 `deadline < now AND task_status IN (WAITING, IN_PROGRESS) AND overdue_notified='0'`，发通知并置 `overdue_notified='1'`，避免重复轰炸。

---

## 十、认证与安全

- 密码 BCrypt；JWT（HS256，含 userId）2 小时 + refresh token 7 天（服务端存库，可吊销）。
- Electron 端 token 用 `safeStorage` 加密存本地。
- JWT 拦截器统一鉴权；`is_admin` 用户仅放行 `/system/**` 与全局用户管理，不自动拥有项目业务权限（与 PRD 一致）。
- 富文本 XSS 清洗（白名单标签）后入库；MinIO 预签名 URL 短时效（15 分钟）；操作日志记录 IP/设备（User-Agent 摘要）。

---

## 十一、桌面端设计

### 11.1 目录结构

```
desktop/
├─ electron/            主进程：窗口、托盘、系统通知、safeStorage、自动更新(二期)
├─ src/
│  ├─ api/              axios 实例 + 按域接口封装 + token 刷新拦截器
│  ├─ ws/               STOMP 客户端封装、订阅管理、重连
│  ├─ stores/           Zustand：auth、board、draft、notification、ui(主题/视图)
│  ├─ components/ui/    shadcn 风格自绘基础组件（Button/Card/Dialog/Drawer…）
│  ├─ features/
│  │  ├─ board/         看板页：BoardColumn、TaskCard、TaskDrawer、BoardFilters、DragLayer
│  │  ├─ auth/          登录页
│  │  ├─ my-task/       我的任务
│  │  ├─ drafts/        我的草稿
│  │  ├─ workload/      人员负载
│  │  ├─ notification/  通知中心
│  │  └─ settings/      个人/项目成员与权限/系统管理
│  └─ router/           路由 + 权限守卫
```

### 11.2 关键交互实现

- **拖拽**（dnd-kit）三种语义：
  1. 待分发列排序——仅限同优先级桶内调整顺序（前端桶校验拦截 + 服务端兜底），落空白处归位到本组末尾；
  2. 等待中队列——同样仅限同优先级桶内，跨执行人拖拽需 `pf:task:dragCross`；
  3. 跨列拖入——如待分发→等待中触发分发弹窗选执行人。
- **拖到第 1 位切换**：执行人把等待中卡片拖到队首时弹确认框；确认后：当前进行中任务调 `switch-back`（保留原 rank）+ 新任务调 `start`。
- **实时协同**：本地乐观更新 + WS 推送对账，冲突时以服务端为准重排。
- **视图**：看板/列表切换、紧凑/舒适密度、亮色/暗色/跟随系统（`nativeTheme`）；优先级色条与状态色走设计 token。
- **脱敏渲染**：跨项目负载视图后端只下发 `{projectName, priority}`，前端无标题字段可渲染，从数据层面保证脱敏。
- **队列前序数量**：卡片显示"前面还有 N 个任务"，数据来自看板接口（对自己创建的任务/有 `pf:task:viewWorkload` 权限者计算）。

---

## 十二、测试策略

- **服务端单元测试（重点）**：
  - `LexoRankUtil`：中值、深度增长、重整边界全覆盖；
  - `TaskQueueOrder`：显示顺序（优先级降序+桶内 rank 升序）、桶边界定位（含桶缺失/空队列）、桶内合法与跨级拒绝；
  - `TaskStateMachine`：每个动作的合法/非法流转成对测试；
  - 权限切面：多角色并集、创建者特权、无权限拒绝。
- Controller 层 MockMvc 冒烟（认证、看板聚合、权限拦截）。
- **客户端**：拖拽桶约束前端镜像逻辑 vitest 单测；拖拽交互手测为主。
- **联调验收**：PRD 交互流程 1–10 逐条走查。

---

## 十三、一期范围边界

**做**：PRD 5.1 全部 + 多人可绑分发者 + 完全自定义角色 + 权限可编辑（内置角色除 DISTRIBUTOR 外）+ 评论 @ 通知 + 逾期通知 + 任务编号（{project_key}-{seq_no}）+ 自由标签（labels JSON）。

**不做**：子任务、转派拖拽、甘特图、日历视图（里程碑水平时间轴为轻量增补，见十四）、工时统计、多端同步、外部 IM 通知、标签管理页、自动更新（Electron auto-update 留二期）、集群部署。

---

## 十四、项目规划（里程碑）（增补）

> 2026-10-17 增补章节：新增「里程碑」菜单，承载项目阶段目标（里程碑）的时间轴管理。属一期边界（甘特图/日历）之外的轻量扩展，**任务关联里程碑为二期功能**。

### 14.1 范围决策

- 里程碑无状态字段（不做未完成/已完成）；卡片只展示名称与负责人。
- 任务关联本期不做：不改 `task_info`、任务表单/详情不出现里程碑字段。
- 编辑弹窗可修改预计完成日期；删除需 confirm 确认（无级联数据）。
- 权限：查看对所有项目成员开放；管理（增删改）默认授予 DISTRIBUTOR、PM。

### 14.2 数据模型

`milestone` 表（Flyway `V3__milestone.sql`，主表继承 BaseEntity 审计字段收尾）：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(128) | 主键 UUID |
| project_id | varchar(128) | 所属项目 |
| name | varchar(255) | 目标名称 |
| owner_id | varchar(128) | 负责人用户 ID（须为项目成员，可空） |
| expected_date | date | 预计完成日期（空 = 未规划日期；序列化固定 yyyy-MM-dd） |

索引：`idx_milestone_project_date (project_id, expected_date)`；删除走 `@TableLogic` 逻辑删。

### 14.3 权限与菜单

- 新增权限点：`pf:milestone:view`（查看里程碑，四个内置角色默认集均含）、`pf:milestone:manage`（管理里程碑，DISTRIBUTOR/PM 默认集含）。
- **不直接复用 `pf:task:viewProject` 做菜单准入**：`SystemMenuEnum` 单测约束权限点在目录树中恰好出现一次、准入权限必须挂在本菜单权限点之下，故必须新增独立查看权限点（效果等价：全员默认可见，自定义角色可在角色编辑树按「里程碑」分组勾选）。
- 菜单：`SystemMenuEnum.PLAN("plan", "里程碑", sortNo=4)`，位于「我的草稿」之后。

### 14.4 API 设计

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/projects/{projectId}/milestones` | milestone:view | 列表：日期升序 + 未规划日期随后，ownerName 批量翻译 |
| POST | `/api/projects/{projectId}/milestones` | milestone:manage | 创建（负责人须为项目成员，否则 4001） |
| PUT | `/api/projects/{projectId}/milestones/{milestoneId}` | milestone:manage | 全量覆盖（日期传空即移入未规划日期） |
| DELETE | `/api/projects/{projectId}/milestones/{milestoneId}` | milestone:manage | 逻辑删 |

业务码：`3009 MILESTONE_NOT_FOUND`（不存在或归属项目不符——防止 A 项目权限者以 B 项目 milestoneId 越权）。

### 14.5 实时同步

`MilestoneChangedEvent`（milestoneId/projectId/changeType/operatorId）→ AFTER_COMMIT 监听器复用 `/topic/project.{projectId}` 推送，消息体 `{milestoneId, changeType, operatorId}`；与任务消息（含 `taskId`）按字段名区分，客户端按 `operatorId` 去重后失效里程碑查询。

### 14.6 桌面端

- 菜单 `plan` → 路由 `/plan` → `features/plan/PlanPage.tsx`（单文件自包含：时间轴、卡片、增删改弹窗）。
- 时间轴：按 `expectedDate` 升序分组，组序偶数卡片在轴上方、奇数在下方交替；轴行显示 MM/DD/年份/数量徽章/快捷新增（预填日期）；轴右端全局「＋ 新增里程碑」；页尾「未规划日期」分区。
- 项目上下文为页内自选（同人员负载页模式）；WS 收到他人里程碑变更自动失效 `['milestones']` 查询。

---

## 附：确认记录

- 2026-10-10/11 设计评审：角色模型（完全自定义）、账号（自建）、JDK 21 + SB3、单实例、无子任务、管理员建项目+指定分发者、多分发者、Monorepo、LexoRank、MyBatis-Plus、dnd-kit、TailwindCSS+shadcn、MySQL 8.0、自建基础类、禁 tinyint（枚举用 varchar 字符串）。
- PRD 补漏决策：reopen 时执行人已有进行中任务 → 改判进入 WAITING 队首。
- 2026-10-17 项目规划（里程碑）增补确认：里程碑无状态字段；任务关联二期实现；编辑可修改预计完成日期；删除需 confirm；管理权限 = DISTRIBUTOR + PM（新增 pf:milestone:view/manage 双权限点，查看对全部成员开放）；复用项目 WS 主题推送里程碑变更。
