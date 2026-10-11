import { ReactNode, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { notificationApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { useUiStore } from '@/stores/ui';
import { Avatar } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { ProjectCreateDialog } from '@/components/ProjectCreateDialog';

/** 侧边栏菜单：menuCode 与后端 SystemMenuEnum 一致，按权限并集动态显隐 */
const NAV_ITEMS = [
  { to: '/board', menuCode: 'board', label: '看板', icon: '▦' },
  { to: '/my-task', menuCode: 'myTask', label: '我的任务', icon: '☑' },
  { to: '/drafts', menuCode: 'myDraft', label: '我的草稿', icon: '✎' },
  { to: '/workload', menuCode: 'workload', label: '人员负载', icon: '⚖' },
  { to: '/notifications', menuCode: 'notification', label: '通知中心', icon: '🔔' },
  { to: '/settings', menuCode: 'settings', label: '设置', icon: '⚙' },
];

/** 应用骨架：侧边导航 + 顶栏（项目切换/新建项目/通知/主题/用户） */
export function Layout() {
  const navigate = useNavigate();
  const { user, admin, projectList, currentProjectId, setCurrentProject, logout, loadPermissions, permissions, menus } =
    useAuthStore();
  const { theme, setTheme } = useUiStore();
  const [createOpen, setCreateOpen] = useState(false);

  // 未读数轮询（WS 推送到达时也会主动失效该查询）
  const { data: unread = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationApi.unreadCount(),
    refetchInterval: 60000,
  });

  async function onProjectChange(projectId: string) {
    await setCurrentProject(projectId);
  }

  return (
    <div className="flex h-screen bg-surface-light text-zinc-800 dark:bg-[#17181c] dark:text-zinc-200">
      {/* 侧边导航 */}
      <aside className="flex w-52 shrink-0 flex-col border-r border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
        <div className="px-4 py-4">
          <div className="text-base font-bold text-blue-600 dark:text-blue-400">Priority Flow</div>
          <div className="text-[11px] text-zinc-400">研发任务优先级看板</div>
        </div>
        <nav className="flex-1 space-y-0.5 px-2">
          {NAV_ITEMS.filter((item) => menus.includes(item.menuCode)).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? 'bg-blue-50 font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                    : 'text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800'
                }`
              }
            >
              <span className="w-4 text-center">{item.icon}</span>
              <span className="flex-1">{item.label}</span>
              {item.to === '/notifications' && unread > 0 && (
                <span className="rounded-full bg-red-500 px-1.5 text-[10px] font-semibold text-white">{unread}</span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-zinc-200 p-3 dark:border-zinc-800">
          <div className="flex items-center gap-2">
            <Avatar name={user?.displayName} />
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{user?.displayName}</div>
              <div className="truncate text-[11px] text-zinc-400">{user?.username}</div>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="mt-2 w-full justify-start"
            onClick={() => {
              void logout();
              navigate('/login');
            }}
          >
            退出登录
          </Button>
        </div>
      </aside>

      {/* 右侧主区域 */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center gap-3 border-b border-zinc-200 bg-white px-5 dark:border-zinc-800 dark:bg-zinc-900">
          <div className="w-56">
            <Select
              value={currentProjectId ?? ''}
              onChange={(e) => void onProjectChange(e.target.value)}
              options={projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` }))}
              placeholder={projectList.length === 0 ? '暂无项目' : ''}
            />
          </div>
          {admin && (
            <Button variant="ghost" size="icon" title="新建项目（管理员）" onClick={() => setCreateOpen(true)}>
              ＋
            </Button>
          )}
          <div className="flex-1" />
          <div className="text-[11px] text-zinc-400">
            {permissions.length > 0 ? `已加载 ${permissions.length} 项权限` : ''}
          </div>
          <Button
            variant="ghost"
            size="icon"
            title="切换主题"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          >
            {theme === 'dark' ? '☀' : '☾'}
          </Button>
        </header>
        <main className="min-h-0 flex-1 overflow-hidden">
          <Outlet />
        </main>
      </div>

      {/* 管理员创建项目：成功后刷新项目清单（保持当前选中项目） */}
      <ProjectCreateDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => void loadPermissions(currentProjectId ?? undefined)}
      />
    </div>
  );
}

export type { ReactNode };
