package com.sdecloud.priorityflow.task.bean.dto;

import lombok.Data;

/**
 * 拖拽排序入参：服务端只收目标位置（beforeId/afterId），rank 由服务端计算。
 *
 * @author priority-flow
 */
@Data
public class TaskSortDto {

    /** 目标位置的后邻任务 ID（拖到它前面） */
    private String beforeId;

    /** 目标位置的前邻任务 ID（拖到它后面） */
    private String afterId;
}
