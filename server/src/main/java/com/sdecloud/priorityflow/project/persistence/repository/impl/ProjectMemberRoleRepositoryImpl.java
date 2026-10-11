package com.sdecloud.priorityflow.project.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.project.persistence.mapper.ProjectMemberRoleMapper;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberRoleDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRoleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 成员角色绑定仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class ProjectMemberRoleRepositoryImpl extends ServiceImpl<ProjectMemberRoleMapper, ProjectMemberRoleDo>
        implements ProjectMemberRoleRepository {

    @Override
    public List<ProjectMemberRoleDo> listByProjectAndUser(String projectId, String userId) {
        return this.list(new LambdaQueryWrapper<ProjectMemberRoleDo>()
                .eq(ProjectMemberRoleDo::getProjectId, projectId)
                .eq(ProjectMemberRoleDo::getUserId, userId));
    }

    @Override
    public List<String> listUserIdsByRole(String projectId, String roleId) {
        return this.list(new LambdaQueryWrapper<ProjectMemberRoleDo>()
                        .eq(ProjectMemberRoleDo::getProjectId, projectId)
                        .eq(ProjectMemberRoleDo::getRoleId, roleId))
                .stream().map(ProjectMemberRoleDo::getUserId).distinct().toList();
    }

    @Override
    public List<ProjectMemberRoleDo> listByProjectId(String projectId) {
        return this.list(new LambdaQueryWrapper<ProjectMemberRoleDo>()
                .eq(ProjectMemberRoleDo::getProjectId, projectId));
    }

    @Override
    public void bindRole(String projectId, String userId, String roleId) {
        // 软删除过的绑定再次添加：复活原记录以满足唯一约束
        ProjectMemberRoleDo deleted = baseMapper.selectIncludingDeleted(projectId, userId, roleId);
        if (deleted != null) {
            if ("1".equals(deleted.getDelFlag())) {
                baseMapper.revive(projectId, userId, roleId);
            }
            return;
        }
        ProjectMemberRoleDo binding = new ProjectMemberRoleDo();
        binding.setProjectId(projectId);
        binding.setUserId(userId);
        binding.setRoleId(roleId);
        this.save(binding);
    }

    @Override
    public void removeAllByUser(String projectId, String userId) {
        this.remove(new LambdaQueryWrapper<ProjectMemberRoleDo>()
                .eq(ProjectMemberRoleDo::getProjectId, projectId)
                .eq(ProjectMemberRoleDo::getUserId, userId));
    }

    @Override
    public void removeAllByRole(String roleId) {
        this.remove(new LambdaQueryWrapper<ProjectMemberRoleDo>().eq(ProjectMemberRoleDo::getRoleId, roleId));
    }
}
