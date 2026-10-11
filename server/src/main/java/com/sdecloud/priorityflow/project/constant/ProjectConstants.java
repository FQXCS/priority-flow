package com.sdecloud.priorityflow.project.constant;

import com.sdecloud.priorityflow.common.permission.PermissionConstants;

import java.util.List;

/**
 * 项目域常量：内置角色编码、默认权限集、状态。
 *
 * @author priority-flow
 */
public final class ProjectConstants {

    /** 内置角色：项目分发者（权限全集，不可编辑/删除） */
    public static final String ROLE_DISTRIBUTOR = "DISTRIBUTOR";

    /** 内置角色：项目经理（权限集可由分发者编辑） */
    public static final String ROLE_PM = "PM";

    /** 内置角色：执行人员 */
    public static final String ROLE_EXECUTOR = "EXECUTOR";

    /** 内置角色：观察者 */
    public static final String ROLE_OBSERVER = "OBSERVER";

    /** 项目状态：正常 */
    public static final String PROJECT_STATUS_NORMAL = "0";

    /** 项目状态：归档 */
    public static final String PROJECT_STATUS_ARCHIVED = "1";

    /** 分发者权限全集 */
    public static final List<String> DISTRIBUTOR_PERMISSIONS = List.of(
            PermissionConstants.TASK_CREATE_DRAFT,
            PermissionConstants.TASK_PUBLISH,
            PermissionConstants.TASK_VIEW_PROJECT,
            PermissionConstants.TASK_VIEW_WORKLOAD,
            PermissionConstants.TASK_VIEW_MASKED,
            PermissionConstants.TASK_DISTRIBUTE,
            PermissionConstants.TASK_CHANGE_PRIORITY,
            PermissionConstants.TASK_DRAG_SORT,
            PermissionConstants.TASK_DRAG_CROSS,
            PermissionConstants.TASK_START,
            PermissionConstants.TASK_SWITCH_BACK,
            PermissionConstants.TASK_COMPLETE,
            PermissionConstants.TASK_CLOSE,
            PermissionConstants.TASK_REOPEN,
            PermissionConstants.COMMENT_CREATE,
            PermissionConstants.ATTACHMENT_UPLOAD,
            PermissionConstants.MEMBER_MANAGE,
            PermissionConstants.ROLE_MANAGE,
            PermissionConstants.LOG_VIEW
    );

    /** 项目经理默认权限集（分发/关闭等可由分发者调整） */
    public static final List<String> PM_PERMISSIONS = List.of(
            PermissionConstants.TASK_CREATE_DRAFT,
            PermissionConstants.TASK_PUBLISH,
            PermissionConstants.TASK_VIEW_PROJECT,
            PermissionConstants.TASK_VIEW_WORKLOAD,
            PermissionConstants.TASK_VIEW_MASKED,
            PermissionConstants.TASK_DISTRIBUTE,
            PermissionConstants.TASK_CHANGE_PRIORITY,
            PermissionConstants.TASK_DRAG_SORT,
            PermissionConstants.TASK_DRAG_CROSS,
            PermissionConstants.TASK_CLOSE,
            PermissionConstants.TASK_REOPEN,
            PermissionConstants.COMMENT_CREATE,
            PermissionConstants.ATTACHMENT_UPLOAD,
            PermissionConstants.LOG_VIEW
    );

    /** 执行人员默认权限集 */
    public static final List<String> EXECUTOR_PERMISSIONS = List.of(
            PermissionConstants.TASK_VIEW_PROJECT,
            PermissionConstants.TASK_START,
            PermissionConstants.TASK_SWITCH_BACK,
            PermissionConstants.TASK_COMPLETE,
            PermissionConstants.TASK_DRAG_SORT,
            PermissionConstants.COMMENT_CREATE,
            PermissionConstants.ATTACHMENT_UPLOAD
    );

    /** 观察者默认权限集 */
    public static final List<String> OBSERVER_PERMISSIONS = List.of(
            PermissionConstants.TASK_VIEW_PROJECT,
            PermissionConstants.TASK_VIEW_MASKED,
            PermissionConstants.LOG_VIEW
    );

    private ProjectConstants() {
    }
}
