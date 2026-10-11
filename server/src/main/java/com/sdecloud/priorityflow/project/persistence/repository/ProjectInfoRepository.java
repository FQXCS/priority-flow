package com.sdecloud.priorityflow.project.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;

import java.util.List;

/**
 * 项目信息仓库。
 *
 * @author priority-flow
 */
public interface ProjectInfoRepository extends IService<ProjectInfoDo> {

    /**
     * 按编号前缀查询（唯一校验用）
     */
    ProjectInfoDo findByKey(String projectKey);

    /**
     * 查询用户参与的全部项目
     */
    List<ProjectInfoDo> listByMemberUserId(String userId);

    /**
     * 查询全部项目（系统管理）
     */
    List<ProjectInfoDo> listAll();
}
