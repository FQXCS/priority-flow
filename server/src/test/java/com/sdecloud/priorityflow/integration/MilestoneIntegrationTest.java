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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 项目规划里程碑集成测试：CRUD 正路径、权限负路径（2xxx/3xxx/4xxx）、跨项目越权、列表排序。
 *
 * <p>启动时清库并重建种子管理员，随后按 Order 顺序执行。</p>
 *
 * @author priority-flow
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MilestoneIntegrationTest {

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    private static final Map<String, String> IDS = new ConcurrentHashMap<>();

    private static boolean WIPED = false;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private String login(String username, String password) {
        return TOKENS.computeIfAbsent(username, ignore -> {
            MvcResult result = call("POST", "/api/auth/login", null,
                    "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
            assertEquals(0, code(result), "登录失败：" + json(result));
            return JsonPath.read(json(result), "$.data.accessToken");
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listData(MvcResult result) {
        return ((List<?>) JsonPath.read(json(result), "$.data")).stream()
                .map(o -> (Map<String, Object>) o).toList();
    }

    /**
     * 取单个 data 对象（fastjson2 默认不输出 null 字段，断言空值须走 Map.get）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapData(MvcResult result) {
        return (Map<String, Object>) JSON.parseObject(json(result)).get("data");
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

    // ================= 1. 账号与项目初始化 =================

    @Test
    @Order(1)
    void setup_usersAndProjects() {
        String admin = login("admin", "admin123");

        // 创建用户：分发者 / 项目经理 / 执行人 / 观察者 / 项目外人员
        for (String[] u : new String[][]{
                {"dist1", "分发者老陈"}, {"pm1", "项目经理小林"}, {"executor1", "执行人小王"},
                {"observer1", "观察者小赵"}, {"outsider1", "项目外人士"}}) {
            MvcResult result = call("POST", "/api/system/users", admin,
                    "{\"username\":\"" + u[0] + "\",\"displayName\":\"" + u[1] + "\",\"initialPassword\":\"pass123456\"}");
            assertEquals(0, code(result), json(result));
            IDS.put(u[0] + "Id", JsonPath.read(json(result), "$.data.id"));
        }

        // 同一分发者建两个项目：B 项目仅用于跨项目越权校验
        MvcResult projectA = call("POST", "/api/projects", admin,
                "{\"projectName\":\"规划测试项目A\",\"projectKey\":\"PLNA\",\"distributorUserId\":\""
                        + IDS.get("dist1Id") + "\"}");
        assertEquals(0, code(projectA), json(projectA));
        IDS.put("projectAId", JsonPath.read(json(projectA), "$.data.id"));

        MvcResult projectB = call("POST", "/api/projects", admin,
                "{\"projectName\":\"规划测试项目B\",\"projectKey\":\"PLNB\",\"distributorUserId\":\""
                        + IDS.get("dist1Id") + "\"}");
        assertEquals(0, code(projectB), json(projectB));
        IDS.put("projectBId", JsonPath.read(json(projectB), "$.data.id"));

        // 分发者登录后自行管理成员（admin 非项目成员，无成员管理权限）
        String dist1 = login("dist1", "pass123456");
        String projectIdA = IDS.get("projectAId");
        addMemberAndBindRoles(dist1, projectIdA, IDS.get("pm1Id"), "PM");
        addMemberAndBindRoles(dist1, projectIdA, IDS.get("executor1Id"), "EXECUTOR");
        addMemberAndBindRoles(dist1, projectIdA, IDS.get("observer1Id"), "OBSERVER");

        for (String u : new String[]{"pm1", "executor1", "observer1", "outsider1"}) {
            login(u, "pass123456");
        }
    }

    // ================= 2. 创建 =================

    @Test
    @Order(2)
    void create_byDistributor() {
        String dist1 = TOKENS.get("dist1");
        String projectIdA = IDS.get("projectAId");

        MvcResult m1 = call("POST", "/api/projects/" + projectIdA + "/milestones", dist1,
                "{\"name\":\"四张清单对接完成\",\"ownerId\":\"" + IDS.get("executor1Id")
                        + "\",\"expectedDate\":\"2026-11-04\"}");
        assertEquals(0, code(m1), json(m1));
        IDS.put("mAId", JsonPath.read(json(m1), "$.data.id"));
        assertEquals("执行人小王", JsonPath.read(json(m1), "$.data.ownerName"));
        assertEquals("2026-11-04", JsonPath.read(json(m1), "$.data.expectedDate"));

        MvcResult m2 = call("POST", "/api/projects/" + projectIdA + "/milestones", dist1,
                "{\"name\":\"十月底评审\",\"ownerId\":\"" + IDS.get("pm1Id") + "\",\"expectedDate\":\"2026-10-31\"}");
        assertEquals(0, code(m2), json(m2));
        IDS.put("mBId", JsonPath.read(json(m2), "$.data.id"));

        // 未规划日期 + 未指定负责人（fastjson2 不输出 null 字段，用 Map 断言）
        MvcResult m3 = call("POST", "/api/projects/" + projectIdA + "/milestones", dist1,
                "{\"name\":\"远期目标\"}");
        assertEquals(0, code(m3), json(m3));
        IDS.put("mCId", JsonPath.read(json(m3), "$.data.id"));
        Map<String, Object> m3Data = mapData(m3);
        assertNull(m3Data.get("expectedDate"));
        assertNull(m3Data.get("ownerName"));
    }

    @Test
    @Order(3)
    void create_negative() {
        String dist1 = TOKENS.get("dist1");
        String observer1 = TOKENS.get("observer1");
        String outsider1 = TOKENS.get("outsider1");
        String projectIdA = IDS.get("projectAId");
        String url = "/api/projects/" + projectIdA + "/milestones";

        // 负责人不是项目成员
        assertEquals(4001, code(call("POST", url, dist1,
                "{\"name\":\"非法负责人\",\"ownerId\":\"" + IDS.get("outsider1Id") + "\"}")));
        // 名称空白
        assertEquals(4001, code(call("POST", url, dist1, "{\"name\":\"   \"}")));
        // 观察者无管理权限
        assertEquals(2001, code(call("POST", url, observer1, "{\"name\":\"观察者尝试\"}")));
        // 项目外人员
        assertEquals(2001, code(call("POST", url, outsider1, "{\"name\":\"外人尝试\"}")));
    }

    // ================= 3. 列表 =================

    @Test
    @Order(4)
    void list_orderAndViewPermissions() {
        String projectIdA = IDS.get("projectAId");
        String url = "/api/projects/" + projectIdA + "/milestones";

        // 执行人（仅 view 权限）可查，且按日期升序、未规划日期最后
        MvcResult list = call("GET", url, TOKENS.get("executor1"));
        assertEquals(0, code(list), json(list));
        List<Map<String, Object>> data = listData(list);
        assertEquals(3, data.size());
        assertEquals("十月底评审", data.get(0).get("name"));
        assertEquals("四张清单对接完成", data.get(1).get("name"));
        assertEquals("远期目标", data.get(2).get("name"));

        assertEquals(0, code(call("GET", url, TOKENS.get("observer1"))));
        // 项目外人员不可查
        assertEquals(2001, code(call("GET", url, TOKENS.get("outsider1"))));
    }

    // ================= 4. 编辑 =================

    @Test
    @Order(5)
    void update_byDistributor() {
        String dist1 = TOKENS.get("dist1");
        String projectIdA = IDS.get("projectAId");
        String url = "/api/projects/" + projectIdA + "/milestones/" + IDS.get("mCId");

        // 改名 + 补负责人 + 补日期
        MvcResult updated = call("PUT", url, dist1,
                "{\"name\":\"远期目标改名\",\"ownerId\":\"" + IDS.get("observer1Id")
                        + "\",\"expectedDate\":\"2026-12-01\"}");
        assertEquals(0, code(updated), json(updated));
        assertEquals("远期目标改名", JsonPath.read(json(updated), "$.data.name"));
        assertEquals("观察者小赵", JsonPath.read(json(updated), "$.data.ownerName"));
        assertEquals("2026-12-01", JsonPath.read(json(updated), "$.data.expectedDate"));

        // 日期与负责人置空 → 移入未规划日期
        MvcResult cleared = call("PUT", url, dist1, "{\"name\":\"远期目标改名\"}");
        assertEquals(0, code(cleared), json(cleared));
        Map<String, Object> clearedData = mapData(cleared);
        assertNull(clearedData.get("expectedDate"));
        assertNull(clearedData.get("ownerName"));
    }

    @Test
    @Order(6)
    void update_negativeAndCrossProject() {
        String dist1 = TOKENS.get("dist1");
        String projectIdA = IDS.get("projectAId");
        String projectIdB = IDS.get("projectBId");

        // 观察者无管理权限
        assertEquals(2001, code(call("PUT", "/api/projects/" + projectIdA + "/milestones/" + IDS.get("mAId"),
                TOKENS.get("observer1"), "{\"name\":\"观察者尝试\"}")));

        // 项目 B 先建一个里程碑，再用 A 项目 URL 操作它 → 归属校验 3009
        MvcResult created = call("POST", "/api/projects/" + projectIdB + "/milestones", dist1,
                "{\"name\":\"项目B里程碑\",\"expectedDate\":\"2026-11-20\"}");
        assertEquals(0, code(created), json(created));
        String milestoneBId = JsonPath.read(json(created), "$.data.id");

        assertEquals(3009, code(call("PUT", "/api/projects/" + projectIdA + "/milestones/" + milestoneBId,
                dist1, "{\"name\":\"越权尝试\"}")));
        assertEquals(3009, code(call("DELETE", "/api/projects/" + projectIdA + "/milestones/" + milestoneBId,
                dist1, null)));

        // 不存在的里程碑
        assertEquals(3009, code(call("PUT", "/api/projects/" + projectIdA + "/milestones/not-exist-id",
                dist1, "{\"name\":\"任意\"}")));
    }

    // ================= 5. 删除 =================

    @Test
    @Order(7)
    void delete_byDistributor() {
        String dist1 = TOKENS.get("dist1");
        String projectIdA = IDS.get("projectAId");
        String url = "/api/projects/" + projectIdA + "/milestones/" + IDS.get("mBId");

        // 观察者无管理权限
        assertEquals(2001, code(call("DELETE", url, TOKENS.get("observer1"), null)));

        assertEquals(0, code(call("DELETE", url, dist1, null)));
        MvcResult list = call("GET", "/api/projects/" + projectIdA + "/milestones", TOKENS.get("pm1"));
        List<Map<String, Object>> data = listData(list);
        assertEquals(2, data.size());
        assertTrue(data.stream().noneMatch(m -> IDS.get("mBId").equals(m.get("id"))));

        // 重复删除 → 逻辑删后查不到
        assertEquals(3009, code(call("DELETE", url, dist1, null)));
        assertFalse(data.stream().anyMatch(m -> "十月底评审".equals(m.get("name"))));
    }
}
