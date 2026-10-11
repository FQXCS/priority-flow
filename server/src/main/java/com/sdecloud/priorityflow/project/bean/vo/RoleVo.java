package com.sdecloud.priorityflow.project.bean.vo;

import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 角色 VO（继承 PO，权限 JSON 文本解析为列表下发）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleVo extends ProjectRoleDo {

    private static final long serialVersionUID = 1L;

    /** 权限点列表 */
    private List<String> permissionList;
}
