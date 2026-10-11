package com.sdecloud.priorityflow.project.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.project.persistence.mapper.ProjectRoleMapper;
import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectRoleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目角色仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class ProjectRoleRepositoryImpl extends ServiceImpl<ProjectRoleMapper, ProjectRoleDo>
        implements ProjectRoleRepository {

    @Override
    public List<ProjectRoleDo> listByProjectId(String projectId) {
        return this.list(new LambdaQueryWrapper<ProjectRoleDo>().eq(ProjectRoleDo::getProjectId, projectId));
    }

    @Override
    public ProjectRoleDo findByCode(String projectId, String roleCode) {
        return this.getOne(new LambdaQueryWrapper<ProjectRoleDo>()
                .eq(ProjectRoleDo::getProjectId, projectId)
                .eq(ProjectRoleDo::getRoleCode, roleCode), false);
    }
}
