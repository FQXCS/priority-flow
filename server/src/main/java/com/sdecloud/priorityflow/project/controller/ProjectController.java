package com.sdecloud.priorityflow.project.controller;

import com.sdecloud.priorityflow.auth.bean.vo.UserVo;
import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.RequireProjectPermission;
import com.sdecloud.priorityflow.project.bean.dto.MemberAddDto;
import com.sdecloud.priorityflow.project.bean.dto.MemberRoleUpdateDto;
import com.sdecloud.priorityflow.project.bean.dto.ProjectCreateDto;
import com.sdecloud.priorityflow.project.bean.dto.RoleSaveDto;
import com.sdecloud.priorityflow.project.bean.vo.ProjectMemberVo;
import com.sdecloud.priorityflow.project.bean.vo.ProjectVo;
import com.sdecloud.priorityflow.project.bean.vo.RoleVo;
import com.sdecloud.priorityflow.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目/成员/角色接口。
 *
 * @author priority-flow
 */
@Tag(name = "项目", description = "项目、成员、角色权限管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "我的项目列表")
    @GetMapping
    public R<List<ProjectVo>> listMy() {
        return R.ok(projectService.listMyProjects());
    }

    @Operation(summary = "创建项目", description = "系统管理员专用，创建时指定第一任项目分发者")
    @PostMapping
    public R<ProjectVo> create(@Valid @RequestBody ProjectCreateDto dto) {
        return R.ok(projectService.createProject(dto));
    }

    @Operation(summary = "全部项目", description = "系统管理视图，管理员专用")
    @GetMapping("/all")
    public R<List<ProjectVo>> listAll() {
        return R.ok(projectService.listAllProjects());
    }

    @Operation(summary = "项目详情")
    @GetMapping("/{projectId}")
    public R<ProjectVo> detail(@PathVariable String projectId) {
        return R.ok(projectService.getProject(projectId));
    }

    // ---------------- 成员 ----------------

    @Operation(summary = "项目成员列表")
    @GetMapping("/{projectId}/members")
    public R<List<ProjectMemberVo>> listMembers(@PathVariable String projectId) {
        return R.ok(projectService.listMembers(projectId));
    }

    @Operation(summary = "成员候选列表", description = "可添加为项目成员的系统用户，排除已有成员与禁用用户")
    @RequireProjectPermission(PermissionConstants.MEMBER_MANAGE)
    @GetMapping("/{projectId}/member-candidates")
    public R<List<UserVo>> memberCandidates(@PathVariable String projectId) {
        return R.ok(projectService.listMemberCandidates(projectId));
    }

    @Operation(summary = "添加项目成员")
    @RequireProjectPermission(PermissionConstants.MEMBER_MANAGE)
    @PostMapping("/{projectId}/members")
    public R<Void> addMember(@PathVariable String projectId, @Valid @RequestBody MemberAddDto dto) {
        projectService.addMember(projectId, dto);
        return R.ok();
    }

    @Operation(summary = "移除项目成员")
    @RequireProjectPermission(PermissionConstants.MEMBER_MANAGE)
    @DeleteMapping("/{projectId}/members/{userId}")
    public R<Void> removeMember(@PathVariable String projectId, @PathVariable String userId) {
        projectService.removeMember(projectId, userId);
        return R.ok();
    }

    @Operation(summary = "更新成员角色绑定", description = "全量替换，成员可绑多角色，权限取并集")
    @RequireProjectPermission(PermissionConstants.MEMBER_MANAGE)
    @PutMapping("/{projectId}/members/{userId}/roles")
    public R<Void> updateMemberRoles(@PathVariable String projectId, @PathVariable String userId,
                                     @Valid @RequestBody MemberRoleUpdateDto dto) {
        projectService.updateMemberRoles(projectId, userId, dto);
        return R.ok();
    }

    // ---------------- 角色 ----------------

    @Operation(summary = "项目角色列表")
    @GetMapping("/{projectId}/roles")
    public R<List<RoleVo>> listRoles(@PathVariable String projectId) {
        return R.ok(projectService.listRoles(projectId));
    }

    @Operation(summary = "创建自定义角色", description = "权限集须为分发者全集的子集")
    @RequireProjectPermission(PermissionConstants.ROLE_MANAGE)
    @PostMapping("/{projectId}/roles")
    public R<RoleVo> createRole(@PathVariable String projectId, @Valid @RequestBody RoleSaveDto dto) {
        return R.ok(projectService.createRole(projectId, dto));
    }

    @Operation(summary = "更新角色", description = "内置 DISTRIBUTOR 不可编辑")
    @RequireProjectPermission(PermissionConstants.ROLE_MANAGE)
    @PutMapping("/{projectId}/roles/{roleId}")
    public R<RoleVo> updateRole(@PathVariable String projectId, @PathVariable String roleId,
                                @Valid @RequestBody RoleSaveDto dto) {
        return R.ok(projectService.updateRole(projectId, roleId, dto));
    }

    @Operation(summary = "删除角色", description = "仅自定义角色可删")
    @RequireProjectPermission(PermissionConstants.ROLE_MANAGE)
    @DeleteMapping("/{projectId}/roles/{roleId}")
    public R<Void> deleteRole(@PathVariable String projectId, @PathVariable String roleId) {
        projectService.deleteRole(projectId, roleId);
        return R.ok();
    }
}
