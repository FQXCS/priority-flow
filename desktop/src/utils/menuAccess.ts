/** 菜单显示顺序（与后端 SystemMenuEnum 一致；Layout.NAV_ITEMS 顺序需保持一致） */
export const MENU_ORDER = ['board', 'myTask', 'myDraft', 'plan', 'workload', 'report', 'notification', 'settings'];

/**
 * 菜单编码 → 路由路径。
 * myTask/myDraft 的编码与路由路径不同（/my-task、/drafts），守卫重定向必须走真实路径，
 * 否则落到 * 通配路由与 /board 守卫互相弹跳，产生无限重定向白屏。
 */
export const MENU_PATHS: Record<string, string> = {
  board: '/board',
  myTask: '/my-task',
  myDraft: '/drafts',
  plan: '/plan',
  workload: '/workload',
  report: '/report',
  notification: '/notifications',
  settings: '/settings',
};

/**
 * 计算重定向落点：按显示顺序返回第一个可见菜单编码。
 *
 * @param menus 当前可见菜单编码列表
 * @returns 第一个可见菜单编码；列表为空返回 null（调用方应渲染占位页，不得重定向，避免自循环）
 */
export function firstVisibleMenu(menus: string[]): string | null {
  return MENU_ORDER.find((code) => menus.includes(code)) ?? null;
}
