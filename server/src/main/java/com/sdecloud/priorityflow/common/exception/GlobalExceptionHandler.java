package com.sdecloud.priorityflow.common.exception;

import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常、参数校验异常、系统异常统一转为 R 响应。
 *
 * @author priority-flow
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：code={}, msg={}", e.getCode().getCode(), e.getMessage());
        return R.failed(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid DTO）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? ResultCode.BAD_PARAM.getMsg() : fieldError.getDefaultMessage();
        return R.failed(ResultCode.BAD_PARAM, msg);
    }

    /**
     * 唯一约束冲突
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突：{}", e.getMessage());
        return R.failed(ResultCode.DUPLICATE);
    }

    /**
     * 系统异常兜底
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.failed(ResultCode.SYSTEM_ERROR);
    }
}
