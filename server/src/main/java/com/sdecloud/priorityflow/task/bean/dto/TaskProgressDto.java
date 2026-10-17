package com.sdecloud.priorityflow.task.bean.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置任务进度入参（上限 99，100 仅由完成动作产生）。
 *
 * @author priority-flow
 */
@Data
public class TaskProgressDto {

    /** 进度百分比（0-99） */
    @NotNull(message = "进度不能为空")
    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 99, message = "进度不能手动设置为100，请通过完成按钮完成任务")
    private Integer progress;
}
