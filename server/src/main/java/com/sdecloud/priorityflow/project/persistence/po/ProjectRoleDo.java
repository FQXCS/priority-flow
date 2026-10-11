package com.sdecloud.priorityflow.project.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目角色实体：内置 DISTRIBUTOR/PM/EXECUTOR/OBSERVER + 自定义角色。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_role")
public class ProjectRoleDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private String projectId;

    /** 角色名称 */
    private String roleName;

    /** 角色编码（内置编码或自定义编码） */
    private String roleCode;

    /** 权限点集合（JSON 字符串数组文本） */
    private String permissions;

    /** 是否内置角色（0 否 / 1 是） */
    private String isBuiltin;
}
