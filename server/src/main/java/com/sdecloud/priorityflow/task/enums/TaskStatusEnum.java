package com.sdecloud.priorityflow.task.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举（varchar 存字符串常量）。
 *
 * <pre>
 * DRAFT → PENDING_DIST → WAITING → IN_PROGRESS → COMPLETED → CLOSED
 *   ↑（withdraw 撤回）↑        ↑（switchBack 切回）↑
 * CLOSED →（reopen，执行人空闲 → IN_PROGRESS / 忙碌 → WAITING 队首）
 * </pre>
 *
 * @author priority-flow
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    /** 草稿（sort_rank 为 NULL，按 update_time 倒序） */
    DRAFT("DRAFT", "草稿"),

    /** 待分发（sort_rank 作用域=项目） */
    PENDING_DIST("PENDING_DIST", "待分发"),

    /** 等待中（sort_rank 作用域=执行人） */
    WAITING("WAITING", "等待中"),

    /** 进行中（sort_rank 不参与排序，按 started_time 倒序） */
    IN_PROGRESS("IN_PROGRESS", "进行中"),

    /** 已完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 已关闭 */
    CLOSED("CLOSED", "已关闭");

    /** 数据库存储值 */
    @EnumValue
    private final String code;

    /** 中文名称 */
    private final String label;
}
