import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';
import type { R } from './types';
import { useAuthStore } from '@/stores/auth';

/**
 * axios 实例：统一 R 响应解包 + 401 时单飞刷新令牌并重放请求。
 */
export const http: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** 单飞刷新：并发 401 只触发一次 refresh */
let refreshing: Promise<string | null> | null = null;

async function refreshOnce(): Promise<string | null> {
  const { refreshToken, doRefresh } = useAuthStore.getState();
  if (!refreshToken) {
    return null;
  }
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

http.interceptors.response.use(
  async (response) => {
    const body = response.data as R<unknown>;
    // 业务码非 0 时抛出带 code 的错误，页面按需提示
    if (body && typeof body.code === 'number' && body.code !== 0) {
      if (body.code === 1001) {
        const newToken = await refreshOnce();
        if (newToken) {
          // 重放原请求
          const config = response.config as AxiosRequestConfig & { _retried?: boolean };
          if (!config._retried) {
            config._retried = true;
            config.headers = { ...config.headers, Authorization: `Bearer ${newToken}` };
            return http.request(config);
          }
        }
        useAuthStore.getState().logoutLocal();
      }
      const error = new Error(body.msg || '请求失败') as Error & { code?: number };
      error.code = body.code;
      throw error;
    }
    return response;
  },
  async (error) => {
    // HTTP 层错误（网络等）：R 结构经 HTTP 200 返回，这里只处理非 2xx
    if (error.response) {
      const body = error.response.data as R<unknown>;
      const wrapped = new Error(body?.msg || '网络异常') as Error & { code?: number };
      wrapped.code = body?.code ?? 5000;
      throw wrapped;
    }
    throw error;
  },
);

/** 解包 R<T>：code!==0 抛错，否则返回 data */
export async function unwrap<T>(promise: Promise<{ data: R<T> }>): Promise<T> {
  const response = await promise;
  return response.data.data as T;
}
