package com.sdecloud.priorityflow.project.bean.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目 VO（继承 PO）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectVo extends ProjectInfoDo {

    private static final long serialVersionUID = 1L;

    /** 项目成员数 */
    @TableField(exist = false)
    private Integer memberCount;
}
