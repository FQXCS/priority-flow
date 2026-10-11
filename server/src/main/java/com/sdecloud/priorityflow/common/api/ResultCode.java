package com.sdecloud.priorityflow.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码定义。
 * <p>分段：0 成功；1xxx 认证；2xxx 权限；3xxx 任务规则；4xxx 参数；5xxx 系统。</p>
 *
 * @author priority-flow
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "成功"),

    /** 系统内部异常 */
    SYSTEM_ERROR(5000, "系统异常"),

    // ---------- 1xxx 认证 ----------
    /** 未登录或凭证无效 */
    UNAUTHORIZED(1001, "未登录或凭证无效"),
    /** 登录已过期 */
    TOKEN_EXPIRED(1002, "登录已过期"),
    /** 用户名或密码错误 */
    LOGIN_FAILED(1003, "用户名或密码错误"),
    /** 账号已被禁用 */
    ACCOUNT_DISABLED(1004, "账号已被禁用"),
    /** 刷新令牌无效或已过期 */
    REFRESH_INVALID(1005, "刷新令牌无效或已过期"),

    // ---------- 2xxx 权限 ----------
    /** 无项目操作权限 */
    NO_PERMISSION(2001, "无项目操作权限"),
    /** 仅任务执行人可执行该操作 */
    NOT_ASSIGNEE(2002, "仅任务执行人可执行该操作"),
    /** 仅任务创建者可执行该操作 */
    NOT_CREATOR(2003, "仅任务创建者可执行该操作"),
    /** 无权访问 */
    FORBIDDEN(2004, "无权访问"),
    /** 需要系统管理员权限 */
    NEED_ADMIN(2005, "需要系统管理员权限"),

    // ---------- 3xxx 任务规则 ----------
    /** 任务当前状态不允许该操作 */
    ILLEGAL_TRANSITION(3001, "任务当前状态不允许该操作"),
    /** 当前已有进行中的任务 */
    HAS_IN_PROGRESS(3002, "当前已有进行中的任务，请先完成或切换回等待中"),
    /** 执行人不符合条件 */
    ASSIGNEE_INVALID(3003, "执行人不符合条件"),
    /** 不允许跨优先级拖拽 */
    DRAG_CROSS_PRIORITY(3004, "不允许跨优先级拖拽"),
    /** 不允许跨执行人拖拽 */
    DRAG_CROSS_FORBIDDEN(3005, "不允许跨执行人拖拽"),
    /** 数据已被他人修改 */
    CONFLICT_REFRESH(3006, "数据已被他人修改，请刷新后重试"),
    /** 排序键空间耗尽 */
    RANK_NO_ROOM(3007, "排序键空间耗尽，请稍后重试"),
    /** 任务不存在 */
    TASK_NOT_FOUND(3008, "任务不存在"),

    // ---------- 4xxx 参数 ----------
    /** 参数错误 */
    BAD_PARAM(4001, "参数错误"),
    /** 数据已存在 */
    DUPLICATE(4002, "数据已存在");

    /** 错误码 */
    private final int code;

    /** 默认提示消息 */
    private final String msg;
}
