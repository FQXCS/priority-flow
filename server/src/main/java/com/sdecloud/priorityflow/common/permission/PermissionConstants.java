package com.sdecloud.priorityflow.common.permission;

/**
 * 权限点常量（前后端同一套，前端登录后缓存控制按钮显隐）。
 *
 * @author priority-flow
 */
public final class PermissionConstants {

    /** 创建草稿 */
    public static final String TASK_CREATE_DRAFT = "pf:task:createDraft";
    /** 发布任务 */
    public static final String TASK_PUBLISH = "pf:task:publish";
    /** 查看项目任务 */
    public static final String TASK_VIEW_PROJECT = "pf:task:viewProject";
    /** 查看人员当前任务 */
    public static final String TASK_VIEW_WORKLOAD = "pf:task:viewWorkload";
    /** 查看跨项目脱敏 */
    public static final String TASK_VIEW_MASKED = "pf:task:viewMasked";
    /** 分发任务 */
    public static final String TASK_DISTRIBUTE = "pf:task:distribute";
    /** 修改优先级 */
    public static final String TASK_CHANGE_PRIORITY = "pf:task:changePriority";
    /** 拖拽排序（仅限同优先级桶内） */
    public static final String TASK_DRAG_SORT = "pf:task:dragSort";
    /** 跨执行人队列拖拽（仍限同优先级桶内） */
    public static final String TASK_DRAG_CROSS = "pf:task:dragCross";
    /** 开始任务 */
    public static final String TASK_START = "pf:task:start";
    /** 切换回等待中 */
    public static final String TASK_SWITCH_BACK = "pf:task:switchBack";
    /** 完成任务 */
    public static final String TASK_COMPLETE = "pf:task:complete";
    /** 关闭任务 */
    public static final String TASK_CLOSE = "pf:task:close";
    /** 重新打开 */
    public static final String TASK_REOPEN = "pf:task:reopen";
    /** 评论 */
    public static final String COMMENT_CREATE = "pf:comment:create";
    /** 上传附件 */
    public static final String ATTACHMENT_UPLOAD = "pf:attachment:upload";
    /** 管理项目成员 */
    public static final String MEMBER_MANAGE = "pf:member:manage";
    /** 管理项目角色 */
    public static final String ROLE_MANAGE = "pf:role:manage";
    /** 查看操作日志 */
    public static final String LOG_VIEW = "pf:log:view";

    private PermissionConstants() {
    }
}
