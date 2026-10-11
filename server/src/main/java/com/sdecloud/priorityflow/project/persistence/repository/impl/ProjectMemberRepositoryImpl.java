package com.sdecloud.priorityflow.project.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.project.persistence.mapper.ProjectMemberMapper;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目成员仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class ProjectMemberRepositoryImpl extends ServiceImpl<ProjectMemberMapper, ProjectMemberDo>
        implements ProjectMemberRepository {

    @Override
    public List<ProjectMemberDo> listByProjectId(String projectId) {
        return this.list(new LambdaQueryWrapper<ProjectMemberDo>().eq(ProjectMemberDo::getProjectId, projectId));
    }

    @Override
    public ProjectMemberDo findMember(String projectId, String userId) {
        return this.getOne(new LambdaQueryWrapper<ProjectMemberDo>()
                .eq(ProjectMemberDo::getProjectId, projectId)
                .eq(ProjectMemberDo::getUserId, userId), false);
    }

    @Override
    public void addMember(String projectId, String userId) {
        // 软删除过成员再次加入：复活原记录以满足唯一约束
        ProjectMemberDo deleted = baseMapper.selectIncludingDeleted(projectId, userId);
        if (deleted != null) {
            if ("1".equals(deleted.getDelFlag())) {
                baseMapper.revive(projectId, userId);
            }
            return;
        }
        ProjectMemberDo member = new ProjectMemberDo();
        member.setProjectId(projectId);
        member.setUserId(userId);
        this.save(member);
    }

    @Override
    public void removeMember(String projectId, String userId) {
        this.remove(new LambdaQueryWrapper<ProjectMemberDo>()
                .eq(ProjectMemberDo::getProjectId, projectId)
                .eq(ProjectMemberDo::getUserId, userId));
    }
}
