import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/stores/auth';
import { Layout } from '@/components/Layout';
import { LoginPage } from '@/features/auth/LoginPage';
import { BoardPage } from '@/features/board/BoardPage';
import { MyTaskPage } from '@/features/my-task/MyTaskPage';
import { DraftsPage } from '@/features/drafts/DraftsPage';
import { WorkloadPage } from '@/features/workload/WorkloadPage';
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
        <Route path="/board" element={<BoardPage />} />
        <Route path="/my-task" element={<MyTaskPage />} />
        <Route path="/drafts" element={<DraftsPage />} />
        <Route path="/workload" element={<WorkloadPage />} />
        <Route path="/notifications" element={<NotificationPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/board" replace />} />
    </Routes>
  );
}
