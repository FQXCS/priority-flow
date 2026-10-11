package com.sdecloud.priorityflow.common.permission;

import com.sdecloud.priorityflow.project.constant.ProjectConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单权限目录单测：目录完整性（不重不漏）+ 各内置角色默认权限集对应的菜单可见性。
 *
 * @author priority-flow
 */
class SystemMenuEnumTest {

    /** 收集权限点常量类中全部权限码（反射读取 public static final String 字段） */
    private static Set<String> allConstantCodes() {
        Set<String> codes = new HashSet<>();
        for (Field field : PermissionConstants.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    codes.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return codes;
    }

    @Test
    void catalogCoversAllPermissionCodesExactlyOnce() {
        // 目录树中权限点必须恰好覆盖全部常量：无遗漏、无重复
        List<String> treeCodes = new ArrayList<>();
        for (SystemMenuEnum menu : SystemMenuEnum.values()) {
            menu.getPermissions().forEach(p -> treeCodes.add(p.getCode()));
        }
        Set<String> constantCodes = allConstantCodes();
        assertEquals(constantCodes.size(), treeCodes.size(), "权限点在目录树中不得重复");
        assertEquals(constantCodes, new HashSet<>(treeCodes), "目录树必须覆盖全部权限点常量");
    }

    @Test
    void accessPermissionsAreSubsetOfMenuPermissions() {
        // 准入权限必须是本菜单下可配置权限点的子集，否则会出现「见菜单却配不出权限」
        for (SystemMenuEnum menu : SystemMenuEnum.values()) {
            Set<String> ownCodes = new HashSet<>(menu.getPermissions().stream()
                    .map(PermissionDefinition::getCode).toList());
            assertTrue(ownCodes.containsAll(menu.getAccessPermissions()),
                    menu + " 的准入权限必须挂在本菜单权限点之下");
        }
    }

    @Test
    void alwaysVisibleMenusWhenNoPermission() {
        // 无任何权限：仅通知中心与设置（个人设置必须可达）可见
        List<String> menus = SystemMenuEnum.visibleMenuCodes(Set.of());
        assertEquals(List.of("notification", "settings"), menus);
    }

    @Test
    void pmCannotSeeMyTaskMenu() {
        // PM 默认无开始/完成权限：看板等可见，我的任务不可见
        List<String> menus = SystemMenuEnum.visibleMenuCodes(Set.copyOf(ProjectConstants.PM_PERMISSIONS));
        assertFalse(menus.contains("myTask"));
        assertTrue(menus.contains("board"));
        assertTrue(menus.contains("myDraft"));
        assertTrue(menus.contains("workload"));
    }

    @Test
    void executorSeesBoardAndMyTaskOnly() {
        // 执行人默认无草稿/负载权限：看板 + 我的任务 + 始终可见菜单
        List<String> menus = SystemMenuEnum.visibleMenuCodes(Set.copyOf(ProjectConstants.EXECUTOR_PERMISSIONS));
        assertEquals(List.of("board", "myTask", "notification", "settings"), menus);
    }

    @Test
    void observerSeesBoardAndMaskedWorkloadOnly() {
        // 观察者默认含 viewMasked：可进人员负载（仅脱敏视图），但无我的任务/我的草稿
        List<String> menus = SystemMenuEnum.visibleMenuCodes(Set.copyOf(ProjectConstants.OBSERVER_PERMISSIONS));
        assertTrue(menus.contains("board"));
        assertTrue(menus.contains("workload"));
        assertFalse(menus.contains("myTask"));
        assertFalse(menus.contains("myDraft"));
    }

    @Test
    void distributorSeesAllMenus() {
        assertEquals(SystemMenuEnum.allMenuCodes(),
                SystemMenuEnum.visibleMenuCodes(Set.copyOf(ProjectConstants.DISTRIBUTOR_PERMISSIONS)));
        assertEquals(6, SystemMenuEnum.allMenuCodes().size());
    }
}
