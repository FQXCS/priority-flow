package com.sdecloud.priorityflow.project.persistence.mapper;

import com.sdecloud.priorityflow.common.persistence.BaseMapper;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 项目成员 Mapper：软删除 + 唯一约束并存的复活查询（绕过逻辑删除过滤）。
 *
 * @author priority-flow
 */
public interface ProjectMemberMapper extends BaseMapper<ProjectMemberDo> {

    /**
     * 查询含已软删除的成员记录（复活判断用）
     */
    @Select("SELECT * FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId} LIMIT 1")
    ProjectMemberDo selectIncludingDeleted(@Param("projectId") String projectId, @Param("userId") String userId);

    /**
     * 复活软删除的成员记录
     */
    @Update("UPDATE project_member SET del_flag = '0' WHERE project_id = #{projectId} AND user_id = #{userId}")
    int revive(@Param("projectId") String projectId, @Param("userId") String userId);
}
