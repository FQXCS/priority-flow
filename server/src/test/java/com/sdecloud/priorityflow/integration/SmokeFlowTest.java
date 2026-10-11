package com.sdecloud.priorityflow.integration;

import com.alibaba.fastjson2.JSON;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * PRD 交互流程全链路冒烟测试（真实 MySQL + Flyway + MinIO）：
 * 认证 → 建项目 → 建任务 → 发布/撤回 → 分发 → 开始/切换/完成 → 关闭/重开 → 看板 → 排序 → 评论/附件/日志/通知 → 权限拦截。
 *
 * @author priority-flow
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeFlowTest {

    /** 管理员初始密码（AdminUserInitializer） */
    private static final String ADMIN_PASSWORD = "admin123";

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    private static final Map<String, String> IDS = new ConcurrentHashMap<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.sdecloud.priorityflow.auth.init.AdminUserInitializer adminUserInitializer;

    /** 测试库只清一次（所有用例共享上下文与执行顺序） */
    private static boolean WIPED = false;

    @org.junit.jupiter.api.BeforeEach
    void wipeDatabaseOnce() {
        if (WIPED) {
            return;
        }
        for (String table : new String[]{"sys_user_token", "sys_user", "notification_info", "task_attachment",
                "task_comment", "task_operation_log", "task_info", "project_member_role", "project_role",
                "project_member", "project_info"}) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        // 重建种子管理员
        adminUserInitializer.run(null);
        WIPED = true;
    }

    // ---------------- 请求与断言工具 ----------------

    private String json(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取响应失败", e);
        }
    }

    /** 统一 R 响应 code 取值（消除 JsonPath 数字类型歧义） */
    private int code(MvcResult result) {
        return ((Number) JsonPath.read(json(result), "$.code")).intValue();
    }

    private MvcResult postJson(String url, String token, String body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult putJson(String url, String token, String body) throws Exception {
        var request = put(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult getJson(String url, String token) throws Exception {
        var request = get(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listData(MvcResult result) {
        return (List<Map<String, Object>>) JSON.parseObject(json(result)).get("data");
    }

    // ---------------- 1. 认证 ----------------

    @Test
    @Order(1)
    void loginShouldIssueTokens() throws Exception {
        MvcResult result = postJson("/api/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}");
        assertEquals(0, code(result));
        assertEquals("admin", JsonPath.read(json(result), "$.data.user.username"));
        assertNotNull(JsonPath.read(json(result), "$.data.accessToken"));
        IDS.put("adminId", JsonPath.read(json(result), "$.data.user.id"));
        TOKENS.put("admin", JsonPath.read(json(result), "$.data.accessToken"));
        TOKENS.put("adminRefresh", JsonPath.read(json(result), "$.data.refreshToken"));
    }

    @Test
    @Order(2)
    void loginWithWrongPasswordShouldFail() throws Exception {
        MvcResult result = postJson("/api/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"wrong\"}");
        assertEquals(1003, code(result));
    }

    @Test
    @Order(3)
    void unauthenticatedRequestShouldReturn1001() throws Exception {
        assertEquals(1001, code(getJson("/api/me/profile", null)));
    }

    // ---------------- 2. 系统管理：创建用户 / 刷新令牌 ----------------

    @Test
    @Order(10)
    void adminShouldCreateUsers() throws Exception {
        MvcResult executor = postJson("/api/system/users", TOKENS.get("admin"),
                "{\"username\":\"executor1\",\"displayName\":\"执行人小王\",\"initialPassword\":\"pass123456\"}");
        assertEquals(0, code(executor));
        IDS.put("executorId", JsonPath.read(json(executor), "$.data.id"));

        MvcResult observer = postJson("/api/system/users", TOKENS.get("admin"),
                "{\"username\":\"observer1\",\"displayName\":\"观察者小李\",\"initialPassword\":\"pass123456\"}");
        assertEquals(0, code(observer));
        IDS.put("observerId", JsonPath.read(json(observer), "$.data.id"));
    }

    @Test
    @Order(11)
    void refreshTokenShouldRotate() throws Exception {
        MvcResult result = postJson("/api/auth/refresh", null,
                "{\"refreshToken\":\"" + TOKENS.get("adminRefresh") + "\"}");
        assertEquals(0, code(result));
        assertNotNull(JsonPath.read(json(result), "$.data.accessToken"));
        // 旧 refresh 已轮换失效
        MvcResult reuse = postJson("/api/auth/refresh", null,
                "{\"refreshToken\":\"" + TOKENS.get("adminRefresh") + "\"}");
        assertEquals(1005, code(reuse));
        TOKENS.put("adminRefresh", JsonPath.read(json(result), "$.data.refreshToken"));
    }

    // ---------------- 3. 项目与角色 ----------------

    @Test
    @Order(20)
    void adminShouldCreateProjectWithBuiltinRoles() throws Exception {
        MvcResult result = postJson("/api/projects", TOKENS.get("admin"),
                "{\"projectName\":\"研发看板\",\"projectKey\":\"PF\",\"distributorUserId\":\""
                        + IDS.get("adminId") + "\",\"remark\":\"冒烟测试项目\"}");
        assertEquals(0, code(result));
        IDS.put("projectId", JsonPath.read(json(result), "$.data.id"));

        String projectId = IDS.get("projectId");
        // 内置 4 角色已初始化
        assertEquals(4, listData(getJson("/api/projects/" + projectId + "/roles", TOKENS.get("admin"))).size());
        // 成员含第一任分发者
        assertEquals(1, listData(getJson("/api/projects/" + projectId + "/members", TOKENS.get("admin"))).size());

        // 加入执行人与观察者为成员
        assertEquals(0, code(postJson("/api/projects/" + projectId + "/members",
                TOKENS.get("admin"), "{\"userId\":\"" + IDS.get("executorId") + "\"}")));
        assertEquals(0, code(postJson("/api/projects/" + projectId + "/members",
                TOKENS.get("admin"), "{\"userId\":\"" + IDS.get("observerId") + "\"}")));

        // 给执行人绑定 EXECUTOR 内置角色
        String executorRoleId = listData(getJson("/api/projects/" + projectId + "/roles", TOKENS.get("admin")))
                .stream().filter(r -> "EXECUTOR".equals(r.get("roleCode")))
                .map(r -> (String) r.get("id")).findFirst().orElse(null);
        assertNotNull(executorRoleId);
        assertEquals(0, code(putJson("/api/projects/" + projectId + "/members/"
                + IDS.get("executorId") + "/roles", TOKENS.get("admin"),
                "{\"roleIds\":[\"" + executorRoleId + "\"]}")));

        // 管理员权限并集含分发/跨执行人拖拽
        MvcResult permissions = getJson("/api/me/permissions?projectId=" + projectId, TOKENS.get("admin"));
        List<String> perms = JsonPath.read(json(permissions), "$.data.permissions");
        assertTrue(perms.contains("pf:task:distribute"));
        assertTrue(perms.contains("pf:task:dragCross"));
    }

    // ---------------- 4. 任务：创建/发布/撤回/删除 ----------------

    @Test
    @Order(30)
    void createPublishAndWithdrawFlow() throws Exception {
        String token = TOKENS.get("admin");
        // 直接发布（action=PUBLISH）
        MvcResult published = postJson("/api/tasks", token, "{\"projectId\":\"" + IDS.get("projectId")
                + "\",\"title\":\"搭建看板骨架\",\"priority\":\"HIGH\",\"taskType\":\"DEVELOP\","
                + "\"action\":\"PUBLISH\",\"labels\":[\"前端\"],\"estimatedHours\":16,\"deadline\":\"2026-12-31 18:00:00\"}");
        assertEquals(0, code(published));
        assertEquals("PF-1", JsonPath.read(json(published), "$.data.taskNo"));
        assertEquals("PENDING_DIST", JsonPath.read(json(published), "$.data.taskStatus"));
        assertNotNull(JsonPath.read(json(published), "$.data.sortRank"));
        IDS.put("task1", JsonPath.read(json(published), "$.data.id"));

        // 存草稿（action=DRAFT）
        MvcResult draft = postJson("/api/tasks", token, "{\"projectId\":\"" + IDS.get("projectId")
                + "\",\"title\":\"待定任务\",\"action\":\"DRAFT\"}");
        assertEquals(0, code(draft));
        assertEquals("PF-2", JsonPath.read(json(draft), "$.data.taskNo"));
        assertEquals("DRAFT", JsonPath.read(json(draft), "$.data.taskStatus"));
        IDS.put("draftId", JsonPath.read(json(draft), "$.data.id"));

        // 草稿编辑 + 发布
        assertEquals(0, code(putJson("/api/tasks/" + IDS.get("draftId"), token,
                "{\"title\":\"待定任务（已定）\",\"priority\":\"URGENT\"}")));
        MvcResult published2 = postJson("/api/tasks/" + IDS.get("draftId") + "/publish", token, "");
        assertEquals("PENDING_DIST", JsonPath.read(json(published2), "$.data.taskStatus"));

        // 创建者撤回 → 回草稿
        MvcResult withdrawn = postJson("/api/tasks/" + IDS.get("draftId") + "/withdraw", token, "");
        assertEquals("DRAFT", JsonPath.read(json(withdrawn), "$.data.taskStatus"));
        // 草稿删除
        MvcResult deleted = mockMvc.perform(delete("/api/tasks/" + IDS.get("draftId"))
                .header("Authorization", "Bearer " + token)).andReturn();
        assertEquals(0, code(deleted));
    }

    // ---------------- 5. 分发 / 开始 / 切换 / 完成 / 关闭 / 重开 ----------------

    @Test
    @Order(40)
    void distributeStartSwitchCompleteCloseReopenFlow() throws Exception {
        String admin = TOKENS.get("admin");
        String projectId = IDS.get("projectId");
        String task1 = IDS.get("task1");

        // 非成员分发被拒（3xxx）
        MvcResult badAssignee = postJson("/api/tasks/" + task1 + "/distribute", admin,
                "{\"assigneeId\":\"no-such-user\"}");
        assertTrue(code(badAssignee) >= 3000);

        // 分发 PF-1 给执行人
        MvcResult distributed = postJson("/api/tasks/" + task1 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executorId") + "\"}");
        assertEquals(0, code(distributed));
        assertEquals("WAITING", JsonPath.read(json(distributed), "$.data.taskStatus"));

        // 执行人登录
        MvcResult login = postJson("/api/auth/login", null,
                "{\"username\":\"executor1\",\"password\":\"pass123456\"}");
        TOKENS.put("executor", JsonPath.read(json(login), "$.data.accessToken"));

        // 我的等待任务可见
        MvcResult mine = getJson("/api/tasks/mine?scope=waiting", TOKENS.get("executor"));
        assertEquals(1, listData(mine).size());

        // 执行人无分发权限（EXECUTOR 角色默认不含 distribute）
        MvcResult denied = postJson("/api/tasks/" + task1 + "/distribute", TOKENS.get("executor"),
                "{\"assigneeId\":\"" + IDS.get("executorId") + "\"}");
        assertEquals(2001, code(denied));

        // 开始 → 进行中
        MvcResult started = postJson("/api/tasks/" + task1 + "/start", TOKENS.get("executor"), "");
        assertEquals("IN_PROGRESS", JsonPath.read(json(started), "$.data.taskStatus"));

        // 创建第二个任务并分发给同一执行人，验证"开始时已有进行中任务被拒"
        // （草稿 PF-2 已删除但仍占用编号，故此任务编号为 PF-3）
        MvcResult task2 = postJson("/api/tasks", admin, "{\"projectId\":\"" + projectId
                + "\",\"title\":\"修复登录缺陷\",\"priority\":\"URGENT\",\"taskType\":\"BUG\",\"action\":\"PUBLISH\"}");
        assertEquals("PF-3", JsonPath.read(json(task2), "$.data.taskNo"));
        IDS.put("task2", JsonPath.read(json(task2), "$.data.id"));
        postJson("/api/tasks/" + IDS.get("task2") + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executorId") + "\"}");
        MvcResult secondStart = postJson("/api/tasks/" + IDS.get("task2") + "/start", TOKENS.get("executor"), "");
        assertEquals(3002, code(secondStart));

        // 切换回等待中
        MvcResult switched = postJson("/api/tasks/" + task1 + "/switch-back", TOKENS.get("executor"), "");
        assertEquals("WAITING", JsonPath.read(json(switched), "$.data.taskStatus"));

        // 完成 → 关闭 → 重新打开（执行人此时空闲 → IN_PROGRESS）
        postJson("/api/tasks/" + task1 + "/start", TOKENS.get("executor"), "");
        MvcResult completed = postJson("/api/tasks/" + task1 + "/complete", TOKENS.get("executor"), "");
        assertEquals("COMPLETED", JsonPath.read(json(completed), "$.data.taskStatus"));
        MvcResult closed = postJson("/api/tasks/" + task1 + "/close", admin, "{\"reason\":\"验收通过\"}");
        assertEquals("CLOSED", JsonPath.read(json(closed), "$.data.taskStatus"));
        MvcResult reopened = postJson("/api/tasks/" + task1 + "/reopen", admin, "{\"reason\":\"发现问题回归\"}");
        assertEquals("IN_PROGRESS", JsonPath.read(json(reopened), "$.data.taskStatus"));
    }

    // ---------------- 6. 看板聚合 / 拖拽排序 ----------------

    @Test
    @Order(50)
    void boardShouldAggregateColumns() throws Exception {
        MvcResult board = getJson("/api/projects/" + IDS.get("projectId") + "/board", TOKENS.get("admin"));
        assertEquals(0, code(board));
        assertEquals("PF", JsonPath.read(json(board), "$.data.projectKey"));
        // 等待中按执行人分组（task2=PF-3 等待中），进行中分组含 task1=PF-1
        List<String> waitingTaskNos = JsonPath.read(json(board), "$.data.waitingGroupList[*].taskList[*].taskNo");
        assertTrue(waitingTaskNos.contains("PF-3"), "等待中应含 PF-3，实际=" + waitingTaskNos);
        List<String> inProgressTaskNos = JsonPath.read(json(board), "$.data.inProgressGroupList[*].taskList[*].taskNo");
        assertTrue(inProgressTaskNos.contains("PF-1"), "进行中应含 PF-1，实际=" + inProgressTaskNos);
    }

    @Test
    @Order(51)
    void dragSortShouldReorderPendingDist() throws Exception {
        String admin = TOKENS.get("admin");
        // 发布两个任务到待分发列：task3(HIGH) 先入队，task4(LOW) 尾插
        MvcResult task3 = postJson("/api/tasks", admin, "{\"projectId\":\"" + IDS.get("projectId")
                + "\",\"title\":\"整理文档\",\"priority\":\"HIGH\",\"action\":\"PUBLISH\"}");
        assertEquals(0, code(task3));
        IDS.put("task3", JsonPath.read(json(task3), "$.data.id"));
        MvcResult task4 = postJson("/api/tasks", admin, "{\"projectId\":\"" + IDS.get("projectId")
                + "\",\"title\":\"准备发布说明\",\"priority\":\"LOW\",\"action\":\"PUBLISH\"}");
        assertEquals(0, code(task4));
        IDS.put("task4", JsonPath.read(json(task4), "$.data.id"));
        MvcResult task5 = postJson("/api/tasks", admin, "{\"projectId\":\"" + IDS.get("projectId")
                + "\",\"title\":\"校对文档\",\"priority\":\"HIGH\",\"action\":\"PUBLISH\"}");
        assertEquals(0, code(task5));
        IDS.put("task5", JsonPath.read(json(task5), "$.data.id"));

        MvcResult board = getJson("/api/projects/" + IDS.get("projectId") + "/board", admin);
        String task3Rank = JsonPath.read(json(board), "$.data.pendingDistList[0].sortRank");

        // 同优先级（HIGH）桶内拖拽：task5 拖到 task3 前 → 成功
        MvcResult sorted = putJson("/api/tasks/" + IDS.get("task5") + "/sort-rank", admin,
                "{\"beforeId\":\"" + IDS.get("task3") + "\"}");
        assertEquals(0, code(sorted));
        String newRank = JsonPath.read(json(sorted), "$.data.sortRank");
        assertTrue(newRank.compareTo(task3Rank) < 0, "新排序键 " + newRank + " 应小于 " + task3Rank);

        // 跨优先级拖拽被拒：LOW 的 task4 拖到 HIGH 的 task3 前 → 3004
        assertEquals(3004, code(putJson("/api/tasks/" + IDS.get("task4") + "/sort-rank", admin,
                        "{\"beforeId\":\"" + IDS.get("task3") + "\"}")),
                "跨优先级拖拽应被拒绝");

        // 执行人（有 dragSort）把 task4 拖回本优先级桶尾（无邻居 → 桶尾插）
        MvcResult executorSort = putJson("/api/tasks/" + IDS.get("task4") + "/sort-rank", TOKENS.get("executor"), "{}");
        assertEquals(0, code(executorSort));
        String tailRank = JsonPath.read(json(executorSort), "$.data.sortRank");
        assertTrue(tailRank.compareTo(task3Rank) > 0, "桶尾插入键 " + tailRank + " 应大于 " + task3Rank);
    }

    // ---------------- 7. 评论 / 附件 / 日志 / 通知 ----------------

    @Test
    @Order(60)
    void commentAttachmentLogNotificationFlow() throws Exception {
        String admin = TOKENS.get("admin");
        String taskId = IDS.get("task1");

        // 评论 @ 执行人
        MvcResult comment = postJson("/api/tasks/" + taskId + "/comments", admin,
                "{\"content\":\"请关注回归点\",\"mentions\":[\"" + IDS.get("executorId") + "\"]}");
        assertEquals(0, code(comment));
        assertEquals(1, listData(getJson("/api/tasks/" + taskId + "/comments", admin)).size());

        // 附件预签名（真实 MinIO，验证桶存在）
        MvcResult presign = postJson("/api/tasks/" + taskId + "/attachments/presign", admin,
                "{\"fileName\":\"设计稿.png\",\"contentType\":\"image/png\"}");
        assertEquals(0, code(presign));
        assertNotNull(JsonPath.read(json(presign), "$.data.uploadUrl"));
        assertTrue(((String) JsonPath.read(json(presign), "$.data.objectKey")).startsWith("task/" + taskId + "/"));

        // 操作日志（动作编码为 TaskActionEnum 小写 code）
        MvcResult logs = getJson("/api/tasks/" + taskId + "/logs", admin);
        List<String> actions = JsonPath.read(json(logs), "$.data[*].action");
        assertTrue(actions.contains("publish"));
        assertTrue(actions.contains("distribute"));
        assertTrue(actions.contains("reopen"));

        // 执行人站内通知：被分发 + 被评论 @，未读数 ≥ 2
        MvcResult unread = getJson("/api/notifications/unread-count", TOKENS.get("executor"));
        assertTrue(((Number) JsonPath.read(json(unread), "$.data")).intValue() >= 2);
        MvcResult notifications = getJson("/api/notifications?pageNum=1&pageSize=20", TOKENS.get("executor"));
        List<String> types = JsonPath.read(json(notifications), "$.data[*].notifyType");
        assertTrue(types.contains("DISTRIBUTE"));
        assertTrue(types.contains("COMMENT_CREATED"));
        // 标记全部已读
        assertEquals(0, code(putJson("/api/notifications/read-all", TOKENS.get("executor"), "")));
        MvcResult unreadAfter = getJson("/api/notifications/unread-count", TOKENS.get("executor"));
        assertEquals(0, ((Number) JsonPath.read(json(unreadAfter), "$.data")).intValue());
    }

    // ---------------- 8. 负载 / 权限拦截 ----------------

    @Test
    @Order(70)
    void workloadAndMaskedCrossProject() throws Exception {
        MvcResult workload = getJson("/api/projects/" + IDS.get("projectId") + "/workload", TOKENS.get("admin"));
        assertTrue(listData(workload).stream().anyMatch(r -> IDS.get("executorId").equals(r.get("userId"))));
        // 执行人无 viewWorkload 权限
        MvcResult denied = getJson("/api/projects/" + IDS.get("projectId") + "/workload", TOKENS.get("executor"));
        assertEquals(2001, code(denied));

        // 跨项目脱敏（管理员有 viewMasked）：只含 project+priority，无标题字段
        MvcResult masked = getJson("/api/workload/cross-project", TOKENS.get("admin"));
        assertEquals(0, code(masked));
        List<Map<String, Object>> maskedRows = listData(masked);
        if (!maskedRows.isEmpty()) {
            assertFalse(maskedRows.get(0).containsKey("title"));
            assertTrue(maskedRows.get(0).containsKey("priority"));
        }
    }

    @Test
    @Order(71)
    void executorShouldNotManageRoles() throws Exception {
        MvcResult denied = postJson("/api/projects/" + IDS.get("projectId") + "/roles", TOKENS.get("executor"),
                "{\"roleName\":\"自定义\",\"permissions\":[]}");
        assertEquals(2001, code(denied));
    }
}
