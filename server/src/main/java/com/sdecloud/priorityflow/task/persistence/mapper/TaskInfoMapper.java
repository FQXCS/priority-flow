package com.sdecloud.priorityflow.task.persistence.mapper;

import com.sdecloud.priorityflow.common.persistence.BaseMapper;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务信息 Mapper。
 *
 * @author priority-flow
 */
public interface TaskInfoMapper extends BaseMapper<TaskInfoDo> {

    /**
     * 项目内当前最大序号（原生 SQL 含软删除行：唯一索引 (project_id, seq_no) 不区分删除标记，
     * 新序号必须越过已删除任务占用的编号）
     */
    @Select("SELECT MAX(seq_no) FROM task_info WHERE project_id = #{projectId}")
    Integer maxSeqNoIncludingDeleted(@Param("projectId") String projectId);
}
