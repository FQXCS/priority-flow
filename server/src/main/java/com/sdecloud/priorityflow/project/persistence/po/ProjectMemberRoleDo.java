package com.sdecloud.priorityflow.project.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 成员角色绑定实体（子表：不继承 BaseEntity，精简为业务字段 + create_by/create_time + del_flag）。
 *
 * @author priority-flow
 */
@Data
@TableName("project_member_role")
public class ProjectMemberRoleDo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目 ID */
    private String projectId;

    /** 用户 ID */
    private String userId;

    /** 角色 ID */
    private String roleId;

    /** 创建者（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 删除标记（0 正常 / 1 删除） */
    @TableLogic
    private String delFlag;
}
