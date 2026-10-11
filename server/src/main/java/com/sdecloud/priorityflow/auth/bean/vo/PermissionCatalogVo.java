package com.sdecloud.priorityflow.auth.bean.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 权限目录 VO：「菜单 → 权限点」树，角色编辑界面渲染用。
 *
 * @author priority-flow
 */
@Data
public class PermissionCatalogVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单节点列表（按显示顺序） */
    private List<MenuNodeVo> menuList;
}
