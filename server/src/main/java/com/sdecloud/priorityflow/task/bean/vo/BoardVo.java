package com.sdecloud.priorityflow.task.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 看板 VO：一次性返回六列结构化数据（含按执行人分组的等待中/进行中）。
 *
 * @author priority-flow
 */
@Data
public class BoardVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 项目编号前缀 */
    private String projectKey;

    /** 草稿列（仅当前用户自己的草稿，update_time 倒序） */
    private List<TaskInfoVo> draftList;

    /** 待分发列（sort_rank 升序） */
    private List<TaskInfoVo> pendingDistList;

    /** 等待中列（按执行人分组，组内 sort_rank 升序） */
    private List<AssigneeGroupVo> waitingGroupList;

    /** 进行中列（按执行人分组，组内 started_time 倒序） */
    private List<AssigneeGroupVo> inProgressGroupList;

    /** 已完成列（completed_time 倒序） */
    private List<TaskInfoVo> completedList;

    /** 已关闭列（closed_time 倒序） */
    private List<TaskInfoVo> closedList;
}
