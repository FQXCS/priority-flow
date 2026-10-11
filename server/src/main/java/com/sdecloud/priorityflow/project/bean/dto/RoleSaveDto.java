package com.sdecloud.priorityflow.project.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色（创建/更新）入参。
 *
 * @author priority-flow
 */
@Data
public class RoleSaveDto {

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称最长 64 字符")
    private String roleName;

    /** 权限点集合（必须是分发者全集的子集） */
    @NotNull(message = "权限点集合不能为 null")
    private List<String> permissions;
}
