package com.sdecloud.priorityflow.task.bean.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 跨项目负载 VO（脱敏：只回项目 + 优先级，不下发任务标题等明细，数据层面保证脱敏）。
 *
 * @author priority-flow
 */
@Data
public class CrossProjectWorkloadVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 优先级 */
    private String priority;
}
