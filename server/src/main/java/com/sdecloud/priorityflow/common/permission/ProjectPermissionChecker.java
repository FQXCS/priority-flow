package com.sdecloud.priorityflow.common.permission;

import java.util.List;

/**
 * 项目权限解析器（common 定义接口，project 域实现，避免 common 反向依赖业务域）。
 *
 * @author priority-flow
 */
public interface ProjectPermissionChecker {

    /**
     * 校验用户在项目中是否拥有指定权限点（多角色权限并集）
     *
     * @param userId         用户 ID
     * @param projectId      项目 ID
     * @param permissionCode 权限点编码
     * @return true=有权限
     */
    boolean hasPermission(String userId, String projectId, String permissionCode);

    /**
     * 查询用户在项目中的权限并集（非成员返回空集）
     *
     * @param userId    用户 ID
     * @param projectId 项目 ID
     * @return 权限点集合
     */
    List<String> unionPermissions(String userId, String projectId);

    /**
     * 判断用户是否项目成员（WS 订阅鉴权等可见性场景）
     */
    boolean isProjectMember(String userId, String projectId);
}
