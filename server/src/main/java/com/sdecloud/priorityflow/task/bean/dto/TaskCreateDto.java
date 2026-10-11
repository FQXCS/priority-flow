package com.sdecloud.priorityflow.task.bean.dto;

import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.enums.TaskTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建任务入参（body 带 action = DRAFT/PUBLISH）。
 *
 * @author priority-flow
 */
@Data
public class TaskCreateDto {

    /** 保存动作：DRAFT 存草稿 / PUBLISH 直接发布 */
    private String action = "DRAFT";

    /** 所属项目 ID */
    @NotBlank(message = "所属项目不能为空")
    private String projectId;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题最长 255 字符")
    private String title;

    /** 富文本描述（服务端 XSS 清洗后入库） */
    private String description;

    /** 优先级（发布时必填） */
    private PriorityEnum priority;

    /** 任务类型 */
    private TaskTypeEnum taskType;

    /** 自由标签 */
    private List<String> labels;

    /** 预计工时（小时） */
    private BigDecimal estimatedHours;

    /** 剩余工时（小时） */
    private BigDecimal remainingHours;

    /** 预计完成时间 */
    private LocalDateTime expectedFinishAt;

    /** 截止时间 */
    private LocalDateTime deadline;
}
