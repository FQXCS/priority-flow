package com.sdecloud.priorityflow.milestone.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑里程碑入参（全量覆盖；预计完成日期传 null 即移入未规划日期）。
 *
 * @author priority-flow
 */
@Data
public class MilestoneUpdateDto {

    /** 目标名称 */
    @NotBlank(message = "目标名称不能为空")
    @Size(max = 255, message = "目标名称最长 255 字符")
    private String name;

    /** 负责人用户 ID（可空） */
    private String ownerId;

    /** 预计完成日期（可空，空 = 未规划日期） */
    private LocalDate expectedDate;
}
