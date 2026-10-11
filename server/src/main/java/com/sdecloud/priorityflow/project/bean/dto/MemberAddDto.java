package com.sdecloud.priorityflow.project.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加项目成员入参。
 *
 * @author priority-flow
 */
@Data
public class MemberAddDto {

    /** 用户 ID */
    @NotBlank(message = "用户 ID 不能为空")
    private String userId;
}
