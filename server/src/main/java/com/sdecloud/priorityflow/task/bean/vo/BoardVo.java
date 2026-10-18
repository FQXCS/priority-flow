package com.sdecloud.priorityflow.task.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 看板 VO：一次性返回六列结构化数据（等待中/进行中为扁平列表，人员筛选走查询参数）。
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

    /** 等待中列（全部执行人合并，优先级降序 + sort_rank 升序） */
    private List<TaskInfoVo> waitingList;

    /** 进行中列（全部执行人合并，started_time 倒序） */
    private List<TaskInfoVo> inProgressList;

    /** 已完成列（completed_time 倒序） */
    private List<TaskInfoVo> completedList;

    /** 已关闭列（closed_time 倒序） */
    private List<TaskInfoVo> closedList;
}
