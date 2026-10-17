package com.sdecloud.priorityflow.project.service.impl;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.common.permission.SystemMenuEnum;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberRoleDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRoleRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectRoleRepository;
import com.sdecloud.priorityflow.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 项目权限解析器实现：多角色权限并集；系统管理员不参与具体项目，视为全项目全量权限。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class ProjectPermissionCheckerImpl implements ProjectPermissionChecker {

    private final SysUserRepository sysUserRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectMemberRoleRepository projectMemberRoleRepository;

    private final ProjectRoleRepository projectRoleRepository;

    @Override
    public boolean hasPermission(String userId, String projectId, String permissionCode) {
        return unionPermissions(userId, projectId).contains(permissionCode);
    }

    @Override
    public List<String> unionPermissions(String userId, String projectId) {
        SysUserDo user = sysUserRepository.getById(userId);
        if (user != null && "1".equals(user.getIsAdmin())) {
            // 系统管理员：全部项目的全量权限（成员/角色管理、任务流转等）
            return SystemMenuEnum.allPermissionCodes();
        }
        if (!isProjectMember(userId, projectId)) {
            return List.of();
        }
        List<ProjectMemberRoleDo> bindings = projectMemberRoleRepository.listByProjectAndUser(projectId, userId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<ProjectRoleDo> roles = projectRoleRepository.listByIds(
                bindings.stream().map(ProjectMemberRoleDo::getRoleId).distinct().toList());
        Set<String> union = new HashSet<>();
        roles.forEach(role -> union.addAll(JsonUtils.parseStringList(role.getPermissions())));
        return List.copyOf(union);
    }

    @Override
    public boolean isProjectMember(String userId, String projectId) {
        ProjectMemberDo member = projectMemberRepository.findMember(projectId, userId);
        return member != null;
    }
}
