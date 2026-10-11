package com.sdecloud.priorityflow.auth.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 菜单节点 VO：权限目录树中的一级菜单（侧边栏菜单 + 其下可配置权限点）。
 *
 * @author priority-flow
 */
@Data
public class MenuNodeVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单编码 */
    private String menuCode;

    /** 菜单中文名称 */
    private String menuName;

    /** 显示顺序 */
    private Integer sortNo;

    /** 准入权限：用户权限并集命中任一即显示该菜单；为空 = 登录即可见 */
    private List<String> accessPermissionList;

    /** 菜单下可配置的权限点列表 */
    private List<PermissionItemVo> permissionList;
}
