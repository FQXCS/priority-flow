package com.sdecloud.priorityflow.task.bean.dto;

import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改优先级入参（rank 重置为新优先级桶最后）。
 *
 * @author priority-flow
 */
@Data
public class TaskPriorityDto {

    /** 目标优先级 */
    @NotNull(message = "优先级不能为空")
    private PriorityEnum priority;
}
