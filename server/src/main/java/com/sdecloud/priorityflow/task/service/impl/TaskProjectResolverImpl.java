package com.sdecloud.priorityflow.task.service.impl;

import com.sdecloud.priorityflow.common.permission.TaskProjectResolver;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任务归属解析器实现（权限切面 taskId → projectId）。
 *
 * @author priority-flow
 */
@Component
@RequiredArgsConstructor
public class TaskProjectResolverImpl implements TaskProjectResolver {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public String resolveProjectId(String taskId) {
        TaskInfoDo task = taskInfoRepository.getById(taskId);
        return task == null ? null : task.getProjectId();
    }
}
