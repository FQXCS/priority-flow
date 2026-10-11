package com.sdecloud.priorityflow.common.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装。
 * <p>错误码分段：1xxx 认证 / 2xxx 权限 / 3xxx 任务规则 / 4xxx 参数 / 5xxx 系统。</p>
 *
 * @param <T> 数据类型
 * @author priority-flow
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 响应码，0 表示成功 */
    private int code;

    /** 提示消息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /**
     * 成功（无数据）
     */
    public static <T> R<T> ok() {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    /**
     * 成功（带数据）
     */
    public static <T> R<T> ok(T data) {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    /**
     * 失败（使用预置错误码与默认消息）
     */
    public static <T> R<T> failed(ResultCode resultCode) {
        return build(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /**
     * 失败（使用预置错误码 + 自定义消息）
     */
    public static <T> R<T> failed(ResultCode resultCode, String msg) {
        return build(resultCode.getCode(), msg, null);
    }

    /**
     * 失败（自定义错误码与消息）
     */
    public static <T> R<T> failed(int code, String msg) {
        return build(code, msg, null);
    }

    private static <T> R<T> build(int code, String msg, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
