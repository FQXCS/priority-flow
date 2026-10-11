package com.sdecloud.priorityflow.common.exception;

import com.sdecloud.priorityflow.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常：service/切面中抛出，由全局异常处理器转为统一 R 响应。
 *
 * @author priority-flow
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final ResultCode code;

    public BusinessException(ResultCode code) {
        super(code.getMsg());
        this.code = code;
    }

    public BusinessException(ResultCode code, String message) {
        super(message);
        this.code = code;
    }
}
