package com.sdecloud.priorityflow.project.service.impl;

import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import com.sdecloud.priorityflow.project.service.ProjectQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目查询服务实现（跨域只读）。
 *
 * @author priority-flow
 */
@Service
@RequiredArgsConstructor
public class ProjectQueryServiceImpl implements ProjectQueryService {

    private final ProjectInfoRepository projectInfoRepository;

    @Override
    public List<ProjectInfoDo> listMemberProjects(String userId) {
        return projectInfoRepository.listByMemberUserId(userId);
    }
}
