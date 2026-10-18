import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth';
import { firstVisibleMenu, MENU_PATHS } from '@/utils/menuAccess';
import { Layout } from '@/components/Layout';
import { LoginPage } from '@/features/auth/LoginPage';
import { BoardPage } from '@/features/board/BoardPage';
import { PlanPage } from '@/features/plan/PlanPage';
import { MyTaskPage } from '@/features/my-task/MyTaskPage';
import { DraftsPage } from '@/features/drafts/DraftsPage';
import { WorkloadPage } from '@/features/workload/WorkloadPage';
import { ReportPage } from '@/features/report/ReportPage';
import { NotificationPage } from '@/features/notification/NotificationPage';
import { SettingsPage } from '@/features/settings/SettingsPage';

/** 权限守卫：未登录跳转登录页；恢复流程完成前显示加载态 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { restored, accessToken } = useAuthStore();
  if (!restored) {
    return <div className="grid h-screen place-items-center text-sm text-zinc-400">恢复会话中…</div>;
  }
  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

/** 菜单可见性守卫：深链直达无权限菜单时重定向到第一个可见菜单；无任何可见菜单时渲染占位页（不重定向，避免自循环白屏） */
function RequireMenu({ menuCode, children }: { menuCode: string; children: React.ReactNode }) {
  const { menus } = useAuthStore();
  if (!menus.includes(menuCode)) {
    const firstAllowed = firstVisibleMenu(menus);
    if (!firstAllowed) {
      return (
        <div className="grid h-screen place-items-center text-sm text-zinc-400">
          暂无可访问的菜单，请联系管理员分配角色
        </div>
      );
    }
    // 按真实路由路径重定向（编码≠路径，如 myTask→/my-task；直接拼编码会落 * 通配造成重定向循环）
    return <Navigate to={MENU_PATHS[firstAllowed] ?? `/${firstAllowed}`} replace />;
  }
  return <>{children}</>;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/board" replace />} />
        <Route
          path="/board"
          element={
            <RequireMenu menuCode="board">
              <BoardPage />
            </RequireMenu>
          }
        />
        <Route
          path="/plan"
          element={
            <RequireMenu menuCode="plan">
              <PlanPage />
            </RequireMenu>
          }
        />
        <Route
          path="/my-task"
          element={
            <RequireMenu menuCode="myTask">
              <MyTaskPage />
            </RequireMenu>
          }
        />
        <Route
          path="/drafts"
          element={
            <RequireMenu menuCode="myDraft">
              <DraftsPage />
            </RequireMenu>
          }
        />
        <Route
          path="/workload"
          element={
            <RequireMenu menuCode="workload">
              <WorkloadPage />
            </RequireMenu>
          }
        />
        <Route
          path="/report"
          element={
            <RequireMenu menuCode="report">
              <ReportPage />
            </RequireMenu>
          }
        />
        <Route
          path="/notifications"
          element={
            <RequireMenu menuCode="notification">
              <NotificationPage />
            </RequireMenu>
          }
        />
        <Route
          path="/settings"
          element={
            <RequireMenu menuCode="settings">
              <SettingsPage />
            </RequireMenu>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/board" replace />} />
    </Routes>
  );
}
