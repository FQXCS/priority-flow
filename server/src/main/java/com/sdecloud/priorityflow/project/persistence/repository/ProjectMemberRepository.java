package com.sdecloud.priorityflow.project.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;

import java.util.List;

/**
 * 项目成员仓库。
 *
 * @author priority-flow
 */
public interface ProjectMemberRepository extends IService<ProjectMemberDo> {

    /**
     * 查询项目成员
     */
    List<ProjectMemberDo> listByProjectId(String projectId);

    /**
     * 查询单个成员关系
     */
    ProjectMemberDo findMember(String projectId, String userId);

    /**
     * 添加成员（含软删除复活）
     */
    void addMember(String projectId, String userId);

    /**
     * 移除成员（软删除）
     */
    void removeMember(String projectId, String userId);
}
