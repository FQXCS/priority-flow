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
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 计划周报集成测试：生成聚合（种子任务走完整任务流）、发布唯一性、参数校验、
 * 编辑/删除（3010）、权限负路径（2001）、跨项目列表可见性与排序。
 *
 * <p>启动时清库并重建种子管理员，随后按 Order 顺序执行。</p>
 *
 * @author priority-flow
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeeklyReportIntegrationTest {

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    private static final Map<String, String> IDS = new ConcurrentHashMap<>();

    private static boolean WIPED = false;

    /** 统计周期：本周一 ~ 本周日（完成任务完成时间 = 测试执行时刻，必落在本周期内） */
    private static final LocalDate PERIOD_START = LocalDate.now().with(DayOfWeek.MONDAY);
    private static final LocalDate PERIOD_END = PERIOD_START.plusDays(6);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                "task_comment", "task_operation_log", "task_info", "milestone", "weekly_report_info",
                "project_member_role", "project_role",
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

    private void addMemberAndBindRoles(String admin, String projectId, String userId, String roleCode) {
        assertEquals(0, code(call("POST", "/api/projects/" + projectId + "/members", admin,
                "{\"userId\":\"" + userId + "\"}")), "添加成员失败");
        String roleId = listData(call("GET", "/api/projects/" + projectId + "/roles", admin)).stream()
                .filter(r -> roleCode.equals(r.get("roleCode")))
                .map(r -> (String) r.get("id")).findFirst().orElseThrow();
        assertEquals(0, code(call("PUT", "/api/projects/" + projectId + "/members/" + userId + "/roles", admin,
                "{\"roleIds\":[\"" + roleId + "\"]}")), "绑定角色失败");
    }

    /**
     * 发布一条任务到指定状态：创建 → 发布 → 分发，随后按需 开始/设进度/完成
     */
    private String seedTask(String operator, String assignee, String title, String priority,
                            LocalDate expectedFinish, LocalDate deadline, boolean startAndProgress,
                            boolean complete, int progress) {
        StringBuilder body = new StringBuilder("{\"action\":\"PUBLISH\",\"projectId\":\"" + IDS.get("projectAId")
                + "\",\"title\":\"" + title + "\",\"priority\":\"" + priority + "\"");
        if (expectedFinish != null) {
            body.append(",\"expectedFinishAt\":\"").append(expectedFinish.atTime(18, 0).format(DATETIME)).append("\"");
        }
        if (deadline != null) {
            body.append(",\"deadline\":\"").append(deadline.atTime(12, 0).format(DATETIME)).append("\"");
        }
        body.append("}");
        MvcResult created = call("POST", "/api/tasks", operator, body.toString());
        assertEquals(0, code(created), json(created));
        String taskId = JsonPath.read(json(created), "$.data.id");
        assertEquals(0, code(call("POST", "/api/tasks/" + taskId + "/distribute", operator,
                "{\"assigneeId\":\"" + assignee + "\"}")), "分发失败");
        if (startAndProgress) {
            // 状态动作由执行人本人执行（assignee 是用户 ID，调用须带其登录 token）
            String executorToken = TOKENS.get("executor1");
            assertEquals(0, code(call("POST", "/api/tasks/" + taskId + "/start", executorToken, null)), "开始失败");
            assertEquals(0, code(call("POST", "/api/tasks/" + taskId + "/progress", executorToken,
                    "{\"progress\":" + progress + "}")), "设置进度失败");
        }
        if (complete) {
            assertEquals(0, code(call("POST", "/api/tasks/" + taskId + "/complete", TOKENS.get("executor1"), null)),
                    "完成失败");
        }
        return taskId;
    }

    // ================= 1. 账号与项目初始化 =================

    @Test
    @Order(1)
    void setup_usersAndProjects() {
        String admin = login("admin", "admin123");

        for (String[] u : new String[][]{
                {"dist1", "分发者老陈"}, {"pm1", "项目经理小林"}, {"executor1", "执行人小王"},
                {"observer1", "观察者小赵"}, {"outsider1", "项目外人士"}}) {
            MvcResult result = call("POST", "/api/system/users", admin,
                    "{\"username\":\"" + u[0] + "\",\"displayName\":\"" + u[1] + "\",\"initialPassword\":\"pass123456\"}");
            assertEquals(0, code(result), json(result));
            IDS.put(u[0] + "Id", JsonPath.read(json(result), "$.data.id"));
        }

        MvcResult projectA = call("POST", "/api/projects", admin,
                "{\"projectName\":\"周报测试项目A\",\"projectKey\":\"WRPA\",\"distributorUserId\":\""
                        + IDS.get("dist1Id") + "\"}");
        assertEquals(0, code(projectA), json(projectA));
        IDS.put("projectAId", JsonPath.read(json(projectA), "$.data.id"));

        MvcResult projectB = call("POST", "/api/projects", admin,
                "{\"projectName\":\"周报测试项目B\",\"projectKey\":\"WRPB\",\"distributorUserId\":\""
                        + IDS.get("dist1Id") + "\"}");
        assertEquals(0, code(projectB), json(projectB));
        IDS.put("projectBId", JsonPath.read(json(projectB), "$.data.id"));

        String dist1 = login("dist1", "pass123456");
        String projectIdA = IDS.get("projectAId");
        String projectIdB = IDS.get("projectBId");
        addMemberAndBindRoles(dist1, projectIdA, IDS.get("pm1Id"), "PM");
        addMemberAndBindRoles(dist1, projectIdA, IDS.get("executor1Id"), "EXECUTOR");
        addMemberAndBindRoles(dist1, projectIdA, IDS.get("observer1Id"), "OBSERVER");
        // pm1 同时是项目 B 成员：用于跨项目列表可见性与排序
        addMemberAndBindRoles(dist1, projectIdB, IDS.get("pm1Id"), "PM");

        for (String u : new String[]{"pm1", "executor1", "observer1", "outsider1"}) {
            login(u, "pass123456");
        }
    }

    // ================= 2. 种子任务（走完整任务流） =================

    @Test
    @Order(2)
    void setup_seedTasks() {
        String pm1 = TOKENS.get("pm1");
        String executor1 = IDS.get("executor1Id");
        LocalDate nextStart = PERIOD_END.plusDays(1);

        // 本周完成：completed_time = 完成动作时刻，必落在本周期
        seedTask(pm1, executor1, "专利修改与提交", "HIGH", null, null, true, true, 40);
        // 进行中、无计划日期：仅进本周进展，不进下周计划
        seedTask(pm1, executor1, "接口联调", "URGENT", null, null, true, false, 55);
        // 下周计划：预计完成 = 下周一
        seedTask(pm1, executor1, "数据迁移", "MEDIUM", nextStart, null, false, false, 0);
        // 下周计划：无预计完成，截止 = 下周三 兜底
        seedTask(pm1, executor1, "文档评审", "MEDIUM", null, nextStart.plusDays(2), false, false, 0);
        // 预计完成超出下周末：不回退截止时间，排除
        seedTask(pm1, executor1, "远期重构", "MEDIUM", nextStart.plusDays(7), nextStart, false, false, 0);
    }

    // ================= 3. 生成草稿 =================

    @Test
    @Order(3)
    void generate_byPm() {
        String pm1 = TOKENS.get("pm1");
        MvcResult result = call("POST", "/api/weekly-reports/generate", pm1,
                "{\"projectId\":\"" + IDS.get("projectAId")
                        + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_END.format(DATE) + "\"}");
        assertEquals(0, code(result), json(result));

        // 本周进展 = 周期内完成（专利修改 100%）+ 进度>0 的未完成任务（接口联调 55）
        assertEquals(2, ((List<?>) JsonPath.read(json(result), "$.data.content")).size());
        assertEquals("专利修改与提交", JsonPath.read(json(result), "$.data.content[0].text"));
        assertEquals(100, ((Number) JsonPath.read(json(result), "$.data.content[0].progress")).intValue());
        assertEquals("执行人小王", JsonPath.read(json(result), "$.data.content[0].person"));
        assertEquals("接口联调", JsonPath.read(json(result), "$.data.content[1].text"));
        assertEquals(55, ((Number) JsonPath.read(json(result), "$.data.content[1].progress")).intValue());

        // 下周计划 = 全部未完成任务目标 100%，按队列顺序（URGENT 在前，其余按分发顺序）
        assertEquals(4, ((List<?>) JsonPath.read(json(result), "$.data.planContent")).size());
        assertEquals("接口联调", JsonPath.read(json(result), "$.data.planContent[0].text"));
        assertEquals(100, ((Number) JsonPath.read(json(result), "$.data.planContent[0].progress")).intValue());
        assertEquals("数据迁移", JsonPath.read(json(result), "$.data.planContent[1].text"));
        assertEquals("文档评审", JsonPath.read(json(result), "$.data.planContent[2].text"));
        assertEquals("远期重构", JsonPath.read(json(result), "$.data.planContent[3].text"));

        // 无权限：观察者 / 项目外人员
        assertEquals(2001, code(call("POST", "/api/weekly-reports/generate", TOKENS.get("observer1"),
                "{\"projectId\":\"" + IDS.get("projectAId")
                        + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_END.format(DATE) + "\"}")));
        assertEquals(2001, code(call("POST", "/api/weekly-reports/generate", TOKENS.get("outsider1"),
                "{\"projectId\":\"" + IDS.get("projectAId")
                        + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_END.format(DATE) + "\"}")));

        // 周期倒置 / 跨度 32 天 → 4001
        assertEquals(4001, code(call("POST", "/api/weekly-reports/generate", pm1,
                "{\"projectId\":\"" + IDS.get("projectAId")
                        + "\",\"periodStart\":\"" + PERIOD_END.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_START.format(DATE) + "\"}")));
        assertEquals(4001, code(call("POST", "/api/weekly-reports/generate", pm1,
                "{\"projectId\":\"" + IDS.get("projectAId")
                        + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_START.plusDays(31).format(DATE) + "\"}")));
    }

    // ================= 4. 发布与编辑 =================

    @Test
    @Order(4)
    void create_andUpdate() {
        String pm1 = TOKENS.get("pm1");
        String periodBody = "\"projectId\":\"" + IDS.get("projectAId")
                + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                + "\",\"periodEnd\":\"" + PERIOD_END.format(DATE) + "\"";

        // 人工编辑条目后发布（结构化条目快照入库）
        MvcResult created = call("POST", "/api/weekly-reports", pm1, "{" + periodBody
                + ",\"content\":[{\"text\":\"专利修改与提交\",\"progress\":100,\"person\":\"执行人小王\"},"
                + "{\"text\":\"系统外专项工作\"}],\"planContent\":[]}");
        assertEquals(0, code(created), json(created));
        String reportId = JsonPath.read(json(created), "$.data.id");
        IDS.put("reportAId", reportId);
        assertEquals("周报测试项目A", JsonPath.read(json(created), "$.data.projectName"));
        List<?> storedContent = JSON.parseArray((String) JsonPath.read(json(created), "$.data.content"));
        assertEquals(2, storedContent.size());
        Map<?, ?> firstEntry = (Map<?, ?>) storedContent.get(0);
        assertEquals("专利修改与提交", firstEntry.get("text"));
        assertEquals(100, ((Number) firstEntry.get("progress")).intValue());
        assertEquals("执行人小王", firstEntry.get("person"));
        assertEquals("系统外专项工作", ((Map<?, ?>) storedContent.get(1)).get("text"));
        assertEquals("[]", JsonPath.read(json(created), "$.data.planContent"));

        // 同项目同周期重复发布 → 4002
        assertEquals(4002, code(call("POST", "/api/weekly-reports", pm1, "{" + periodBody
                + ",\"content\":[],\"planContent\":[]}")));

        // 条目超限：任务说明 501 字符 / 101 条 → 4001
        assertEquals(4001, code(call("POST", "/api/weekly-reports", pm1, "{" + periodBody
                + ",\"content\":[{\"text\":\"" + "长".repeat(501) + "\"}],\"planContent\":[]}")));
        StringBuilder tooMany = new StringBuilder("[");
        for (int i = 0; i < 101; i++) {
            tooMany.append("{\"text\":\"条目").append(i).append("\"},");
        }
        tooMany.setCharAt(tooMany.length() - 1, ']');
        assertEquals(4001, code(call("POST", "/api/weekly-reports", pm1, "{" + periodBody
                + ",\"content\":" + tooMany + ",\"planContent\":[]}")));

        // 编辑：仅改条目，周期不变
        MvcResult updated = call("PUT", "/api/weekly-reports/" + reportId, pm1,
                "{\"content\":[{\"text\":\"专利修改与提交\",\"progress\":100,\"person\":\"执行人小王\"}],"
                        + "\"planContent\":[{\"text\":\"下周专项计划\"}]}");
        assertEquals(0, code(updated), json(updated));
        List<?> updatedContent = JSON.parseArray((String) JsonPath.read(json(updated), "$.data.content"));
        assertEquals(1, updatedContent.size());
        assertEquals("专利修改与提交", ((Map<?, ?>) updatedContent.get(0)).get("text"));
        List<?> updatedPlan = JSON.parseArray((String) JsonPath.read(json(updated), "$.data.planContent"));
        assertEquals(1, updatedPlan.size());
        assertEquals("下周专项计划", ((Map<?, ?>) updatedPlan.get(0)).get("text"));

        // 编辑/删除不存在的周报 → 3010
        assertEquals(3010, code(call("PUT", "/api/weekly-reports/not-exist-id", pm1,
                "{\"content\":[],\"planContent\":[]}")));
        assertEquals(3010, code(call("DELETE", "/api/weekly-reports/not-exist-id", pm1, null)));
    }

    // ================= 5. 跨项目列表 =================

    @Test
    @Order(5)
    void list_visibleProjectsAndOrder() {
        String pm1 = TOKENS.get("pm1");

        // 项目 B 发布同周期周报：pm1 双项目成员 → 两条，同周期按项目名升序
        MvcResult createdB = call("POST", "/api/weekly-reports", pm1,
                "{\"projectId\":\"" + IDS.get("projectBId")
                        + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_END.format(DATE)
                        + "\",\"content\":[],\"planContent\":[]}");
        assertEquals(0, code(createdB), json(createdB));
        IDS.put("reportBId", JsonPath.read(json(createdB), "$.data.id"));

        MvcResult list = call("GET", "/api/weekly-reports", pm1);
        assertEquals(0, code(list), json(list));
        List<Map<String, Object>> data = listData(list);
        assertEquals(2, data.size());
        assertEquals("周报测试项目A", data.get(0).get("projectName"));
        assertEquals("周报测试项目B", data.get(1).get("projectName"));
        assertEquals(PERIOD_START.format(DATE), data.get(0).get("periodStart"));

        // 仅 view 权限：可见列表，不可写
        MvcResult executorList = call("GET", "/api/weekly-reports", TOKENS.get("executor1"));
        assertEquals(0, code(executorList), json(executorList));
        assertEquals(1, listData(executorList).size());
        assertEquals(2001, code(call("PUT", "/api/weekly-reports/" + IDS.get("reportAId"),
                TOKENS.get("executor1"), "{\"content\":[],\"planContent\":[]}")));
        assertEquals(2001, code(call("DELETE", "/api/weekly-reports/" + IDS.get("reportAId"),
                TOKENS.get("observer1"), null)));

        // 项目外人员：列表为空数组，写接口 2001
        MvcResult outsiderList = call("GET", "/api/weekly-reports", TOKENS.get("outsider1"));
        assertEquals(0, code(outsiderList), json(outsiderList));
        assertTrue(listData(outsiderList).isEmpty());
        assertEquals(2001, code(call("POST", "/api/weekly-reports", TOKENS.get("outsider1"),
                "{\"projectId\":\"" + IDS.get("projectAId")
                        + "\",\"periodStart\":\"" + PERIOD_START.format(DATE)
                        + "\",\"periodEnd\":\"" + PERIOD_END.format(DATE)
                        + "\",\"content\":[],\"planContent\":[]}")));
    }

    // ================= 6. 删除 =================

    @Test
    @Order(6)
    void delete_byPm() {
        String pm1 = TOKENS.get("pm1");
        String url = "/api/weekly-reports/" + IDS.get("reportAId");

        assertEquals(0, code(call("DELETE", url, pm1, null)));
        MvcResult list = call("GET", "/api/weekly-reports", pm1);
        assertEquals(1, listData(list).size());
        assertEquals("周报测试项目B", listData(list).get(0).get("projectName"));

        // 软删后重复删除 → 3010
        assertEquals(3010, code(call("DELETE", url, pm1, null)));
        assertFalse(listData(list).stream().anyMatch(m -> IDS.get("reportAId").equals(m.get("id"))));
    }
}
