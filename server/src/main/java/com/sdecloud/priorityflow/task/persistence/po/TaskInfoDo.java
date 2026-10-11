package com.sdecloud.priorityflow.task.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.enums.TaskTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务信息实体（核心表，一期不含子任务）。
 *
 * <p>sort_rank 生命周期：草稿/已完成/已关闭为 NULL；待分发作用域=项目；
 * 等待中作用域=执行人；进行中不参与排序（列值保留进入等待中时的 rank，
 * 切换回等待中时按 PRD 要求恢复原排序位）。</p>
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_info")
public class TaskInfoDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属项目 ID */
    private String projectId;

    /** 项目内递增序号，任务编号 = {project_key}-{seq_no} */
    private Integer seqNo;

    /** 标题 */
    private String title;

    /** 富文本描述（存储前已 XSS 清洗） */
    private String description;

    /** 优先级 */
    private PriorityEnum priority;

    /** 任务类型 */
    private TaskTypeEnum taskType;

    /** 状态 */
    private TaskStatusEnum taskStatus;

    /** 创建人 = 草稿归属人（业务字段，归属永不变更；与审计字段 create_by 语义分离） */
    private String creatorId;

    /** 执行人（分发后必填） */
    private String assigneeId;

    /** 分发操作人 */
    private String distributorId;

    /** 自由标签（JSON 字符串数组） */
    private String labels;

    /** 预计工时（小时） */
    private BigDecimal estimatedHours;

    /** 剩余工时（小时） */
    private BigDecimal remainingHours;

    /** 预计完成时间 */
    private LocalDateTime expectedFinishAt;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** LexoRank 排序键（同优先级桶内顺序键；显示顺序 = 优先级降序 + 桶内 rank 升序） */
    private String sortRank;

    /** 发布时间 */
    private LocalDateTime publishedTime;

    /** 分发时间 */
    private LocalDateTime distributedTime;

    /** 开始时间 */
    private LocalDateTime startedTime;

    /** 完成时间 */
    private LocalDateTime completedTime;

    /** 关闭时间 */
    private LocalDateTime closedTime;

    /** 逾期通知已发标记（0 未发 / 1 已发，防重复轰炸） */
    private String overdueNotified;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
