package com.sdecloud.priorityflow.common.permission;

/**
 * 任务归属解析器（common 定义接口，task 域实现，避免 project↔task 循环依赖）。
 *
 * @author priority-flow
 */
public interface TaskProjectResolver {

    /**
     * 由任务 ID 解析所属项目 ID
     *
     * @param taskId 任务 ID
     * @return 项目 ID；任务不存在返回 null
     */
    String resolveProjectId(String taskId);
}
