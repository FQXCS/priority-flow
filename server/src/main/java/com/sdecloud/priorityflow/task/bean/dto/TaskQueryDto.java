package com.sdecloud.priorityflow.task.bean.dto;

import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.enums.TaskTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板/列表通用筛选入参。
 *
 * @author priority-flow
 */
@Data
public class TaskQueryDto {

    /** 按执行人筛选 */
    private String assigneeId;

    /** 按优先级筛选 */
    private PriorityEnum priority;

    /** 按类型筛选 */
    private TaskTypeEnum taskType;

    /** 截止时间区间起点 */
    private LocalDateTime deadlineFrom;

    /** 截止时间区间终点 */
    private LocalDateTime deadlineTo;

    /** 仅看逾期任务 */
    private Boolean overdueOnly;

    /** 搜索关键字（标题/描述/任务编号） */
    private String keyword;

    /** 项目筛选（仅跨项目看板 /api/board 使用；项目看板忽略此参数） */
    private String projectId;
}
