package com.sdecloud.priorityflow.project.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberRoleDo;

import java.util.List;

/**
 * 成员角色绑定仓库。
 *
 * @author priority-flow
 */
public interface ProjectMemberRoleRepository extends IService<ProjectMemberRoleDo> {

    /**
     * 查询用户在项目中的全部角色绑定
     */
    List<ProjectMemberRoleDo> listByProjectAndUser(String projectId, String userId);

    /**
     * 查询项目内拥有指定角色的全部用户 ID
     */
    List<String> listUserIdsByRole(String projectId, String roleId);

    /**
     * 查询项目内全部绑定
     */
    List<ProjectMemberRoleDo> listByProjectId(String projectId);

    /**
     * 绑定角色（含软删除复活）
     */
    void bindRole(String projectId, String userId, String roleId);

    /**
     * 移除用户在项目中的全部角色绑定
     */
    void removeAllByUser(String projectId, String userId);

    /**
     * 移除角色全部绑定（删除角色时）
     */
    void removeAllByRole(String roleId);
}
