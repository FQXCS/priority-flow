package com.sdecloud.priorityflow.project.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目信息实体。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_info")
public class ProjectInfoDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目名称 */
    private String projectName;

    /** 任务编号前缀（如 PF），唯一 */
    private String projectKey;

    /** 状态（0 正常 / 1 归档） */
    private String status;
}
