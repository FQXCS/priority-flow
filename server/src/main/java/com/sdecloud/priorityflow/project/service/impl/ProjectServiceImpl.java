package com.sdecloud.priorityflow.project.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.auth.bean.vo.UserVo;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.util.JsonUtils;
import com.sdecloud.priorityflow.project.bean.dto.MemberAddDto;
import com.sdecloud.priorityflow.project.bean.dto.MemberRoleUpdateDto;
import com.sdecloud.priorityflow.project.bean.dto.ProjectCreateDto;
import com.sdecloud.priorityflow.project.bean.dto.RoleSaveDto;
import com.sdecloud.priorityflow.project.bean.vo.ProjectMemberVo;
import com.sdecloud.priorityflow.project.bean.vo.ProjectVo;
import com.sdecloud.priorityflow.project.bean.vo.RoleVo;
import com.sdecloud.priorityflow.project.constant.ProjectConstants;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberRoleDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRoleRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectRoleRepository;
import com.sdecloud.priorityflow.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目/成员/角色服务实现。
 * <p>创建项目时初始化 4 个内置角色并绑定第一任分发者；
 * 一个项目可绑定多个分发者（DISTRIBUTOR 权限全集、不可编辑删除）。</p>
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectInfoRepository projectInfoRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectRoleRepository projectRoleRepository;

    private final ProjectMemberRoleRepository projectMemberRoleRepository;

    private final SysUserRepository sysUserRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVo createProject(ProjectCreateDto dto) {
        requireAdmin();
        if (projectInfoRepository.findByKey(dto.getProjectKey()) != null) {
            throw new BusinessException(ResultCode.DUPLICATE, "项目编号前缀已存在：" + dto.getProjectKey());
        }
        SysUserDo distributor = sysUserRepository.getById(dto.getDistributorUserId());
        if (distributor == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "第一任项目分发者用户不存在");
        }

        ProjectInfoDo project = new ProjectInfoDo();
        project.setProjectName(dto.getProjectName());
        project.setProjectKey(dto.getProjectKey());
        project.setStatus(ProjectConstants.PROJECT_STATUS_NORMAL);
        project.setRemark(dto.getRemark());
        projectInfoRepository.save(project);

        // 初始化 4 个内置角色
        createBuiltinRole(project.getId(), "项目分发者", ProjectConstants.ROLE_DISTRIBUTOR, ProjectConstants.DISTRIBUTOR_PERMISSIONS);
        createBuiltinRole(project.getId(), "项目经理", ProjectConstants.ROLE_PM, ProjectConstants.PM_PERMISSIONS);
        createBuiltinRole(project.getId(), "执行人员", ProjectConstants.ROLE_EXECUTOR, ProjectConstants.EXECUTOR_PERMISSIONS);
        createBuiltinRole(project.getId(), "观察者", ProjectConstants.ROLE_OBSERVER, ProjectConstants.OBSERVER_PERMISSIONS);

        // 添加成员并绑定第一任分发者
        projectMemberRepository.addMember(project.getId(), distributor.getId());
        ProjectRoleDo distributorRole = projectRoleRepository.findByCode(project.getId(), ProjectConstants.ROLE_DISTRIBUTOR);
        projectMemberRoleRepository.bindRole(project.getId(), distributor.getId(), distributorRole.getId());

        log.info("创建项目：{}（{}），第一任分发者：{}", project.getProjectName(), project.getProjectKey(), distributor.getUsername());
        return toProjectVo(project, 1);
    }

    @Override
    public List<ProjectVo> listMyProjects() {
        String userId = UserContext.require().getUserId();
        return projectInfoRepository.listByMemberUserId(userId).stream()
                .map(p -> toProjectVo(p, null)).toList();
    }

    @Override
    public List<ProjectVo> listAllProjects() {
        requireAdmin();
        return projectInfoRepository.listAll().stream()
                .map(p -> toProjectVo(p, (int) projectMemberRepository.listByProjectId(p.getId()).size())).toList();
    }

    @Override
    public ProjectVo getProject(String projectId) {
        ProjectInfoDo project = requireProject(projectId);
        // 成员或系统管理员可见
        if (projectMemberRepository.findMember(projectId, UserContext.require().getUserId()) == null
                && !UserContext.require().isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非项目成员无权查看");
        }
        return toProjectVo(project, (int) projectMemberRepository.listByProjectId(projectId).size());
    }

    @Override
    public List<ProjectMemberVo> listMembers(String projectId) {
        requireProject(projectId);
        List<ProjectMemberDo> members = projectMemberRepository.listByProjectId(projectId);
        List<String> userIds = members.stream().map(ProjectMemberDo::getUserId).toList();
        Map<String, SysUserDo> userMap = userIds.isEmpty() ? Map.of()
                : sysUserRepository.listByIds(userIds).stream()
                        .collect(Collectors.toMap(SysUserDo::getId, Function.identity()));
        Map<String, List<ProjectMemberRoleDo>> bindingMap = projectMemberRoleRepository.listByProjectId(projectId)
                .stream().collect(Collectors.groupingBy(ProjectMemberRoleDo::getUserId));
        Map<String, ProjectRoleDo> roleMap = projectRoleRepository.listByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectRoleDo::getId, Function.identity()));

        return members.stream().map(member -> {
            ProjectMemberVo vo = new ProjectMemberVo();
            BeanUtils.copyProperties(member, vo);
            SysUserDo user = userMap.get(member.getUserId());
            if (user != null) {
                UserVo userVo = new UserVo();
                BeanUtils.copyProperties(user, userVo);
                vo.setUser(userVo);
            }
            vo.setRoleList(bindingMap.getOrDefault(member.getUserId(), List.of()).stream()
                    .map(b -> roleMap.get(b.getRoleId()))
                    .filter(Objects::nonNull)
                    .map(this::toRoleVo).toList());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(String projectId, MemberAddDto dto) {
        requireProject(projectId);
        SysUserDo user = sysUserRepository.getById(dto.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "用户不存在");
        }
        if (projectMemberRepository.findMember(projectId, dto.getUserId()) != null) {
            throw new BusinessException(ResultCode.DUPLICATE, "该用户已是项目成员");
        }
        projectMemberRepository.addMember(projectId, dto.getUserId());
        log.info("添加项目成员：projectId={}，userId={}", projectId, dto.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(String projectId, String userId) {
        requireProject(projectId);
        projectMemberRepository.removeMember(projectId, userId);
        projectMemberRoleRepository.removeAllByUser(projectId, userId);
        log.info("移除项目成员：projectId={}，userId={}", projectId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRoles(String projectId, String userId, MemberRoleUpdateDto dto) {
        requireProject(projectId);
        if (projectMemberRepository.findMember(projectId, userId) == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "该用户不是项目成员");
        }
        // 校验角色均属于本项目
        Set<String> projectRoleIds = projectRoleRepository.listByProjectId(projectId).stream()
                .map(ProjectRoleDo::getId).collect(Collectors.toSet());
        if (!projectRoleIds.containsAll(dto.getRoleIds())) {
            throw new BusinessException(ResultCode.BAD_PARAM, "存在不属于本项目的角色");
        }
        projectMemberRoleRepository.removeAllByUser(projectId, userId);
        dto.getRoleIds().forEach(roleId -> projectMemberRoleRepository.bindRole(projectId, userId, roleId));
        log.info("更新成员角色：projectId={}，userId={}，roleIds={}", projectId, userId, dto.getRoleIds());
    }

    @Override
    public List<RoleVo> listRoles(String projectId) {
        requireProject(projectId);
        return projectRoleRepository.listByProjectId(projectId).stream()
                .map(this::toRoleVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVo createRole(String projectId, RoleSaveDto dto) {
        requireProject(projectId);
        validatePermissionSubset(dto.getPermissions());
        ProjectRoleDo role = new ProjectRoleDo();
        role.setProjectId(projectId);
        role.setRoleName(dto.getRoleName());
        role.setRoleCode("CUSTOM_" + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        role.setPermissions(JSON.toJSONString(dto.getPermissions()));
        role.setIsBuiltin("0");
        projectRoleRepository.save(role);
        log.info("创建自定义角色：projectId={}，roleName={}", projectId, dto.getRoleName());
        return toRoleVo(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVo updateRole(String projectId, String roleId, RoleSaveDto dto) {
        requireProject(projectId);
        ProjectRoleDo role = projectRoleRepository.getById(roleId);
        if (role == null || !Objects.equals(role.getProjectId(), projectId)) {
            throw new BusinessException(ResultCode.BAD_PARAM, "角色不存在");
        }
        if (ProjectConstants.ROLE_DISTRIBUTOR.equals(role.getRoleCode())) {
            throw new BusinessException(ResultCode.BAD_PARAM, "内置分发者角色不可编辑");
        }
        validatePermissionSubset(dto.getPermissions());
        role.setRoleName(dto.getRoleName());
        role.setPermissions(JSON.toJSONString(dto.getPermissions()));
        projectRoleRepository.updateById(role);
        return toRoleVo(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String projectId, String roleId) {
        requireProject(projectId);
        ProjectRoleDo role = projectRoleRepository.getById(roleId);
        if (role == null || !Objects.equals(role.getProjectId(), projectId)) {
            throw new BusinessException(ResultCode.BAD_PARAM, "角色不存在");
        }
        if ("1".equals(role.getIsBuiltin())) {
            throw new BusinessException(ResultCode.BAD_PARAM, "内置角色不可删除");
        }
        projectMemberRoleRepository.removeAllByRole(roleId);
        projectRoleRepository.removeById(roleId);
        log.info("删除自定义角色：projectId={}，roleId={}", projectId, roleId);
    }

    /**
     * 创建内置角色
     */
    private void createBuiltinRole(String projectId, String roleName, String roleCode, List<String> permissions) {
        ProjectRoleDo role = new ProjectRoleDo();
        role.setProjectId(projectId);
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setPermissions(JSON.toJSONString(permissions));
        role.setIsBuiltin("1");
        projectRoleRepository.save(role);
    }

    /**
     * 自定义权限集必须为分发者全集的子集
     */
    private void validatePermissionSubset(List<String> permissions) {
        Set<String> valid = new HashSet<>(ProjectConstants.DISTRIBUTOR_PERMISSIONS);
        if (!valid.containsAll(permissions)) {
            throw new BusinessException(ResultCode.BAD_PARAM, "存在非法权限点");
        }
    }

    private ProjectInfoDo requireProject(String projectId) {
        ProjectInfoDo project = projectInfoRepository.getById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.BAD_PARAM, "项目不存在");
        }
        return project;
    }

    private void requireAdmin() {
        if (!UserContext.require().isAdmin()) {
            throw new BusinessException(ResultCode.NEED_ADMIN);
        }
    }

    private ProjectVo toProjectVo(ProjectInfoDo project, Integer memberCount) {
        ProjectVo vo = new ProjectVo();
        BeanUtils.copyProperties(project, vo);
        vo.setMemberCount(memberCount);
        return vo;
    }

    private RoleVo toRoleVo(ProjectRoleDo role) {
        RoleVo vo = new RoleVo();
        BeanUtils.copyProperties(role, vo);
        vo.setPermissionList(new ArrayList<>(JsonUtils.parseStringList(role.getPermissions())));
        return vo;
    }
}
