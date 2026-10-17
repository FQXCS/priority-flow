package com.sdecloud.priorityflow.project.service.impl;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberRoleDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRoleRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 权限切面支撑测试：多角色并集、非成员拒绝、系统管理员全量放行。
 *
 * @author priority-flow
 */
@ExtendWith(MockitoExtension.class)
class ProjectPermissionCheckerImplTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectMemberRoleRepository projectMemberRoleRepository;

    @Mock
    private ProjectRoleRepository projectRoleRepository;

    @InjectMocks
    private ProjectPermissionCheckerImpl checker;

    private ProjectRoleDo role(String id, String code, List<String> permissions) {
        ProjectRoleDo role = new ProjectRoleDo();
        role.setId(id);
        role.setRoleCode(code);
        role.setPermissions(String.join("\",\"", permissions).isBlank() ? "[]"
                : "[\"" + String.join("\",\"", permissions) + "\"]");
        return role;
    }

    @Test
    void multiRolePermissionsShouldUnion() {
        when(projectMemberRepository.findMember("p1", "u1")).thenReturn(new ProjectMemberDo());
        ProjectMemberRoleDo b1 = new ProjectMemberRoleDo();
        b1.setRoleId("r1");
        ProjectMemberRoleDo b2 = new ProjectMemberRoleDo();
        b2.setRoleId("r2");
        when(projectMemberRoleRepository.listByProjectAndUser("p1", "u1")).thenReturn(List.of(b1, b2));
        when(projectRoleRepository.listByIds(any())).thenReturn(List.of(
                role("r1", "EXECUTOR", List.of(PermissionConstants.TASK_START, PermissionConstants.TASK_COMPLETE)),
                role("r2", "PM", List.of(PermissionConstants.TASK_PUBLISH, PermissionConstants.TASK_COMPLETE))));

        List<String> union = checker.unionPermissions("u1", "p1");

        assertTrue(union.contains(PermissionConstants.TASK_START));
        assertTrue(union.contains(PermissionConstants.TASK_PUBLISH));
        assertTrue(union.contains(PermissionConstants.TASK_COMPLETE));
    }

    @Test
    void nonMemberShouldHaveNoPermission() {
        when(projectMemberRepository.findMember("p1", "u9")).thenReturn(null);
        assertTrue(checker.unionPermissions("u9", "p1").isEmpty());
        assertFalse(checker.hasPermission("u9", "p1", PermissionConstants.TASK_VIEW_PROJECT));
        assertFalse(checker.isProjectMember("u9", "p1"));
    }

    @Test
    void memberWithoutRolesShouldHaveNoPermission() {
        when(projectMemberRepository.findMember("p1", "u1")).thenReturn(new ProjectMemberDo());
        when(projectMemberRoleRepository.listByProjectAndUser("p1", "u1")).thenReturn(List.of());
        assertTrue(checker.unionPermissions("u1", "p1").isEmpty());
    }

    @Test
    void adminShouldHaveAllPermissionsWithoutMembership() {
        SysUserDo admin = new SysUserDo();
        admin.setIsAdmin("1");
        when(sysUserRepository.getById("ua")).thenReturn(admin);

        List<String> union = checker.unionPermissions("ua", "p1");

        assertTrue(union.contains(PermissionConstants.MEMBER_MANAGE));
        assertTrue(union.contains(PermissionConstants.ROLE_MANAGE));
        assertTrue(union.contains(PermissionConstants.TASK_VIEW_PROJECT));
        assertTrue(checker.hasPermission("ua", "p1", PermissionConstants.MEMBER_MANAGE));
    }

    @Test
    void nonAdminStillRequiresMembership() {
        SysUserDo normal = new SysUserDo();
        normal.setIsAdmin("0");
        when(sysUserRepository.getById("u9")).thenReturn(normal);
        when(projectMemberRepository.findMember("p1", "u9")).thenReturn(null);

        assertFalse(checker.hasPermission("u9", "p1", PermissionConstants.MEMBER_MANAGE));
    }
}
