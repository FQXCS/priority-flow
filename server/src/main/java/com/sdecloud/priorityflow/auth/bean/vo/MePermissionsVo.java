package com.sdecloud.priorityflow.auth.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前用户权限 VO：项目列表 + 指定项目权限并集一次性下发。
 *
 * @author priority-flow
 */
@Data
public class MePermissionsVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否系统管理员 */
    private boolean admin;

    /** 我参与的项目（可切换项目清单） */
    private List<ProjectSimpleVo> projectList;

    /** 当前项目下的权限并集 */
    private List<String> permissions;
}
