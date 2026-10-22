/**
 * 后端服务器地址配置。
 * 打包后页面运行在 file:// 源，无法走 Vite 代理，需在登录页填写绝对地址；
 * Electron 下经主进程持久化到 userData/config.json，浏览器调试存 localStorage。
 */
const STORAGE_KEY = 'pf.serverBaseUrl';

let serverBaseUrl = '';

export function getServerBaseUrl(): string {
  return serverBaseUrl;
}

/** 拼接后端绝对地址；未配置时返回原相对路径（开发模式走 Vite 代理） */
export function apiUrl(path: string): string {
  return `${serverBaseUrl}${path}`;
}

/** 规范化地址：缺协议补 http://、去尾部斜杠；空串表示同源，格式非法返回 null */
export function normalizeServerUrl(raw: string): string | null {
  const value = raw.trim();
  if (!value) {
    return '';
  }
  const withScheme = /^https?:\/\//i.test(value) ? value : `http://${value}`;
  try {
    const url = new URL(withScheme);
    if (!url.hostname) {
      return null;
    }
    return withScheme.replace(/\/+$/, '');
  } catch {
    return null;
  }
}

interface ServerConfigApi {
  get: () => Promise<string | null>;
  set: (url: string) => Promise<void>;
}

function electronServerConfig(): ServerConfigApi | undefined {
  return (window as unknown as { electronAPI?: { serverConfig?: ServerConfigApi } }).electronAPI?.serverConfig;
}

/** 应用启动时加载已保存地址（渲染前调用） */
export async function initServerBaseUrl(): Promise<void> {
  try {
    const configApi = electronServerConfig();
    const saved = configApi ? await configApi.get() : localStorage.getItem(STORAGE_KEY);
    serverBaseUrl = saved ? (normalizeServerUrl(saved) ?? '') : '';
  } catch {
    serverBaseUrl = '';
  }
}

/**
 * 连接测试：请求后端健康接口，5 秒超时；仅当返回统一结构且 code=0 视为可用。
 * 保存地址前调用，测试不通过不保存。
 */
export async function testServerConnection(baseUrl: string): Promise<boolean> {
  const normalized = normalizeServerUrl(baseUrl);
  if (!normalized) {
    return false;
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const response = await fetch(`${normalized}/api/health`, { signal: controller.signal });
    if (!response.ok) {
      return false;
    }
    const body = (await response.json()) as { code?: number };
    return body.code === 0;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

/** 保存地址；格式非法返回 false */
export async function saveServerBaseUrl(raw: string): Promise<boolean> {
  const normalized = normalizeServerUrl(raw);
  if (normalized === null) {
    return false;
  }
  serverBaseUrl = normalized;
  const configApi = electronServerConfig();
  if (configApi) {
    await configApi.set(normalized);
  } else if (normalized) {
    localStorage.setItem(STORAGE_KEY, normalized);
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
  return true;
}
