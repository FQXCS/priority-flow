# Priority Flow · 研发任务优先级看板

高颜值 Trello 风格研发任务优先级看板（PRD 一期完整实现）。

- 设计文档：[docs/superpowers/specs/2026-10-11-priority-flow-design.md](docs/superpowers/specs/2026-10-11-priority-flow-design.md)
- Monorepo 结构：`server/`（Spring Boot 3 模块化单体）+ `desktop/`（Electron + React 客户端）+ `docs/`

## 技术栈

| 端 | 技术 |
| --- | --- |
| 服务端 | JDK 21（虚拟线程）· Spring Boot 3.3 · MyBatis-Plus 3.5 · MySQL 8.0（utf8mb4，Flyway 管理）· MinIO（附件预签名直传）· STOMP over WebSocket · JWT(hutool) · fastjson2 |
| 桌面端 | Electron 33 · React 18 + TypeScript + Vite · TailwindCSS（shadcn 风格自绘组件）· dnd-kit 拖拽 · Zustand · TanStack Query · @stomp/stompjs + SockJS |

## 服务端（server/）

```bash
# 需 JAVA_HOME 指向 JDK 21（E:\Java\jdk-21.0.10）
cd server
E:/apache-maven-3.8.8/bin/mvn spring-boot:run     # 启动（默认 8080）
E:/apache-maven-3.8.8/bin/mvn test                # 70 个测试（单测 + 全链路冒烟 + 全功能测试）
```

- 数据库：首次启动自动建库 `priority_flow` 并执行 Flyway `V1__init.sql`（11 张表）
- 种子账号：**admin / admin123**（首次启动自动创建，登录后请修改）
- 连接配置：`server/src/main/resources/application.yml`（MySQL root/renyangming@localhost:3306，MinIO http://192.168.0.102:9000）

### 业务域

`com.sdecloud.priorityflow` 下七个域：`auth`（认证/JWT/用户）、`project`(项目/成员/角色权限)、`task`（任务/状态机/排序/队列）、`collab`（评论/附件）、`notification`（站内通知/WS 推送/逾期扫描）、`system`（管理员）、`common`（R/异常/BaseEntity/权限切面/LexoRankUtil/WS 配置）。每个域内为 DDD 三层：controller → service(+impl) → bean(dto/vo) + persistence(po/mapper/repository)。

### 核心机制

- **状态机**：`TaskStateMachine` + 8 个独立 `TaskActionHandler`（发布/撤回/分发/开始/切回/完成/关闭/重开），非法流转返回 3xxx 业务码；每次流转统一写操作日志并发布 `TaskChangedEvent`（驱动 WS 推送与站内通知，AFTER_COMMIT）。
- **排序**：优先级分组 + `LexoRankUtil`（base-26 字符串排序键，中值插入 + 深度增长 + 作用域重整）；显示顺序 = 优先级降序（URGENT→LOW）+ 同优先级桶内 sort_rank 升序；拖拽仅允许同优先级桶内调整顺序，跨级一律拒绝（业务码 3004，前端 `dragBucket` 镜像校验 + 服务端 `TaskQueueOrder` 兜底）；`pf:task:dragCross` 语义为跨执行人队列拖拽（仍限桶内）；服务端只收 beforeId/afterId 并计算 rank（均空=本桶尾），乐观锁兜底并发。
- **权限**：内置 4 角色（DISTRIBUTOR 全集不可改；PM/EXECUTOR/OBSERVER 可编辑）+ 自定义角色；成员多角色权限并集；`@RequireProjectPermission` 切面校验；创建者特权（撤回/草稿管理）只认 `creator_id`。
- **跨项目视图**：看板与我的任务默认聚合「我可见的全部项目」（成员且具备 `pf:task:viewProject`），项目作为普通筛选条件（`GET /api/board?projectId=`，mine 同理）；任务 VO 下发 `projectName`，卡片与详情展示所属项目；`GET /api/me/permissions` 不带 projectId 时返回全部参与项目的权限并集；顶栏不再有项目选择器，项目上下文由各页面自选（看板/我的任务筛选栏、人员负载与设置页内选择）；WS 订阅全部参与项目主题。待分发排序作用域为单项目，跨项目视图下前端拦截提示先选具体项目。
- **菜单权限**：侧边栏菜单按权限并集动态显隐（默认全部参与项目并集）。菜单与权限点的归属、准入权限定义在 `SystemMenuEnum`（单一事实源，单测保证权限点不重不漏）；`GET /api/me/permissions` 增量下发 `menuList`，前端侧边栏与路由守卫均按它过滤；`GET /api/me/permission-catalog` 下发「菜单 → 权限点」目录供角色编辑树渲染（前端不再硬编码权限清单）。准入规则：权限并集命中菜单任一准入权限即显示（看板←viewProject，我的任务←start，我的草稿←createDraft，人员负载←viewWorkload/viewMasked）；通知中心与设置登录即可见（个人设置必须可达）；系统管理员全量可见。
- **通知**：按通知矩阵（创建者/执行人/项目经理们/分发者们，去重排除操作人）写 `notification_info` 并推 `/user/queue/notify`；评论 @ 额外通知；每小时逾期扫描（deadline < now 且未终结且未通知过）。
- **安全**：BCrypt 密码；access 2h + refresh 7d（存库可吊销，登出/改密/禁用即吊销）；富文本 jsoup 白名单清洗；预签名 URL 15 分钟；操作日志记录 IP/设备。

## 桌面端（desktop/）

```bash
cd desktop
npm install          # 若 npm 拦截安装脚本：npm install-scripts approve electron esbuild && npm rebuild electron esbuild
npm run dev          # 开发模式（Vite 5173 代理 8080 + Electron 窗口）
npm run build        # 类型检查 + 渲染进程构建 + 主进程编译（dist/ + dist-electron/）
```

功能：登录（token 经 safeStorage 加密存本地，浏览器开发模式退化为 localStorage）、侧边栏菜单按权限并集动态显隐（路由守卫兜底）、看板（默认跨项目聚合全部可见任务 + 项目筛选；六列、按优先级分组排序 + 按执行人分组的等待中/进行中、dnd-kit 拖拽三语义：待分发/等待中均限同优先级桶内排序（跨级拦截）/跨列拖入触发分发弹窗、拖到第 1 位确认切换）、我的任务（默认跨项目 + 项目筛选；三列看板样式：等待中/进行中/已完成一屏总览，操作按钮随卡片，切换开始走确认弹窗）、我的草稿（编辑/发布/删除）、人员负载（页内选项目 + 跨项目脱敏）、通知中心、设置（个人偏好/成员与角色权限管理（页内选项目，「菜单 → 权限点」树形配置）/系统用户管理）；亮/暗/跟随系统主题；WS 实时对账（订阅全部参与项目，按 operatorId 去重回显）+ 桌面系统通知。

## 已知实现决策与偏差

1. **MinIO 桶名**：连接信息中的 `priority_flow` 含下划线，不符合 S3/MinIO 桶命名规范（启动校验会拒绝），实现使用 `priority-flow`（`pf.minio.bucket` 可配，应用启动时自动建桶）。
2. **进行中的 sort_rank**：设计 5.2 表格中「进行中 sort_rank = NULL」按「不参与排序」理解——列值保留进入等待队列时的 rank，使「切换回等待中保留原排序位」（PRD 明确要求）成为可能；完成/关闭/草稿态 rank 置 NULL。
3. **软删除与任务编号**：`task_info` 唯一索引 `(project_id, seq_no)` 不区分删除标记，序号生成使用原生 SQL 取含软删除行的 MAX(seq_no)。
4. **一期不做**（与设计 13 节一致）：子任务、转派拖拽、甘特图、日历、工时统计、外部 IM 通知、自动更新、在线状态（presence 仅预留主题）。
5. **排序方案重构（2026-10-11）**：原设计 5.1「显示顺序 = sort_rank 全序 + 越级拖拽（dragCross 打破不变式）」按产品要求改为「优先级分组排序（优先级降序 + 桶内 rank 升序），拖拽仅限同优先级桶内，禁止跨级」；`pf:task:dragCross` 语义收敛为跨执行人队列拖拽（仍限桶内），越级特权取消。
6. **跨项目看板（2026-10-11）**：设计文档为按项目看板（`GET /projects/{id}/board`）；按产品要求新增跨项目聚合视图（`GET /api/board`，项目为筛选条件），看板/我的任务默认展示全部可见项目任务，顶栏项目选择器移除、改为页面内筛选条件，权限接口不带 projectId 时返回全部参与项目并集（原为第一个项目）。

## 接口文档

应用启动后访问 Swagger UI：http://localhost:8080/swagger-ui.html
