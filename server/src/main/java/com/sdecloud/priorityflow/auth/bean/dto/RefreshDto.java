package com.sdecloud.priorityflow.auth.bean.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌入参。
 *
 * @author priority-flow
 */
@Data
public class RefreshDto {

    /** 刷新令牌 */
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
