package com.sdecloud.priorityflow.project.bean.vo;

import com.sdecloud.priorityflow.auth.bean.vo.UserVo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectMemberDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 项目成员 VO（继承成员 PO，附加用户信息与角色列表）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectMemberVo extends ProjectMemberDo {

    private static final long serialVersionUID = 1L;

    /** 用户信息 */
    private UserVo user;

    /** 绑定角色列表 */
    private List<RoleVo> roleList;
}
