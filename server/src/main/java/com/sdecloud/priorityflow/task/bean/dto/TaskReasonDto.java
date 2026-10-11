package com.sdecloud.priorityflow.task.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 原因备注入参（关闭/重新打开）。
 *
 * @author priority-flow
 */
@Data
public class TaskReasonDto {

    /** 原因备注 */
    @NotBlank(message = "原因备注不能为空")
    @Size(max = 512, message = "原因最长 512 字符")
    private String reason;
}
