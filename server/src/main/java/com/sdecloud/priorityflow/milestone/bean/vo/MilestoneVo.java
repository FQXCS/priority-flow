package com.sdecloud.priorityflow.milestone.bean.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.sdecloud.priorityflow.milestone.persistence.po.MilestoneDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 里程碑出参：继承实体，仅追加负责人姓名翻译字段。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MilestoneVo extends MilestoneDo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 负责人显示名（翻译字段） */
    @TableField(exist = false)
    private String ownerName;
}
