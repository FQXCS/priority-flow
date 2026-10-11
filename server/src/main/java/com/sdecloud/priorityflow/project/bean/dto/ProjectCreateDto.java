package com.sdecloud.priorityflow.project.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建项目入参（管理员专用，创建时指定第一任项目分发者）。
 *
 * @author priority-flow
 */
@Data
public class ProjectCreateDto {

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称最长 128 字符")
    private String projectName;

    /** 任务编号前缀（如 PF），大写字母，2~8 位 */
    @NotBlank(message = "任务编号前缀不能为空")
    @Pattern(regexp = "^[A-Z]{2,8}$", message = "任务编号前缀需为 2~8 位大写字母")
    private String projectKey;

    /** 第一任项目分发者用户 ID */
    @NotBlank(message = "第一任项目分发者不能为空")
    private String distributorUserId;

    /** 备注 */
    private String remark;
}
