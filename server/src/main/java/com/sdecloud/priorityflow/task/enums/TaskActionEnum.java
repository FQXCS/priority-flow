package com.sdecloud.priorityflow.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务动作枚举（状态机动作表）。
 *
 * @author priority-flow
 */
@Getter
@AllArgsConstructor
public enum TaskActionEnum {

    /** 发布（DRAFT → PENDING_DIST） */
    PUBLISH("publish", "发布"),

    /** 撤回（PENDING_DIST → DRAFT，仅创建者） */
    WITHDRAW("withdraw", "撤回"),

    /** 分发（PENDING_DIST → WAITING） */
    DISTRIBUTE("distribute", "分发"),

    /** 开始（WAITING → IN_PROGRESS） */
    START("start", "开始"),

    /** 切换回等待中（IN_PROGRESS → WAITING，保留原 rank） */
    SWITCH_BACK("switchBack", "切换回等待中"),

    /** 完成（IN_PROGRESS → COMPLETED） */
    COMPLETE("complete", "完成"),

    /** 关闭（COMPLETED/WAITING → CLOSED） */
    CLOSE("close", "关闭"),

    /** 重新打开（CLOSED → IN_PROGRESS 或 WAITING 队首） */
    REOPEN("reopen", "重新打开");

    /** 动作编码 */
    private final String code;

    /** 中文名称 */
    private final String label;
}
