import { create } from 'zustand';
import type { LoginVo, MePermissionsVo, UserVo } from '@/api/types';
import { http, unwrap } from '@/api/client';

/** Electron 环境判定 */
export const isElectron = !!(window as unknown as { electronAPI?: unknown }).electronAPI;

async function persistToken(key: string, value: string | null): Promise<void> {
  if (isElectron) {
    const api = (window as unknown as {
      electronAPI: { secureStore: { set: (k: string, v: string) => Promise<void>; clear: () => Promise<void> } };
    }).electronAPI;
    if (value === null) {
      await api.secureStore.clear();
    } else {
      await api.secureStore.set(key, value);
    }
  } else if (value === null) {
    localStorage.removeItem(key);
  } else {
    localStorage.setItem(key, value);
  }
}

async function loadToken(key: string): Promise<string | null> {
  if (isElectron) {
    const api = (window as unknown as {
      electronAPI: { secureStore: { get: (k: string) => Promise<string | null> } };
    }).electronAPI;
    return api.secureStore.get(key);
  }
  return localStorage.getItem(key);
}

interface AuthState {
  /** 是否已完成启动时的恢复流程（防止闪登录页） */
  restored: boolean;
  accessToken: string | null;
  refreshToken: string | null;
  user: UserVo | null;
  /** 是否系统管理员 */
  admin: boolean;
  /** 权限并集（当前项目）与项目清单 */
  permissions: string[];
  projectList: { projectId: string; projectName: string; projectKey: string }[];
  currentProjectId: string | null;

  /** 启动恢复：从 safeStorage/localStorage 读取令牌并拉取权限 */
  restore: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  /** 单飞刷新调用点（拦截器使用），返回新 accessToken */
  doRefresh: () => Promise<string | null>;
  logoutLocal: () => void;
  logout: () => Promise<void>;
  /** 切换项目并重新拉取权限并集 */
  loadPermissions: (projectId?: string) => Promise<void>;
  setCurrentProject: (projectId: string) => Promise<void>;
  hasPermission: (code: string) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  restored: false,
  accessToken: null,
  refreshToken: null,
  user: null,
  admin: false,
  permissions: [],
  projectList: [],
  currentProjectId: null,

  restore: async () => {
    const accessToken = await loadToken('pf.accessToken');
    const refreshToken = await loadToken('pf.refreshToken');
    if (accessToken && refreshToken) {
      set({ accessToken, refreshToken });
      try {
        await get().loadPermissions();
      } catch {
        // 权限拉取失败按未登录处理
        await get().logoutLocal();
      }
    }
    set({ restored: true });
  },

  login: async (username: string, password: string) => {
    const vo = await unwrap<LoginVo>(http.post('/api/auth/login', { username, password }));
    await persistToken('pf.accessToken', vo.accessToken);
    await persistToken('pf.refreshToken', vo.refreshToken);
    set({ accessToken: vo.accessToken, refreshToken: vo.refreshToken, user: vo.user });
    await get().loadPermissions();
  },

  doRefresh: async () => {
    const { refreshToken } = get();
    if (!refreshToken) {
      return null;
    }
    try {
      // 注意：直接用 axios 原始实例，避免走带 401 处理的拦截器
      const response = await axiosPostWithoutAuth('/api/auth/refresh', { refreshToken });
      await persistToken('pf.accessToken', response.accessToken);
      await persistToken('pf.refreshToken', response.refreshToken);
      set({ accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user });
      return response.accessToken;
    } catch {
      await get().logoutLocal();
      return null;
    }
  },

  logoutLocal: () => {
    void persistToken('pf.accessToken', null);
    void persistToken('pf.refreshToken', null);
    set({
      accessToken: null,
      refreshToken: null,
      user: null,
      permissions: [],
      projectList: [],
      currentProjectId: null,
    });
  },

  logout: async () => {
    const { refreshToken } = get();
    try {
      await http.post('/api/auth/logout', refreshToken ? { refreshToken } : {});
    } catch {
      // 服务端登出失败不阻塞本地登出
    }
    get().logoutLocal();
  },

  loadPermissions: async (projectId?: string) => {
    const vo = await unwrap<MePermissionsVo>(
      http.get(`/api/me/permissions${projectId ? `?projectId=${projectId}` : ''}`),
    );
    const profile = await unwrap<UserVo>(http.get('/api/me/profile'));
    const target = projectId ?? vo.projectList[0]?.projectId ?? null;
    set({
      user: profile,
      admin: vo.admin,
      permissions: vo.permissions,
      projectList: vo.projectList,
      currentProjectId: target,
    });
  },

  setCurrentProject: async (projectId: string) => {
    await get().loadPermissions(projectId);
  },

  hasPermission: (code: string) => {
    return get().permissions.includes(code);
  },
}));

/** 绕过响应拦截器的 POST（刷新令牌专用） */
async function axiosPostWithoutAuth(url: string, body: unknown): Promise<LoginVo> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const json = (await response.json()) as { code: number; msg: string; data?: LoginVo };
  if (json.code !== 0 || !json.data) {
    throw new Error(json.msg || '刷新失败');
  }
  return json.data;
}
