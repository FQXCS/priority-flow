package com.sdecloud.priorityflow.project.persistence.mapper;

import com.sdecloud.priorityflow.common.persistence.BaseMapper;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberRoleDo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 成员角色绑定 Mapper。
 *
 * @author priority-flow
 */
public interface ProjectMemberRoleMapper extends BaseMapper<ProjectMemberRoleDo> {

    /**
     * 查询含已软删除的绑定记录（复活判断用）
     */
    @Select("SELECT * FROM project_member_role WHERE project_id = #{projectId} AND user_id = #{userId} "
            + "AND role_id = #{roleId} LIMIT 1")
    ProjectMemberRoleDo selectIncludingDeleted(@Param("projectId") String projectId,
                                               @Param("userId") String userId,
                                               @Param("roleId") String roleId);

    /**
     * 复活软删除的绑定记录
     */
    @Update("UPDATE project_member_role SET del_flag = '0' WHERE project_id = #{projectId} "
            + "AND user_id = #{userId} AND role_id = #{roleId}")
    int revive(@Param("projectId") String projectId, @Param("userId") String userId, @Param("roleId") String roleId);
}
