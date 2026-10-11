package com.sdecloud.priorityflow.project.service;

import com.sdecloud.priorityflow.auth.bean.vo.UserVo;
import com.sdecloud.priorityflow.project.bean.dto.MemberAddDto;
import com.sdecloud.priorityflow.project.bean.dto.MemberRoleUpdateDto;
import com.sdecloud.priorityflow.project.bean.dto.ProjectCreateDto;
import com.sdecloud.priorityflow.project.bean.dto.RoleSaveDto;
import com.sdecloud.priorityflow.project.bean.vo.ProjectMemberVo;
import com.sdecloud.priorityflow.project.bean.vo.ProjectVo;
import com.sdecloud.priorityflow.project.bean.vo.RoleVo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;

import java.util.List;

/**
 * 项目/成员/角色服务。
 *
 * @author priority-flow
 */
public interface ProjectService {

    /**
     * 创建项目（管理员专用）：同时初始化 4 个内置角色并绑定第一任分发者
     */
    ProjectVo createProject(ProjectCreateDto dto);

    /**
     * 我参与的项目
     */
    List<ProjectVo> listMyProjects();

    /**
     * 全部项目（系统管理，管理员专用）
     */
    List<ProjectVo> listAllProjects();

    /**
     * 项目详情（成员可见）
     */
    ProjectVo getProject(String projectId);

    /**
     * 项目成员列表（含用户信息与角色）
     */
    List<ProjectMemberVo> listMembers(String projectId);

    /**
     * 可添加为项目成员的用户候选列表（排除已有成员与禁用用户）
     */
    List<UserVo> listMemberCandidates(String projectId);

    /**
     * 添加项目成员
     */
    void addMember(String projectId, MemberAddDto dto);

    /**
     * 移除项目成员（同时清理角色绑定）
     */
    void removeMember(String projectId, String userId);

    /**
     * 更新成员角色绑定（全量替换）
     */
    void updateMemberRoles(String projectId, String userId, MemberRoleUpdateDto dto);

    /**
     * 项目角色列表
     */
    List<RoleVo> listRoles(String projectId);

    /**
     * 创建自定义角色（权限集须为分发者全集的子集）
     */
    RoleVo createRole(String projectId, RoleSaveDto dto);

    /**
     * 更新角色（内置 DISTRIBUTOR 不可编辑；内置角色仅可改权限集与名称）
     */
    RoleVo updateRole(String projectId, String roleId, RoleSaveDto dto);

    /**
     * 删除角色（仅自定义角色可删，同时清理绑定）
     */
    void deleteRole(String projectId, String roleId);
}
