package com.sdecloud.priorityflow.task.bean.vo;

import com.sdecloud.priorityflow.task.persistence.po.TaskOperationLogDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志 VO（继承 PO，附加操作人姓名）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskLogVo extends TaskOperationLogDo {

    private static final long serialVersionUID = 1L;

    /** 操作人姓名 */
    private String operatorName;
}
