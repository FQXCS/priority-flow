package com.sdecloud.priorityflow.project.bean.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新成员角色绑定入参（全量替换）。
 *
 * @author priority-flow
 */
@Data
public class MemberRoleUpdateDto {

    /** 角色 ID 列表（空数组表示清空角色） */
    @NotNull(message = "角色列表不能为 null")
    private List<String> roleIds;
}
