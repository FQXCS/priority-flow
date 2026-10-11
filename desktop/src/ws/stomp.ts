import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '@/stores/auth';
import type { BoardChangeMessage, NotifyMessage } from '@/api/types';

/**
 * STOMP over WebSocket 客户端封装：断线指数退避重连、看板/个人通知订阅管理。
 * 订阅鉴权由服务端 ChannelInterceptor 校验（仅项目成员可订阅看板主题）。
 */

type BoardChangeHandler = (message: BoardChangeMessage) => void;
type NotifyHandler = (message: NotifyMessage) => void;

let client: Client | null = null;
let boardSubscription: { unsubscribe: () => void } | null = null;
let notifySubscription: { unsubscribe: () => void } | null = null;
let boardHandler: BoardChangeHandler | null = null;
let notifyHandler: NotifyHandler | null = null;

/**
 * 建立 STOMP 连接（SockJS 回退，token 经 URL 参数传递供握手鉴权）
 */
export function connectWs(): void {
  const token = useAuthStore.getState().accessToken;
  if (!token || client?.active) {
    return;
  }
  client = new Client({
    webSocketFactory: () => new SockJS(`/ws?token=${encodeURIComponent(token)}`) as WebSocket,
    reconnectDelay: 2000,
    // 指数退避重连（上限 30s）
    onWebSocketClose: () => {
      if (client) {
        client.reconnectDelay = Math.min((client.reconnectDelay || 2000) * 2, 30000);
      }
    },
    onConnect: () => {
      if (client) {
        client.reconnectDelay = 2000;
      }
      subscribeAll();
    },
    onStompError: () => {
      // 订阅被拒（无权限）等协议错误，交由上层日志观察
    },
  });
  client.activate();
}

/**
 * 断开连接（登出时）
 */
export function disconnectWs(): void {
  client?.deactivate();
  client = null;
  boardSubscription = null;
  notifySubscription = null;
}

function subscribeAll(): void {
  if (!client?.connected) {
    return;
  }
  // 个人通知队列（站内 + 桌面通知触发）
  if (notifyHandler && !notifySubscription) {
    notifySubscription = client.subscribe('/user/queue/notify', (message: IMessage) => {
      notifyHandler?.(JSON.parse(message.body) as NotifyMessage);
    });
  }
  // 当前项目看板主题
  const projectId = useAuthStore.getState().currentProjectId;
  if (projectId && boardHandler) {
    subscribeBoard(projectId, boardHandler);
  }
}

/**
 * 订阅项目看板变更（切换项目时自动换订）
 */
export function subscribeBoard(projectId: string, handler: BoardChangeHandler): void {
  boardHandler = handler;
  boardSubscription?.unsubscribe();
  boardSubscription = null;
  if (client?.connected) {
    boardSubscription = client.subscribe(`/topic/project.${projectId}`, (message: IMessage) => {
      handler(JSON.parse(message.body) as BoardChangeMessage);
    });
  }
}

/**
 * 订阅个人通知
 */
export function subscribeNotify(handler: NotifyHandler): void {
  notifyHandler = handler;
  if (client?.connected && !notifySubscription) {
    notifySubscription = client.subscribe('/user/queue/notify', (message: IMessage) => {
      handler(JSON.parse(message.body) as NotifyMessage);
    });
  }
}
