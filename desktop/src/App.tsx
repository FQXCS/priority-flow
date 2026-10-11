import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { AppRoutes } from '@/router';
import { ToastContainer } from '@/components/ui/Toast';
import { useAuthStore } from '@/stores/auth';
import { useToastStore } from '@/stores/toast';
import { connectWs, disconnectWs, subscribeBoard, subscribeNotify } from '@/ws/stomp';
import type { NotifyMessage } from '@/api/types';

/**
 * 应用根部：WS 连接与订阅跟随登录态/当前项目。
 * 登录成功、启动恢复会话、切换项目、登出均在此正确接线（修复：登录后 WS 未连接导致看板不同步）。
 */
export default function App() {
  const queryClient = useQueryClient();
  const restored = useAuthStore((s) => s.restored);
  const accessToken = useAuthStore((s) => s.accessToken);
  const currentProjectId = useAuthStore((s) => s.currentProjectId);

  // 启动时恢复会话（仅一次）
  useEffect(() => {
    void useAuthStore.getState().restore();
  }, []);

  // 连接/断开 + 个人通知订阅：跟随登录态
  useEffect(() => {
    if (!restored || !accessToken) {
      disconnectWs();
      return;
    }
    connectWs();
    // 个人通知：站内 + 桌面通知（channel=DESKTOP 由客户端调 Electron API）
    subscribeNotify((message: NotifyMessage) => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
      useToastStore.getState().push(message.title, 'info');
      const electronAPI = (window as unknown as {
        electronAPI?: { notify: (title: string, body: string) => void };
      }).electronAPI;
      if (electronAPI) {
        electronAPI.notify(message.title, message.content ?? '');
      }
    });
  }, [restored, accessToken, queryClient]);

  // 看板主题订阅：跟随当前项目（切换项目自动换订；本机操作按 operatorId 去重回显）
  useEffect(() => {
    if (!restored || !accessToken || !currentProjectId) {
      return;
    }
    subscribeBoard(currentProjectId, (change) => {
      const userId = useAuthStore.getState().user?.id;
      if (change.operatorId !== userId) {
        void queryClient.invalidateQueries({ queryKey: ['board', currentProjectId] });
      }
    });
  }, [restored, accessToken, currentProjectId, queryClient]);

  return (
    <>
      <AppRoutes />
      <ToastContainer />
    </>
  );
}
