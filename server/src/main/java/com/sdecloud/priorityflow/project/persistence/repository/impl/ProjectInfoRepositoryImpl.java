package com.sdecloud.priorityflow.project.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.project.persistence.mapper.ProjectInfoMapper;
import com.sdecloud.priorityflow.project.persistence.mapper.ProjectMemberMapper;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目信息仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class ProjectInfoRepositoryImpl extends ServiceImpl<ProjectInfoMapper, ProjectInfoDo>
        implements ProjectInfoRepository {

    private final ProjectMemberMapper projectMemberMapper;

    public ProjectInfoRepositoryImpl(ProjectMemberMapper projectMemberMapper) {
        this.projectMemberMapper = projectMemberMapper;
    }

    @Override
    public ProjectInfoDo findByKey(String projectKey) {
        return this.getOne(new LambdaQueryWrapper<ProjectInfoDo>().eq(ProjectInfoDo::getProjectKey, projectKey), false);
    }

    @Override
    public List<ProjectInfoDo> listByMemberUserId(String userId) {
        // 先查成员关系，再取项目
        List<ProjectMemberDo> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberDo>().eq(ProjectMemberDo::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        return this.listByIds(memberships.stream().map(ProjectMemberDo::getProjectId).toList());
    }

    @Override
    public List<ProjectInfoDo> listAll() {
        return this.list();
    }
}
