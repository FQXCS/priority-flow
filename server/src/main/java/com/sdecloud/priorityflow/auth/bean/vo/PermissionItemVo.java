package com.sdecloud.priorityflow.auth.bean.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 权限点条目 VO：编码 + 中文名称（权限目录树叶子节点）。
 *
 * @author priority-flow
 */
@Data
public class PermissionItemVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权限点编码 */
    private String code;

    /** 权限点中文名称 */
    private String name;
}
