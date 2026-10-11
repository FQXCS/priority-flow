package com.sdecloud.priorityflow.common.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 主表公共基类：id 第一列，审计字段最后（create_by → create_time → update_by → update_time → remark → del_flag）。
 * <p>子表（纯关联表）可不继承本类。</p>
 *
 * @author priority-flow
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 UUID（ASSIGN_UUID 自动生成） */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建者（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新者（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;

    /** 删除标记（0 正常 / 1 删除） */
    @TableLogic
    private String delFlag;
}
