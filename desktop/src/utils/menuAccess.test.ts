import { describe, expect, it } from 'vitest';
import { firstVisibleMenu, MENU_ORDER } from './menuAccess';

/** 路由守卫的落点计算：菜单列表为空时必须返回 null（占位），不得产生自循环重定向 */
describe('firstVisibleMenu', () => {
  it('空菜单列表返回 null', () => {
    // 回归：旧后端无 menuList / 权限接口异常时 menus=[]，此前兜底到 notification 造成自循环白屏
    expect(firstVisibleMenu([])).toBeNull();
  });

  it('返回按显示顺序的第一个可见菜单', () => {
    expect(firstVisibleMenu(['settings', 'workload'])).toBe('workload');
    expect(firstVisibleMenu(['notification', 'settings'])).toBe('notification');
    expect(firstVisibleMenu(['board', 'notification'])).toBe('board');
  });

  it('全量可见时返回看板', () => {
    expect(firstVisibleMenu([...MENU_ORDER])).toBe('board');
  });

  it('候选菜单不在已知顺序中时不参与落点', () => {
    expect(firstVisibleMenu(['unknown-code' as string])).toBeNull();
  });
});
