package com.sdecloud.priorityflow.integration;

import com.alibaba.fastjson2.JSON;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 全功能测试：覆盖设计文档第六节全部 API 端点、负路径（1xxx~4xxx 错误码）、
 * WebSocket 实时同步（看板主题/个人通知队列/订阅鉴权/无效令牌拒绝）、MinIO 真实上传下载。
 *
 * <p>与 SmokeFlowTest 共享 Spring 上下文；启动时清库并重建种子管理员。</p>
 *
 * @author priority-flow
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFunctionalTest {

    private static final String PROJECT_ID_KEY = "projectId";

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    private static final Map<String, String> IDS = new ConcurrentHashMap<>();

    private static final Map<String, String> REFRESH_TOKENS = new ConcurrentHashMap<>();

    private static boolean WIPED = false;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @BeforeEach
    void wipeDatabaseOnce() {
        if (WIPED) {
            return;
        }
        for (String table : new String[]{"sys_user_token", "notification_info", "task_attachment",
                "task_comment", "task_operation_log", "task_info", "milestone", "project_member_role", "project_role",
                "project_member", "project_info", "sys_user"}) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        // 重建种子管理员（固定 ID 便于断言）
        jdbcTemplate.execute("INSERT INTO sys_user (id, username, password_hash, display_name, status, is_admin) "
                + "VALUES ('admin-fixed-id', 'admin', '" + cn.hutool.crypto.digest.BCrypt.hashpw("admin123")
                + "', '系统管理员', '0', '1')");
        WIPED = true;
    }

    // ================= 请求与断言工具 =================

    private String json(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int code(MvcResult result) {
        return ((Number) JsonPath.read(json(result), "$.code")).intValue();
    }

    private MvcResult call(String method, String url, String token, String body) {
        try {
            var builder = switch (method) {
                case "GET" -> get(url);
                case "POST" -> post(url);
                case "PUT" -> put(url);
                case "DELETE" -> delete(url);
                default -> throw new IllegalArgumentException(method);
            };
            builder.contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                builder.content(body);
            }
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            return mockMvc.perform(builder).andReturn();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MvcResult call(String method, String url, String token) {
        return call(method, url, token, null);
    }

    /** 登录并缓存 token */
    private String login(String username, String password) {
        return TOKENS.computeIfAbsent(username, ignore -> {
            MvcResult result = call("POST", "/api/auth/login", null,
                    "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
            assertEquals(0, code(result), "登录失败：" + json(result));
            REFRESH_TOKENS.put(username, JsonPath.read(json(result), "$.data.refreshToken"));
            return JsonPath.read(json(result), "$.data.accessToken");
        });
    }

    private String createTask(String token, String title, String priority, String action) {
        MvcResult result = call("POST", "/api/tasks", token, "{\"projectId\":\"" + IDS.get(PROJECT_ID_KEY)
                + "\",\"title\":\"" + title + "\",\"priority\":\"" + priority + "\",\"action\":\"" + action + "\"}");
        assertEquals(0, code(result), json(result));
        return JsonPath.read(json(result), "$.data.id");
    }

    private List<Map<String, Object>> listData(MvcResult result) {
        return ((List<?>) JSON.parseObject(json(result)).get("data")).stream()
                .map(o -> (Map<String, Object>) o).toList();
    }

    /** 按 JsonPath 读取对象列表（显式走 List<?> 中转，规避泛型推断问题） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listDataAt(MvcResult result, String path) {
        return ((List<?>) JsonPath.read(json(result), path)).stream()
                .map(o -> (Map<String, Object>) o).toList();
    }

    /** 把 Object（实际为 JSON 数组）转为对象列表 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        return ((List<?>) value).stream().map(o -> (Map<String, Object>) o).toList();
    }

    // ================= 1. 账号与项目初始化 =================

    @Test
    @Order(1)
    void setup_usersAndProject() {
        String admin = login("admin", "admin123");
        IDS.put("adminId", "admin-fixed-id");

        // 创建用户：项目经理 / 两个执行人 / 观察者 / 项目外人员
        for (String[] u : new String[][]{
                {"pm1", "项目经理老张"}, {"executor1", "执行人小王"}, {"executor2", "执行人小李"},
                {"observer1", "观察者小赵"}, {"outsider1", "项目外人士"}}) {
            MvcResult result = call("POST", "/api/system/users", admin,
                    "{\"username\":\"" + u[0] + "\",\"displayName\":\"" + u[1] + "\",\"initialPassword\":\"pass123456\"}");
            assertEquals(0, code(result), json(result));
            IDS.put(u[0] + "Id", JsonPath.read(json(result), "$.data.id"));
        }

        // 创建项目并指定第一任分发者
        MvcResult project = call("POST", "/api/projects", admin,
                "{\"projectName\":\"全功能测试项目\",\"projectKey\":\"PFTEST\",\"distributorUserId\":\"admin-fixed-id\"}");
        assertEquals(0, code(project), json(project));
        IDS.put(PROJECT_ID_KEY, JsonPath.read(json(project), "$.data.id"));
        assertEquals("PFTEST", JsonPath.read(json(project), "$.data.projectKey"));

        String projectId = IDS.get(PROJECT_ID_KEY);
        addMemberAndBindRoles(admin, projectId, IDS.get("pm1Id"), "PM");
        addMemberAndBindRoles(admin, projectId, IDS.get("executor1Id"), "EXECUTOR");
        addMemberAndBindRoles(admin, projectId, IDS.get("executor2Id"), "EXECUTOR");
        addMemberAndBindRoles(admin, projectId, IDS.get("observer1Id"), "OBSERVER");

        for (String u : new String[]{"pm1", "executor1", "executor2", "observer1", "outsider1"}) {
            login(u, "pass123456");
        }
    }

    private void addMemberAndBindRoles(String admin, String projectId, String userId, String roleCode) {
        assertEquals(0, code(call("POST", "/api/projects/" + projectId + "/members", admin,
                "{\"userId\":\"" + userId + "\"}")), "添加成员失败");
        String roleId = listData(call("GET", "/api/projects/" + projectId + "/roles", admin)).stream()
                .filter(r -> roleCode.equals(r.get("roleCode")))
                .map(r -> (String) r.get("id")).findFirst().orElseThrow();
        assertEquals(0, code(call("PUT", "/api/projects/" + projectId + "/members/" + userId + "/roles", admin,
                "{\"roleIds\":[\"" + roleId + "\"]}")), "绑定角色失败");
    }

    // ================= 2. 认证边界 =================

    @Test
    @Order(2)
    void auth_negativeAndBoundary() {
        assertEquals(1003, code(call("POST", "/api/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"wrong\"}")));
        assertEquals(1001, code(call("GET", "/api/me/profile", null, null)));

        // 项目外人员：登录正常但无项目无权限，访问看板 2001
        String outsider = login("outsider1", "pass123456");
        MvcResult perms = call("GET", "/api/me/permissions", outsider, null);
        assertEquals(0, code(perms));
        assertEquals(0, ((List<?>) JsonPath.read(json(perms), "$.data.permissions")).size());
        // 菜单可见性：无权限用户仅通知中心与设置（个人设置必须可达）
        List<?> menus = JsonPath.read(json(perms), "$.data.menuList");
        assertEquals(List.of("notification", "settings"), menus);
        // 权限目录：菜单 → 权限点树登录即可获取
        MvcResult catalog = call("GET", "/api/me/permission-catalog", outsider, null);
        assertEquals(0, code(catalog));
        assertEquals(7, ((List<?>) JsonPath.read(json(catalog), "$.data.menuList")).size());
        assertEquals(2001, code(call("GET", "/api/projects/" + IDS.get(PROJECT_ID_KEY) + "/board", outsider, null)));
    }

    @Test
    @Order(3)
    void me_settingsAndPassword() {
        String executor1 = login("executor1", "pass123456");

        // 偏好设置：写入后 profile 可读回
        assertEquals(0, code(call("PUT", "/api/me/settings", executor1,
                "{\"settings\":{\"theme\":\"dark\",\"density\":\"compact\"}}")));
        MvcResult profile = call("GET", "/api/me/profile", executor1, null);
        assertEquals(0, code(profile));
        String settings = JsonPath.read(json(profile), "$.data.settings");
        assertTrue(settings.contains("dark"), "偏好设置应持久化：" + settings);

        // 修改密码：旧 refresh 立即失效、旧密码不能再登录、新密码可登录
        String oldRefresh = REFRESH_TOKENS.get("executor1");
        assertEquals(0, code(call("PUT", "/api/me/password", executor1,
                "{\"oldPassword\":\"pass123456\",\"newPassword\":\"newpass456\"}")));
        assertEquals(1005, code(call("POST", "/api/auth/refresh", null,
                "{\"refreshToken\":\"" + oldRefresh + "\"}")));
        assertEquals(1003, code(call("POST", "/api/auth/login", null,
                "{\"username\":\"executor1\",\"password\":\"pass123456\"}")));
        MvcResult relogin = call("POST", "/api/auth/login", null,
                "{\"username\":\"executor1\",\"password\":\"newpass456\"}");
        assertEquals(0, code(relogin));
        TOKENS.put("executor1", JsonPath.read(json(relogin), "$.data.accessToken"));
    }

    @Test
    @Order(4)
    void system_userManagement() {
        String admin = login("admin", "admin123");

        // 非管理员访问系统接口 → 2005
        assertEquals(2005, code(call("GET", "/api/system/users", login("executor1", "newpass456"), null)));

        // 禁用用户 → 其请求立即失效（1001）；启用恢复
        String executor2Id = IDS.get("executor2Id");
        assertEquals(0, code(call("PUT", "/api/system/users/" + executor2Id, admin, "{\"status\":\"1\"}")));
        assertEquals(1001, code(call("GET", "/api/me/profile", login("executor2", "pass123456"), null)));
        assertEquals(0, code(call("PUT", "/api/system/users/" + executor2Id, admin, "{\"status\":\"0\"}")));
        assertEquals(0, code(call("GET", "/api/me/profile", login("executor2", "pass123456"), null)));

        // 重置密码：旧密码失效、新密码生效
        assertEquals(0, code(call("PUT", "/api/system/users/" + executor2Id + "/reset-password", admin,
                "{\"newPassword\":\"resetpass789\"}")));
        assertEquals(1003, code(call("POST", "/api/auth/login", null,
                "{\"username\":\"executor2\",\"password\":\"pass123456\"}")));
        assertEquals(0, code(call("POST", "/api/auth/login", null,
                "{\"username\":\"executor2\",\"password\":\"resetpass789\"}")));

        // 系统项目列表与配置概览
        assertEquals(0, code(call("GET", "/api/system/projects", admin, null)));
        MvcResult configs = call("GET", "/api/system/configs", admin, null);
        assertEquals(0, code(configs));
        assertNotNull(JsonPath.read(json(configs), "$.data.serverTime"));
    }

    // ================= 3. 角色/成员管理 =================

    @Test
    @Order(5)
    void roles_customRoleLifecycleAndPermissionUnion() {
        String admin = login("admin", "admin123");
        String projectId = IDS.get(PROJECT_ID_KEY);
        String observer1 = login("observer1", "pass123456");

        // 自定义角色：观察者增强（负载查看 + 评论），绑定后并集立即生效
        MvcResult created = call("POST", "/api/projects/" + projectId + "/roles", admin,
                "{\"roleName\":\"观察者增强\",\"permissions\":[\"pf:task:viewWorkload\",\"pf:comment:create\"]}");
        assertEquals(0, code(created), json(created));
        String customRoleId = JsonPath.read(json(created), "$.data.id");

        assertEquals(0, code(call("PUT", "/api/projects/" + projectId + "/members/" + IDS.get("observer1Id")
                + "/roles", admin, "{\"roleIds\":[\"" + builtinRoleId(admin, projectId, "OBSERVER")
                + "\",\"" + customRoleId + "\"]}")));
        List<String> perms = JsonPath.read(json(
                call("GET", "/api/me/permissions?projectId=" + projectId, observer1, null)), "$.data.permissions");
        assertTrue(perms.contains("pf:task:viewWorkload"), "并集应包含自定义角色权限");
        assertTrue(perms.contains("pf:comment:create"));
        assertTrue(perms.contains("pf:log:view"), "并集应保留 OBSERVER 原有权限");

        // 编辑自定义角色：移除 viewWorkload → 并集缩小
        assertEquals(0, code(call("PUT", "/api/projects/" + projectId + "/roles/" + customRoleId, admin,
                "{\"roleName\":\"观察者增强\",\"permissions\":[\"pf:comment:create\"]}")));
        List<String> after = JsonPath.read(json(
                call("GET", "/api/me/permissions?projectId=" + projectId, observer1, null)), "$.data.permissions");
        assertFalse(after.contains("pf:task:viewWorkload"));
        assertTrue(after.contains("pf:comment:create"));

        // 删除自定义角色 → 绑定一并清理
        assertEquals(0, code(call("DELETE", "/api/projects/" + projectId + "/roles/" + customRoleId, admin, null)));
        List<String> finalPerms = JsonPath.read(json(
                call("GET", "/api/me/permissions?projectId=" + projectId, observer1, null)), "$.data.permissions");
        assertFalse(finalPerms.contains("pf:comment:create"));

        // 内置角色保护：DISTRIBUTOR 不可编辑、内置不可删除
        String distributorRoleId = builtinRoleId(admin, projectId, "DISTRIBUTOR");
        assertTrue(code(call("PUT", "/api/projects/" + projectId + "/roles/" + distributorRoleId, admin,
                "{\"roleName\":\"分发者\",\"permissions\":[\"pf:task:viewProject\"]}")) >= 4000);
        assertTrue(code(call("DELETE", "/api/projects/" + projectId + "/roles/" + distributorRoleId, admin, null)) >= 4000);

        // PM 角色权限可编辑：移除分发权限 → pm1 无法分发；恢复后可分发
        String pmRoleId = builtinRoleId(admin, projectId, "PM");
        List<String> pmPerms = currentPermissionCodes(admin, projectId, pmRoleId);
        assertEquals(0, code(call("PUT", "/api/projects/" + projectId + "/roles/" + pmRoleId, admin,
                "{\"roleName\":\"项目经理\",\"permissions\":" + jsonWithout(pmPerms, "pf:task:distribute") + "}")));
        String pm1 = login("pm1", "pass123456");
        String temp = createTask(admin, "权限验证占位任务", "LOW", "PUBLISH");
        assertTrue(code(call("POST", "/api/tasks/" + temp + "/distribute", pm1,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}")) >= 2000, "PM 被移除分发权限后应被拒绝");
        assertEquals(0, code(call("PUT", "/api/projects/" + projectId + "/roles/" + pmRoleId, admin,
                "{\"roleName\":\"项目经理\",\"permissions\":" + toJsonArray(pmPerms) + "}")));
        assertEquals(0, code(call("POST", "/api/tasks/" + temp + "/distribute", pm1,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}")));

        // 移除成员 → 立即失去看板权限；再加回恢复
        assertEquals(0, code(call("DELETE", "/api/projects/" + projectId + "/members/" + IDS.get("observer1Id"),
                admin, null)));
        assertEquals(2001, code(call("GET", "/api/projects/" + projectId + "/board", observer1, null)));
        addMemberAndBindRoles(admin, projectId, IDS.get("observer1Id"), "OBSERVER");
        assertEquals(0, code(call("GET", "/api/projects/" + projectId + "/board", observer1, null)));

        // 成员候选列表：排除已有成员；无 pf:member:manage 权限的成员被拒绝
        List<String> candidateIds = listData(call("GET", "/api/projects/" + projectId + "/member-candidates",
                admin, null)).stream().map(m -> (String) m.get("id")).toList();
        assertFalse(candidateIds.contains(IDS.get("pm1Id")), "候选列表应排除已有成员");
        assertTrue(candidateIds.contains(IDS.get("outsider1Id")), "项目外用户应在候选列表");
        assertEquals(2001, code(call("GET", "/api/projects/" + projectId + "/member-candidates",
                observer1, null)), "无 pf:member:manage 权限应被拒绝");
    }

    private String builtinRoleId(String admin, String projectId, String roleCode) {
        return listData(call("GET", "/api/projects/" + projectId + "/roles", admin)).stream()
                .filter(r -> roleCode.equals(r.get("roleCode")))
                .map(r -> (String) r.get("id")).findFirst().orElseThrow();
    }

    private List<String> currentPermissionCodes(String admin, String projectId, String roleId) {
        return listData(call("GET", "/api/projects/" + projectId + "/roles", admin)).stream()
                .filter(r -> roleId.equals(r.get("id")))
                .map(r -> (List<String>) r.get("permissionList"))
                .findFirst().orElseThrow();
    }

    private String toJsonArray(List<String> list) {
        return list.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private String jsonWithout(List<String> list, String removed) {
        return toJsonArray(list.stream().filter(s -> !s.equals(removed)).toList());
    }

    // ================= 4. 任务全生命周期 =================

    @Test
    @Order(6)
    void task_fullLifecycle() {
        String admin = login("admin", "admin123");
        String pm1 = login("pm1", "pass123456");
        String executor1 = login("executor1", "newpass456");

        // 缺优先级发布 → 4001
        MvcResult draftNoPriority = call("POST", "/api/tasks", pm1, "{\"projectId\":\"" + IDS.get(PROJECT_ID_KEY)
                + "\",\"title\":\"缺优先级任务\",\"action\":\"DRAFT\"}");
        assertEquals(0, code(draftNoPriority));
        assertTrue(code(call("POST", "/api/tasks/" + JsonPath.read(json(draftNoPriority), "$.data.id")
                + "/publish", pm1, null)) >= 4000, "缺优先级发布应被拒");

        // PM 创建并发布；执行人无发布权限不可编辑待分发任务
        MvcResult published = call("POST", "/api/tasks", pm1, "{\"projectId\":\"" + IDS.get(PROJECT_ID_KEY)
                + "\",\"title\":\"看板卡片组件\",\"priority\":\"HIGH\",\"taskType\":\"DEVELOP\",\"action\":\"PUBLISH\"}");
        assertEquals(0, code(published));
        String tP1 = JsonPath.read(json(published), "$.data.id");
        IDS.put("tP1", tP1);
        assertEquals("PFTEST-" + JsonPath.read(json(published), "$.data.seqNo"),
                JsonPath.read(json(published), "$.data.taskNo"));
        assertEquals(0, code(call("PUT", "/api/tasks/" + tP1, pm1, "{\"title\":\"看板卡片组件（改）\"}")));
        assertTrue(code(call("PUT", "/api/tasks/" + tP1, executor1, "{\"title\":\"越权修改\"}")) >= 2000);

        // 撤回：非创建者 2003；创建者撤回回草稿后重新发布
        MvcResult adminDraft = call("POST", "/api/tasks", admin, "{\"projectId\":\"" + IDS.get(PROJECT_ID_KEY)
                + "\",\"title\":\"管理员草稿\",\"priority\":\"HIGH\",\"action\":\"PUBLISH\"}");
        String tW1 = JsonPath.read(json(adminDraft), "$.data.id");
        IDS.put("tW1", tW1);
        assertEquals(2003, code(call("POST", "/api/tasks/" + tW1 + "/withdraw", pm1, null)));
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/withdraw", admin, null)));
        assertEquals("DRAFT", JsonPath.read(json(call("GET", "/api/tasks/" + tW1, admin, null)), "$.data.taskStatus"));
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/publish", admin, null)));

        // 分发：缺执行人 4001 / 非成员或无执行能力 3003 / 正常 0 / 已分发再分发 3001
        assertTrue(code(call("POST", "/api/tasks/" + tW1 + "/distribute", admin, "{}")) >= 4000);
        assertEquals(3003, code(call("POST", "/api/tasks/" + tW1 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("outsider1Id") + "\"}")));
        assertEquals(3003, code(call("POST", "/api/tasks/" + tW1 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("observer1Id") + "\"}")));
        MvcResult distributed = call("POST", "/api/tasks/" + tW1 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}");
        assertEquals(0, code(distributed), json(distributed));
        assertEquals("WAITING", JsonPath.read(json(distributed), "$.data.taskStatus"));
        String rankAfterDistribute = JsonPath.read(json(distributed), "$.data.sortRank");
        assertTrue(code(call("POST", "/api/tasks/" + tW1 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}")) >= 3000);

        // 开始：非执行人 2002 / 正常 / 已有进行中 3002 / 切回保留排序位
        assertEquals(2002, code(call("POST", "/api/tasks/" + tW1 + "/start", pm1, null)));
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/start", executor1, null)));
        String tW2 = createTask(admin, "修复登录缺陷", "URGENT", "PUBLISH");
        IDS.put("tW2", tW2);
        assertEquals(0, code(call("POST", "/api/tasks/" + tW2 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}")));
        assertEquals(3002, code(call("POST", "/api/tasks/" + tW2 + "/start", executor1, null)),
                "已有进行中任务时开始应被拒");
        MvcResult switched = call("POST", "/api/tasks/" + tW1 + "/switch-back", executor1, null);
        assertEquals(0, code(switched));
        assertEquals("WAITING", JsonPath.read(json(switched), "$.data.taskStatus"));
        assertEquals(rankAfterDistribute, JsonPath.read(json(switched), "$.data.sortRank"),
                "切换回等待中必须保留原排序位");
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/start", executor1, null)));

        // 完成/关闭/重开
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/complete", executor1, null)));
        assertEquals(3001, code(call("POST", "/api/tasks/" + tW1 + "/complete", executor1, null)));
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/close", admin, "{\"reason\":\"验收通过\"}")));
        assertEquals(4001, code(call("POST", "/api/tasks/" + tW1 + "/reopen", admin, "{}")));
        MvcResult reopened = call("POST", "/api/tasks/" + tW1 + "/reopen", admin, "{\"reason\":\"发现回归\"}");
        assertEquals(0, code(reopened), json(reopened));
        assertEquals("IN_PROGRESS", JsonPath.read(json(reopened), "$.data.taskStatus"), "执行人空闲时重开应回到进行中");

        // 关闭等待中任务 + 执行人忙碌时重开改判 WAITING 同优先级桶首
        assertEquals(0, code(call("POST", "/api/tasks/" + tW2 + "/close", admin, "{\"reason\":\"取消\"}")));
        String tW3 = createTask(admin, "数据库索引优化", "MEDIUM", "PUBLISH");
        IDS.put("tW3", tW3);
        assertEquals(0, code(call("POST", "/api/tasks/" + tW3 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}")));
        // 注意：tW1 重开后处于进行中（执行人忙碌），此处不再 start tW3
        String tW4 = createTask(admin, "日志脱敏改造", "LOW", "PUBLISH");
        IDS.put("tW4", tW4);
        MvcResult tW4Distributed = call("POST", "/api/tasks/" + tW4 + "/distribute", admin,
                "{\"assigneeId\":\"" + IDS.get("executor1Id") + "\"}");
        assertEquals(0, code(tW4Distributed));
        String tW4Rank = JsonPath.read(json(tW4Distributed), "$.data.sortRank");
        MvcResult busyReopen = call("POST", "/api/tasks/" + tW2 + "/reopen", admin, "{\"reason\":\"需求恢复\"}");
        assertEquals(0, code(busyReopen), json(busyReopen));
        assertEquals("WAITING", JsonPath.read(json(busyReopen), "$.data.taskStatus"), "执行人忙碌时重开应改判等待中");
        String tW2Rank = JsonPath.read(json(busyReopen), "$.data.sortRank");
        assertTrue(tW2Rank.compareTo(tW4Rank) < 0, "忙碌改判应置顶到同优先级桶首（URGENT 即队列最前）");
    }

    // ================= 5. 优先级与拖拽 =================

    @Test
    @Order(7)
    void priorityChange_andDragScenarios() {
        String admin = login("admin", "admin123");
        String executor1 = login("executor1", "newpass456");
        String executor2 = login("executor2", "resetpass789");
        String projectId = IDS.get(PROJECT_ID_KEY);

        // 发布三个不同优先级任务：显示顺序按优先级降序分组 → [PH, PMED, PL]
        String pLow = createTask(admin, "待分发低优先", "LOW", "PUBLISH");
        String pHigh = createTask(admin, "待分发高优先", "HIGH", "PUBLISH");
        String pMed = createTask(admin, "待分发中优先", "MEDIUM", "PUBLISH");
        IDS.put("pLow", pLow);
        IDS.put("pHigh", pHigh);
        IDS.put("pMed", pMed);
        assertEquals(List.of(pHigh, pMed, pLow),
                pendingDistOrder(admin, projectId, List.of(pLow, pMed, pHigh)), "看板应按优先级降序分组显示");

        // /priority 修改：HIGH → URGENT（URGENT 桶唯一元素，居首）
        assertEquals(0, code(call("POST", "/api/tasks/" + pHigh + "/priority", admin, "{\"priority\":\"URGENT\"}")));
        assertEquals(List.of(pHigh, pMed, pLow),
                pendingDistOrder(admin, projectId, List.of(pLow, pMed, pHigh)), "改优先级后应仍按优先级分组排序");

        // PUT 编辑改优先级同样触发重排：LOW → MEDIUM（rank 移到 MEDIUM 桶尾）
        assertEquals(0, code(call("PUT", "/api/tasks/" + pLow, admin, "{\"priority\":\"MEDIUM\"}")));
        assertEquals(List.of(pHigh, pMed, pLow),
                pendingDistOrder(admin, projectId, List.of(pMed, pLow, pHigh)));
        assertTrue(rankOf(admin, projectId, pLow).compareTo(rankOf(admin, projectId, pMed)) > 0,
                "改优先级后 rank 应重排到 MEDIUM 桶尾");

        // 进行中任务不允许改优先级（tW1 重开后处于进行中）
        assertTrue(code(call("POST", "/api/tasks/" + IDS.get("tW1") + "/priority", admin,
                "{\"priority\":\"LOW\"}")) >= 3000);

        // 待分发列同优先级桶内拖拽：新增 URGENT 任务拖到 pHigh 前 → 成功
        String pU2 = createTask(admin, "待分发紧急二号", "URGENT", "PUBLISH");
        String pHighRank = rankOf(admin, projectId, pHigh);
        MvcResult sameBucketDrag = call("PUT", "/api/tasks/" + pU2 + "/sort-rank", admin,
                "{\"beforeId\":\"" + pHigh + "\"}");
        assertEquals(0, code(sameBucketDrag), json(sameBucketDrag));
        assertTrue(((String) JsonPath.read(json(sameBucketDrag), "$.data.sortRank")).compareTo(pHighRank) < 0,
                "同优先级桶内拖拽应成功且排序键更小");

        // 跨优先级拖拽一律拒绝：URGENT 拖到 MEDIUM 之后 → 3004（持 dragCross 也不允许）
        assertEquals(3004, code(call("PUT", "/api/tasks/" + pU2 + "/sort-rank", admin,
                "{\"afterId\":\"" + pMed + "\"}")), "跨优先级拖拽应被拒绝");

        // 执行人拖待分发列（有 dragSort）：本桶尾插成功（pHigh 落回 URGENT 桶尾）
        assertEquals(0, code(call("PUT", "/api/tasks/" + pHigh + "/sort-rank", executor1, "{}")));

        // 等待队列：执行人自拖跨优先级 → 3004；本桶尾插 → 0
        String urgentA = createTask(admin, "执行人紧急任务A", "URGENT", "PUBLISH");
        String lowB = createTask(admin, "执行人低优先任务B", "LOW", "PUBLISH");
        distribute(admin, urgentA, IDS.get("executor2Id"));
        distribute(admin, lowB, IDS.get("executor2Id"));
        assertEquals(3004, code(call("PUT", "/api/tasks/" + lowB + "/sort-rank", executor2,
                "{\"beforeId\":\"" + urgentA + "\"}")), "执行人把 LOW 拖到 URGENT 前应被拒绝");
        assertEquals(0, code(call("PUT", "/api/tasks/" + lowB + "/sort-rank", executor2, "{}")));

        // 跨执行人拖拽 → 3005（目标位置任务不在自己队列）
        String dx = createTask(admin, "执行人1的等待任务", "MEDIUM", "PUBLISH");
        IDS.put("dx", dx);
        distribute(admin, dx, IDS.get("executor1Id"));
        assertEquals(3005, code(call("PUT", "/api/tasks/" + dx + "/sort-rank", executor1,
                "{\"beforeId\":\"" + urgentA + "\"}")));

        // 持 dragCross 者可拖他人队列，但仍限同优先级桶内：跨级 → 3004，桶内 → 0
        assertEquals(3004, code(call("PUT", "/api/tasks/" + lowB + "/sort-rank", admin,
                "{\"beforeId\":\"" + urgentA + "\"}")), "持 dragCross 也不允许跨优先级拖拽");
        String lowC = createTask(admin, "执行人低优先任务C", "LOW", "PUBLISH");
        distribute(admin, lowC, IDS.get("executor2Id"));
        assertEquals(0, code(call("PUT", "/api/tasks/" + lowC + "/sort-rank", admin,
                "{\"beforeId\":\"" + lowB + "\"}")), "持 dragCross 者可跨执行人做同优先级桶内拖拽");

        // executor2 开始任务，供后续负载断言（人员当前任务）
        IDS.put("executor2BusyTask", urgentA);
        assertEquals(0, code(call("POST", "/api/tasks/" + urgentA + "/start", executor2, null)));
    }

    private void distribute(String admin, String taskId, String assigneeId) {
        assertEquals(0, code(call("POST", "/api/tasks/" + taskId + "/distribute", admin,
                "{\"assigneeId\":\"" + assigneeId + "\"}")), "分发失败");
    }

    private List<String> pendingDistOrder(String admin, String projectId, List<String> taskIds) {
        MvcResult board = call("GET", "/api/projects/" + projectId + "/board", admin, null);
        List<String> ids = JsonPath.read(json(board), "$.data.pendingDistList[*].id");
        return ids.stream().filter(taskIds::contains).toList();
    }

    private String rankOf(String admin, String projectId, String taskId) {
        MvcResult board = call("GET", "/api/projects/" + projectId + "/board", admin, null);
        List<Map<String, Object>> pending = listDataAt(board, "$.data.pendingDistList");
        return pending.stream().filter(t -> taskId.equals(t.get("id")))
                .map(t -> (String) t.get("sortRank")).findFirst().orElseThrow();
    }

    // ================= 6. 看板聚合与筛选 =================

    @Test
    @Order(8)
    void board_aggregationFiltersAndAheadCount() {
        String admin = login("admin", "admin123");
        String projectId = IDS.get(PROJECT_ID_KEY);

        // 管理员草稿 + 逾期任务（截止时间已过，标题用 ASCII 便于关键字断言）
        createTask(admin, "admin-private-draft", "HIGH", "DRAFT");
        MvcResult overdue = call("POST", "/api/tasks", admin, "{\"projectId\":\"" + projectId
                + "\",\"title\":\"old-overdue-task\",\"priority\":\"HIGH\",\"action\":\"PUBLISH\","
                + "\"deadline\":\"2020-01-01 10:00:00\"}");
        assertEquals(0, code(overdue));
        IDS.put("overdueTask", JsonPath.read(json(overdue), "$.data.id"));
        distribute(admin, IDS.get("overdueTask"), IDS.get("executor1Id"));

        // 全量看板
        MvcResult board = call("GET", "/api/projects/" + projectId + "/board", admin, null);
        assertEquals(0, code(board));
        assertEquals("PFTEST", JsonPath.read(json(board), "$.data.projectKey"));
        // 草稿列只含自己的草稿
        List<Map<String, Object>> drafts = listDataAt(board, "$.data.draftList");
        assertTrue(drafts.stream().allMatch(t -> "admin-fixed-id".equals(t.get("creatorId"))));
        // 等待中为扁平列表（含全部执行人），管理员（有 viewWorkload）可见队列前序数
        List<Map<String, Object>> waitingTasks = listDataAt(board, "$.data.waitingList");
        assertTrue(waitingTasks.stream().anyMatch(t -> IDS.get("executor1Id").equals(t.get("assigneeId"))));
        assertTrue(waitingTasks.stream().anyMatch(t -> IDS.get("executor2Id").equals(t.get("assigneeId"))));
        for (Map<String, Object> task : waitingTasks) {
            if (IDS.get("executor1Id").equals(task.get("assigneeId"))) {
                assertNotNull(task.get("waitingAheadCount"), "有 viewWorkload 权限者应看到前序数量");
            }
        }
        // 进行中任务不带前序数量
        for (Map<String, Object> task : listDataAt(board, "$.data.inProgressList")) {
            assertNull(task.get("waitingAheadCount"), "进行中任务不应有前序数量");
        }
        // 观察者：无 viewWorkload，非自己创建的任务前序数量为 null
        MvcResult observerBoard = call("GET", "/api/projects/" + projectId + "/board",
                login("observer1", "pass123456"), null);
        boolean sawWaitingTasks = false;
        for (Map<String, Object> task : listDataAt(observerBoard, "$.data.waitingList")) {
            // 观察者无 viewWorkload 且未创建任何等待任务 → 前序数量一律为 null
            assertNull(task.get("waitingAheadCount"), "无权限且非创建者不应看到前序数量");
            sawWaitingTasks = true;
        }
        assertTrue(sawWaitingTasks, "观察者应能看到等待中的任务列表（无前序数量）");

        // 关键字筛选（ASCII 标题，避免 MockMvc 查询参数编码问题）
        MvcResult keyword = call("GET", "/api/projects/" + projectId + "/board?keyword=old-overdue", admin, null);
        // 逾期任务已被分发至等待列，关键字应命中等待列
        List<String> keywordWaiting = JsonPath.read(json(keyword),
                "$.data.waitingList[*].id");
        assertTrue(keywordWaiting.contains(IDS.get("overdueTask")), "关键字筛选应命中等待中的逾期任务");

        // 优先级筛选：URGENT 不含 LOW 任务
        List<String> urgentPending = JsonPath.read(json(
                call("GET", "/api/projects/" + projectId + "/board?priority=URGENT", admin, null)),
                "$.data.pendingDistList[*].id");
        assertFalse(urgentPending.contains(IDS.get("pLow")));

        // 执行人筛选：仅作用于执行态列，草稿/待分发始终全量
        MvcResult assigneeBoard = call("GET",
                "/api/projects/" + projectId + "/board?assigneeId=" + IDS.get("executor1Id"), admin, null);
        List<String> assigneePending = JsonPath.read(json(assigneeBoard), "$.data.pendingDistList[*].id");
        assertTrue(assigneePending.contains(IDS.get("pLow")), "待分发列不应受执行人筛选影响");
        List<String> assigneeDrafts = JsonPath.read(json(assigneeBoard), "$.data.draftList[*].id");
        assertFalse(assigneeDrafts.isEmpty(), "草稿列不应受执行人筛选影响");

        // 逾期筛选
        MvcResult overdueBoard = call("GET", "/api/projects/" + projectId + "/board?overdueOnly=true", admin, null);
        List<String> overdueWaiting = JsonPath.read(json(overdueBoard),
                "$.data.waitingList[*].id");
        assertTrue(overdueWaiting.contains(IDS.get("overdueTask")), "overdue filter should hit waiting task");
        List<String> overduePending = JsonPath.read(json(overdueBoard), "$.data.pendingDistList[*].id");
        assertFalse(overduePending.contains(IDS.get("overdueTask")));

        // 我的任务三作用域
        assertTrue(((List<?>) JsonPath.read(json(
                call("GET", "/api/tasks/mine?scope=waiting", login("executor1", "newpass456"), null)), "$.data")).size() >= 2);
        List<String> progressIds = JsonPath.read(json(
                call("GET", "/api/tasks/mine?scope=in_progress", login("executor1", "newpass456"), null)), "$.data[*].id");
        assertTrue(progressIds.contains(IDS.get("tW1")));
        // 完成 tW1（进行中）供 completed 作用域验证
        assertEquals(0, code(call("POST", "/api/tasks/" + IDS.get("tW1") + "/complete",
                login("executor1", "newpass456"), null)));
        List<String> completedIds = JsonPath.read(json(
                call("GET", "/api/tasks/mine?scope=completed", login("executor1", "newpass456"), null)), "$.data[*].id");
        assertTrue(completedIds.contains(IDS.get("tW1")));

        // 我的草稿
        MvcResult myDrafts = call("GET", "/api/tasks/my-drafts?pageNum=1&pageSize=10", admin, null);
        assertEquals(0, code(myDrafts));
        assertTrue(((Number) JsonPath.read(json(myDrafts), "$.data.total")).intValue() >= 1);
    }

    // ================= 7. 评论 =================

    @Test
    @Order(9)
    void comments_createListAndPermission() {
        String admin = login("admin", "admin123");
        String tW1 = IDS.get("tW1");

        MvcResult created = call("POST", "/api/tasks/" + tW1 + "/comments", admin,
                "{\"content\":\"请关注回归点\",\"mentions\":[\"" + IDS.get("executor1Id") + "\"]}");
        assertEquals(0, code(created), json(created));
        List<Map<String, Object>> comments = listData(call("GET", "/api/tasks/" + tW1 + "/comments", admin, null));
        assertEquals(1, comments.size());
        assertEquals("系统管理员", comments.get(0).get("authorName"));
        assertTrue(((List<?>) comments.get(0).get("mentionNameList")).contains("执行人小王"));

        // 执行人有评论权限；观察者（自定义角色已删）无评论权限
        assertEquals(0, code(call("POST", "/api/tasks/" + tW1 + "/comments", login("executor1", "newpass456"),
                "{\"content\":\"收到\",\"mentions\":[]}")));
        assertEquals(2001, code(call("POST", "/api/tasks/" + tW1 + "/comments", login("observer1", "pass123456"),
                "{\"content\":\"我看看\",\"mentions\":[]}")));
    }

    // ================= 8. 附件（MinIO 真实上传下载） =================

    @Test
    @Order(10)
    void attachments_realUploadDownloadAndRegisterGuard() throws Exception {
        String admin = login("admin", "admin123");
        String executor1 = login("executor1", "newpass456");
        String tW1 = IDS.get("tW1");

        // 观察者无上传权限
        assertEquals(2001, code(call("POST", "/api/tasks/" + tW1 + "/attachments/presign",
                login("observer1", "pass123456"), "{\"fileName\":\"x.png\"}")));

        // 预签名 → 真实 PUT 上传 MinIO → 登记 → 列表 → 下载内容一致
        MvcResult presign = call("POST", "/api/tasks/" + tW1 + "/attachments/presign", executor1,
                "{\"fileName\":\"设计稿.png\",\"contentType\":\"image/png\"}");
        assertEquals(0, code(presign), json(presign));
        String uploadUrl = JsonPath.read(json(presign), "$.data.uploadUrl");
        String objectKey = JsonPath.read(json(presign), "$.data.objectKey");
        byte[] content = ("priority-flow-attachment-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> upload = http.send(HttpRequest.newBuilder(URI.create(uploadUrl))
                        .header("Content-Type", "image/png")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, upload.statusCode(), "预签名 PUT 应成功：" + upload.body());

        MvcResult registered = call("POST", "/api/tasks/" + tW1 + "/attachments", executor1,
                "{\"fileName\":\"设计稿.png\",\"objectKey\":\"" + objectKey + "\",\"fileSize\":" + content.length
                        + ",\"contentType\":\"image/png\"}");
        assertEquals(0, code(registered), json(registered));

        List<Map<String, Object>> attachments = listData(call("GET", "/api/tasks/" + tW1 + "/attachments", admin, null));
        assertEquals(1, attachments.size());
        String downloadUrl = (String) attachments.get(0).get("downloadUrl");
        assertNotNull(downloadUrl);
        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, download.statusCode());
        assertTrue(java.util.Arrays.equals(content, download.body()), "下载内容应与上传一致");

        // 伪造对象键归属 → 4001
        assertEquals(4001, code(call("POST", "/api/tasks/" + IDS.get("tP1") + "/attachments", executor1,
                "{\"fileName\":\"恶意.png\",\"objectKey\":\"" + objectKey + "\",\"fileSize\":10}")));
    }

    // ================= 9. 操作日志 / 负载 =================

    @Test
    @Order(11)
    void logs_workload() {
        String admin = login("admin", "admin123");
        String tW1 = IDS.get("tW1");

        // 操作日志覆盖 tW1 的全部流转动作
        List<String> actions = JsonPath.read(json(call("GET", "/api/tasks/" + tW1 + "/logs", admin, null)),
                "$.data[*].action");
        for (String expected : new String[]{"create", "publish", "withdraw", "distribute", "start",
                "switchBack", "complete", "close", "reopen"}) {
            assertTrue(actions.contains(expected), "日志缺少动作 " + expected + "，实际=" + actions);
        }
        List<String> operators = JsonPath.read(json(call("GET", "/api/tasks/" + tW1 + "/logs", admin, null)),
                "$.data[*].operatorName");
        assertTrue(operators.contains("系统管理员"));
        // 拖拽日志在待分发任务上
        List<String> pHighActions = JsonPath.read(
                json(call("GET", "/api/tasks/" + IDS.get("pHigh") + "/logs", admin, null)), "$.data[*].action");
        assertTrue(pHighActions.contains("sortRank"));

        // 项目负载
        Map<String, Map<String, Object>> byUser = listData(call("GET",
                "/api/projects/" + IDS.get(PROJECT_ID_KEY) + "/workload", admin, null)).stream()
                .collect(Collectors.toMap(r -> (String) r.get("userId"), Function.identity()));
        assertTrue(byUser.containsKey(IDS.get("executor1Id")));
        assertNotNull(byUser.get(IDS.get("executor2Id")).get("currentTask"), "执行人2 应有进行中任务");
        assertEquals(2001, code(call("GET", "/api/projects/" + IDS.get(PROJECT_ID_KEY) + "/workload",
                login("observer1", "pass123456"), null)));
    }

    // ================= 10. 站内通知 =================

    @Test
    @Order(12)
    void notifications_flow() {
        String executor1 = login("executor1", "newpass456");

        int before = ((Number) JsonPath.read(json(call("GET", "/api/notifications/unread-count", executor1, null)),
                "$.data")).intValue();
        assertTrue(before >= 3, "执行人应收到分发/评论@/重开等通知，实际=" + before);

        List<String> types = JsonPath.read(json(call("GET", "/api/notifications?pageNum=1&pageSize=50", executor1, null)),
                "$.data[*].notifyType");
        assertTrue(types.contains("DISTRIBUTE"));
        assertTrue(types.contains("COMMENT_CREATED"));

        // 标记单条已读 → 未读数减一；全部已读 → 0
        String firstUnread = listData(call("GET", "/api/notifications?pageNum=1&pageSize=50", executor1, null))
                .stream().filter(n -> "0".equals(n.get("isRead"))).findFirst().orElseThrow()
                .get("id").toString();
        assertEquals(0, code(call("PUT", "/api/notifications/" + firstUnread + "/read", executor1, null)));
        int after = ((Number) JsonPath.read(json(call("GET", "/api/notifications/unread-count", executor1, null)),
                "$.data")).intValue();
        assertEquals(before - 1, after);
        assertEquals(0, code(call("PUT", "/api/notifications/read-all", executor1, null)));
        assertEquals(0, ((Number) JsonPath.read(json(call("GET", "/api/notifications/unread-count", executor1, null)),
                "$.data")).intValue());
    }

    // ================= 11. WebSocket 实时同步 =================

    @Test
    @Order(13)
    void websocket_boardPushUserNotifyAndAuth() throws Exception {
        String admin = login("admin", "admin123");
        String projectId = IDS.get(PROJECT_ID_KEY);

        // —— 看板主题：管理员订阅后，PM 发布任务应收到 PUBLISH 推送 ——
        StompSession adminWs = connectWs(TOKENS.get("admin"));
        try {
            LinkedBlockingQueue<String> boardQueue = subscribe(adminWs, "/topic/project." + projectId);
            Thread.sleep(500);
            String published = createTask(pm1Token(), "WebSocket 推送验证任务", "HIGH", "PUBLISH");
            String boardMessage = boardQueue.poll(5, TimeUnit.SECONDS);
            assertNotNull(boardMessage, "看板主题应收到任务变更推送");
            assertTrue(boardMessage.contains("\"changeType\":\"PUBLISH\""), boardMessage);
            assertTrue(boardMessage.contains(published), boardMessage);

            // —— 个人通知队列：分发动作推送给执行人 ——
            StompSession executorWs = connectWs(TOKENS.get("executor1"));
            try {
                LinkedBlockingQueue<String> notifyQueue = subscribe(executorWs, "/user/queue/notify");
                Thread.sleep(500);
                String wsTask = createTask(admin, "WebSocket 分发验证任务", "URGENT", "PUBLISH");
                distribute(admin, wsTask, IDS.get("executor1Id"));
                String notifyMessage = notifyQueue.poll(5, TimeUnit.SECONDS);
                assertNotNull(notifyMessage, "个人队列应收到通知推送");
                assertTrue(notifyMessage.contains("\"notifyType\":\"DISTRIBUTE\""), notifyMessage);
            } finally {
                safeDisconnect(executorWs);
            }

            // —— 非成员订阅看板主题：收不到任何推送 ——
            StompSession outsiderWs = connectWs(TOKENS.get("outsider1"));
            try {
                LinkedBlockingQueue<String> outsiderQueue = subscribe(outsiderWs, "/topic/project." + projectId);
                Thread.sleep(500);
                createTask(admin, "非成员不应收到的任务", "LOW", "PUBLISH");
                assertNull(outsiderQueue.poll(2, TimeUnit.SECONDS), "非成员订阅不应收到看板推送");
            } finally {
                safeDisconnect(outsiderWs);
            }
        } finally {
            safeDisconnect(adminWs);
        }

        // —— 无效令牌：握手直接失败 ——
        SockJsClient badSockJs = new SockJsClient(List.of(
                new WebSocketTransport(new StandardWebSocketClient()),
                new RestTemplateXhrTransport()));
        WebSocketStompClient badClient = new WebSocketStompClient(badSockJs);
        CompletableFuture<StompSession> badFuture = new CompletableFuture<>();
        badClient.connectAsync("http://localhost:" + port + "/ws?token=invalid-token",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        badFuture.complete(session);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        badFuture.completeExceptionally(exception);
                    }
                });
        try {
            badFuture.get(5, TimeUnit.SECONDS);
            fail("无效令牌的 WebSocket 连接应被拒绝");
        } catch (Exception expected) {
            // 连接被拒绝/超时均视为拒绝成功
        }
    }

    /** 在指定项目创建任务（createTask 固定走主测试项目，跨项目用例需要指定项目） */
    private String createTaskIn(String token, String projectId, String title, String priority, String action) {
        MvcResult result = call("POST", "/api/tasks", token, "{\"projectId\":\"" + projectId
                + "\",\"title\":\"" + title + "\",\"priority\":\"" + priority + "\",\"action\":\"" + action + "\"}");
        assertEquals(0, code(result), json(result));
        return JsonPath.read(json(result), "$.data.id");
    }

    // ================= 12. 跨项目看板与我的任务项目筛选 =================

    @Test
    @Order(14)
    void crossProject_boardAndMineFilter() {
        String admin = login("admin", "admin123");
        String pm1 = pm1Token();
        String executor1 = login("executor1", "newpass456");
        String outsider = login("outsider1", "pass123456");

        // 第二个项目：管理员为分发者（创建即成员），执行人1 加入；PM1 不加入
        MvcResult project2 = call("POST", "/api/projects", admin,
                "{\"projectName\":\"跨项目看板测试项目\",\"projectKey\":\"PFX\",\"distributorUserId\":\"admin-fixed-id\"}");
        assertEquals(0, code(project2), json(project2));
        String projectId2 = JsonPath.read(json(project2), "$.data.id");
        IDS.put("projectId2", projectId2);
        addMemberAndBindRoles(admin, projectId2, IDS.get("executor1Id"), "EXECUTOR");

        // 两个项目各造数据：项目一待分发 p1；项目二待分发 p2 + 已分发 w2；管理员两项目各一个草稿
        String p1 = createTask(pm1, "项目一待分发任务", "HIGH", "PUBLISH");
        String p2 = createTaskIn(admin, projectId2, "项目二待分发任务", "HIGH", "PUBLISH");
        String w2 = createTaskIn(admin, projectId2, "项目二等待任务", "URGENT", "PUBLISH");
        distribute(admin, w2, IDS.get("executor1Id"));
        createTaskIn(admin, IDS.get(PROJECT_ID_KEY), "项目一管理员草稿", "LOW", "DRAFT");
        createTaskIn(admin, projectId2, "项目二管理员草稿", "LOW", "DRAFT");

        // 任务编号按所属项目翻译
        assertTrue(((String) JsonPath.read(json(call("GET", "/api/tasks/" + p1, admin, null)), "$.data.taskNo"))
                .startsWith("PFTEST-"));
        assertTrue(((String) JsonPath.read(json(call("GET", "/api/tasks/" + p2, admin, null)), "$.data.taskNo"))
                .startsWith("PFX-"));

        // —— 管理员（两个项目均为成员）：跨项目看板同时聚合两项目数据 ——
        MvcResult adminBoard = call("GET", "/api/board", admin, null);
        assertEquals(0, code(adminBoard));
        List<String> adminPending = JsonPath.read(json(adminBoard), "$.data.pendingDistList[*].id");
        assertTrue(adminPending.contains(p1) && adminPending.contains(p2), "待分发列应同时包含两项目任务");
        List<String> adminWaiting = JsonPath.read(json(adminBoard), "$.data.waitingList[*].id");
        assertTrue(adminWaiting.contains(w2), "等待中列应包含项目二已分发任务");
        List<String> adminDrafts = JsonPath.read(json(adminBoard), "$.data.draftList[*].id");
        assertTrue(adminDrafts.size() >= 2, "草稿列应聚合两项目自己的草稿");

        // 指定项目筛选：只保留该项目
        MvcResult adminBoard2 = call("GET", "/api/board?projectId=" + projectId2, admin, null);
        assertEquals(0, code(adminBoard2));
        List<String> board2Pending = JsonPath.read(json(adminBoard2), "$.data.pendingDistList[*].id");
        assertTrue(board2Pending.contains(p2) && !board2Pending.contains(p1));

        // —— PM1（仅项目一成员）：看不到项目二；指定项目二 2001 ——
        MvcResult pmBoard = call("GET", "/api/board", pm1, null);
        assertEquals(0, code(pmBoard));
        List<String> pmPending = JsonPath.read(json(pmBoard), "$.data.pendingDistList[*].id");
        assertTrue(pmPending.contains(p1) && !pmPending.contains(p2), "PM1 不应看到未参与项目的任务");
        assertEquals(2001, code(call("GET", "/api/board?projectId=" + projectId2, pm1, null)));

        // —— 项目外人员：可见项目为空，返回空看板 ——
        MvcResult outsiderBoard = call("GET", "/api/board", outsider, null);
        assertEquals(0, code(outsiderBoard));
        assertEquals(0, listDataAt(outsiderBoard, "$.data.pendingDistList").size());
        assertEquals(0, listDataAt(outsiderBoard, "$.data.draftList").size());
        assertEquals(0, listDataAt(outsiderBoard, "$.data.waitingList").size());

        // —— 我的任务：默认跨项目，projectId 筛选后只留该项目 ——
        List<String> mineAll = JsonPath.read(json(call("GET", "/api/tasks/mine?scope=waiting", executor1, null)),
                "$.data[*].id");
        assertTrue(mineAll.contains(w2), "我的任务等待中应包含项目二任务");
        List<String> mineP2 = JsonPath.read(
                json(call("GET", "/api/tasks/mine?scope=waiting&projectId=" + projectId2, executor1, null)),
                "$.data[*].id");
        assertTrue(mineP2.contains(w2) && mineP2.stream().allMatch(mineAll::contains));
        List<String> mineP1 = JsonPath.read(
                json(call("GET", "/api/tasks/mine?scope=waiting&projectId=" + IDS.get(PROJECT_ID_KEY), executor1, null)),
                "$.data[*].id");
        assertFalse(mineP1.contains(w2), "按项目一筛选不应出现项目二任务");
    }

    @Test
    @Order(15)
    void task_progress() {
        String admin = login("admin", "admin123");
        String pm1 = pm1Token();
        String executor1 = login("executor1", "newpass456");

        // 创建并分发给执行人1（进入等待中）
        String tProgress = createTask(admin, "进度百分比任务", "HIGH", "PUBLISH");
        distribute(admin, tProgress, IDS.get("executor1Id"));

        // 执行人对等待中任务设 50 → 成功且 VO 回带 50
        MvcResult set50 = call("POST", "/api/tasks/" + tProgress + "/progress", executor1, "{\"progress\":50}");
        assertEquals(0, code(set50), json(set50));
        int progressAfterSet = JsonPath.read(json(set50), "$.data.progress");
        assertEquals(50, progressAfterSet);

        // 进度设 100 → 参数校验拒绝（100 仅由完成动作产生）
        assertTrue(code(call("POST", "/api/tasks/" + tProgress + "/progress", executor1, "{\"progress\":100}")) >= 4000,
                "手动设置 100 应被拒绝");

        // 非执行人 → 2002
        assertEquals(2002, code(call("POST", "/api/tasks/" + tProgress + "/progress", pm1, "{\"progress\":30}")));

        // 完成后进度自动置 100
        assertEquals(0, code(call("POST", "/api/tasks/" + tProgress + "/start", executor1, null)));
        MvcResult completed = call("POST", "/api/tasks/" + tProgress + "/complete", executor1, null);
        assertEquals(0, code(completed), json(completed));
        int progressAfterComplete = JsonPath.read(json(completed), "$.data.progress");
        assertEquals(100, progressAfterComplete);

        // 关闭后重新打开 → 进度归 0
        assertEquals(0, code(call("POST", "/api/tasks/" + tProgress + "/close", admin, "{\"reason\":\"验收\"}")));
        MvcResult reopened = call("POST", "/api/tasks/" + tProgress + "/reopen", admin, "{\"reason\":\"进度回归\"}");
        assertEquals(0, code(reopened), json(reopened));
        int progressAfterReopen = JsonPath.read(json(reopened), "$.data.progress");
        assertEquals(0, progressAfterReopen, "重新打开后进度应归 0");
    }

    private String pm1Token() {
        return login("pm1", "pass123456");
    }

    private StompSession connectWs(String token) throws Exception {
        // 服务端为 SockJS 端点（withSockJS），客户端必须用 SockJsClient 匹配帧协议
        SockJsClient sockJsClient = new SockJsClient(List.of(
                new WebSocketTransport(new StandardWebSocketClient()),
                new RestTemplateXhrTransport()));
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync("http://localhost:" + port + "/ws?token=" + token,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        future.complete(session);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        future.completeExceptionally(exception);
                    }
                });
        return future.get(5, TimeUnit.SECONDS);
    }

    /** 断开 STOMP 会话（会话可能已被服务端拒绝关闭，忽略二次断开异常） */
    private void safeDisconnect(StompSession session) {
        try {
            session.disconnect();
        } catch (Exception ignored) {
            // 连接已关闭
        }
    }

    private LinkedBlockingQueue<String> subscribe(StompSession session, String destination) {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                // content-type 为 application/json，用 byte[] 接收后自行转字符串
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return queue;
    }
}
