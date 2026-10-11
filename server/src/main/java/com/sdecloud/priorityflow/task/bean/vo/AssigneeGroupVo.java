package com.sdecloud.priorityflow.task.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 执行人任务分组 VO（等待中/进行中列按执行人分组展示）。
 *
 * @author priority-flow
 */
@Data
public class AssigneeGroupVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行人用户 ID */
    private String userId;

    /** 执行人姓名 */
    private String userName;

    /** 该执行人的任务列表 */
    private List<TaskInfoVo> taskList;
}
