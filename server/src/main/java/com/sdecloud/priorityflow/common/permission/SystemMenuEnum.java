package com.sdecloud.priorityflow.common.permission;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 系统菜单枚举：侧边栏菜单与权限点的归属目录（前后端菜单权限控制的单一事实源）。
 *
 * <p>规则：</p>
 * <ul>
 * <li>每个菜单声明「准入权限」：用户权限并集命中任一准入权限即显示该菜单；准入权限为空 = 登录即可见；</li>
 * <li>每个菜单声明「可配置权限点」：角色编辑界面按「菜单 → 权限点」树渲染，菜单下权限点全量授权后菜单必然可见；</li>
 * <li>菜单下权限点全集必须恰好覆盖 {@link PermissionConstants} 全部常量（由单测保证不重不漏）。</li>
 * </ul>
 *
 * @author priority-flow
 */
public enum SystemMenuEnum {

    /** 看板：任务全生命周期操作 */
    BOARD("board", "看板", 1,
            List.of(PermissionConstants.TASK_VIEW_PROJECT),
            List.of(
                    new PermissionDefinition(PermissionConstants.TASK_VIEW_PROJECT, "查看项目任务"),
                    new PermissionDefinition(PermissionConstants.TASK_PUBLISH, "发布任务"),
                    new PermissionDefinition(PermissionConstants.TASK_DISTRIBUTE, "分发任务"),
                    new PermissionDefinition(PermissionConstants.TASK_CHANGE_PRIORITY, "修改优先级"),
                    new PermissionDefinition(PermissionConstants.TASK_DRAG_SORT, "拖拽排序"),
                    new PermissionDefinition(PermissionConstants.TASK_DRAG_CROSS, "跨执行人拖拽"),
                    new PermissionDefinition(PermissionConstants.TASK_CLOSE, "关闭任务"),
                    new PermissionDefinition(PermissionConstants.TASK_REOPEN, "重新打开"),
                    new PermissionDefinition(PermissionConstants.COMMENT_CREATE, "评论"),
                    new PermissionDefinition(PermissionConstants.ATTACHMENT_UPLOAD, "上传附件"),
                    new PermissionDefinition(PermissionConstants.LOG_VIEW, "查看操作日志"))),

    /** 项目规划：里程碑时间轴（阶段目标，任务关联二期） */
    PLAN("plan", "项目规划", 2,
            List.of(PermissionConstants.MILESTONE_VIEW),
            List.of(
                    new PermissionDefinition(PermissionConstants.MILESTONE_VIEW, "查看项目规划"),
                    new PermissionDefinition(PermissionConstants.MILESTONE_MANAGE, "管理项目规划"))),

    /** 我的任务：执行人对被分发任务的操作 */
    MY_TASK("myTask", "我的任务", 3,
            List.of(PermissionConstants.TASK_START),
            List.of(
                    new PermissionDefinition(PermissionConstants.TASK_START, "开始任务"),
                    new PermissionDefinition(PermissionConstants.TASK_SWITCH_BACK, "切换回等待中"),
                    new PermissionDefinition(PermissionConstants.TASK_COMPLETE, "完成任务"))),

    /** 我的草稿：草稿归属人管理（创建草稿即草稿能力） */
    MY_DRAFT("myDraft", "我的草稿", 4,
            List.of(PermissionConstants.TASK_CREATE_DRAFT),
            List.of(new PermissionDefinition(PermissionConstants.TASK_CREATE_DRAFT, "创建草稿"))),

    /** 人员负载：项目内负载与跨项目脱敏负载 */
    WORKLOAD("workload", "人员负载", 5,
            List.of(PermissionConstants.TASK_VIEW_WORKLOAD, PermissionConstants.TASK_VIEW_MASKED),
            List.of(
                    new PermissionDefinition(PermissionConstants.TASK_VIEW_WORKLOAD, "查看人员当前任务"),
                    new PermissionDefinition(PermissionConstants.TASK_VIEW_MASKED, "查看跨项目脱敏"))),

    /** 通知中心：登录即可见，无权限点 */
    NOTIFICATION("notification", "通知中心", 6, List.of(), List.of()),

    /** 设置：登录即可见（个人设置），成员/角色管理权限点挂在其下配置 */
    SETTINGS("settings", "设置", 7,
            List.of(),
            List.of(
                    new PermissionDefinition(PermissionConstants.MEMBER_MANAGE, "管理项目成员"),
                    new PermissionDefinition(PermissionConstants.ROLE_MANAGE, "管理项目角色")));

    /** 菜单编码（前端路由守卫与侧边栏渲染使用） */
    @Getter
    private final String code;

    /** 菜单中文名称 */
    @Getter
    private final String name;

    /** 显示顺序 */
    @Getter
    private final int sortNo;

    /** 准入权限：权限并集命中任一即显示该菜单；为空 = 登录即可见 */
    @Getter
    private final List<String> accessPermissions;

    /** 菜单下可配置的权限点（角色编辑树展示顺序） */
    @Getter
    private final List<PermissionDefinition> permissions;

    SystemMenuEnum(String code, String name, int sortNo,
                   List<String> accessPermissions, List<PermissionDefinition> permissions) {
        this.code = code;
        this.name = name;
        this.sortNo = sortNo;
        this.accessPermissions = accessPermissions;
        this.permissions = permissions;
    }

    /**
     * 判断菜单对给定权限并集是否可见。
     *
     * @param permissionUnion 用户在当前项目下的权限并集
     * @return true = 可见（准入权限为空视为登录即可见）
     */
    public boolean isVisible(Set<String> permissionUnion) {
        return accessPermissions.isEmpty()
                || accessPermissions.stream().anyMatch(permissionUnion::contains);
    }

    /**
     * 按显示顺序返回对权限并集可见的菜单编码列表。
     *
     * @param permissionUnion 用户在当前项目下的权限并集
     * @return 可见菜单编码列表（如 board/myTask/...）
     */
    public static List<String> visibleMenuCodes(Set<String> permissionUnion) {
        return Arrays.stream(values())
                .filter(menu -> menu.isVisible(permissionUnion))
                .map(SystemMenuEnum::getCode)
                .toList();
    }

    /**
     * 全部菜单编码（按显示顺序，系统管理员全量可见时使用）。
     *
     * @return 全部菜单编码列表
     */
    public static List<String> allMenuCodes() {
        return Arrays.stream(values()).map(SystemMenuEnum::getCode).toList();
    }

    /**
     * 全量权限码（全部菜单权限点并集去重；菜单权限点全集由单测保证恰好覆盖 PermissionConstants 全部常量）。
     *
     * @return 全部权限点编码列表
     */
    public static List<String> allPermissionCodes() {
        return Arrays.stream(values())
                .flatMap(menu -> menu.getPermissions().stream())
                .map(PermissionDefinition::getCode)
                .distinct()
                .toList();
    }
}
