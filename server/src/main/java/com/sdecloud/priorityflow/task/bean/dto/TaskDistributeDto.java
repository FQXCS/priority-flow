package com.sdecloud.priorityflow.task.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分发任务入参。
 *
 * @author priority-flow
 */
@Data
public class TaskDistributeDto {

    /** 执行人用户 ID */
    @NotBlank(message = "执行人不能为空")
    private String assigneeId;
}
