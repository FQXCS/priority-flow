package com.sdecloud.priorityflow.project.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;

import java.util.List;

/**
 * 项目角色仓库。
 *
 * @author priority-flow
 */
public interface ProjectRoleRepository extends IService<ProjectRoleDo> {

    /**
     * 查询项目全部角色
     */
    List<ProjectRoleDo> listByProjectId(String projectId);

    /**
     * 按编码查询项目内角色
     */
    ProjectRoleDo findByCode(String projectId, String roleCode);
}
