package com.sdecloud.priorityflow.common.permission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 权限点定义：编码 + 中文名称（菜单目录树展示用）。
 *
 * @author priority-flow
 */
@Getter
@RequiredArgsConstructor
public class PermissionDefinition {

    /** 权限点编码（与 {@link PermissionConstants} 常量一致） */
    private final String code;

    /** 权限点中文名称 */
    private final String name;
}
