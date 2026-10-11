package com.sdecloud.priorityflow.project.service;

import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;

import java.util.List;

/**
 * 项目查询服务（跨域只读接口：auth 域 me/permissions 使用）。
 *
 * @author priority-flow
 */
public interface ProjectQueryService {

    /**
     * 查询用户参与的项目
     */
    List<ProjectInfoDo> listMemberProjects(String userId);
}
