package com.sdecloud.priorityflow.task.bean.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 人员负载 VO（项目内，需 pf:task:viewWorkload）。
 *
 * @author priority-flow
 */
@Data
public class WorkloadVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 姓名 */
    private String displayName;

    /** 当前进行中任务（无则 null） */
    private TaskInfoVo currentTask;

    /** 等待中任务数量 */
    private Integer waitingCount;
}
