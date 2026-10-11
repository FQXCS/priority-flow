import { contextBridge, ipcRenderer } from 'electron';

/**
 * 预加载脚本：以受控白名单方式暴露主进程能力给渲染进程。
 */
contextBridge.exposeInMainWorld('electronAPI', {
  /** token 经 safeStorage 加密持久化 */
  secureStore: {
    set: (key: string, value: string) => ipcRenderer.invoke('secure-store:set', key, value),
    get: (key: string) => ipcRenderer.invoke('secure-store:get', key),
    clear: () => ipcRenderer.invoke('secure-store:clear'),
  },
  /** 系统通知（Electron Notification API） */
  notify: (title: string, body: string) => ipcRenderer.invoke('notify', title, body),
  /** 主题跟随系统 */
  onThemeChange: (callback: (theme: string) => void) => {
    ipcRenderer.on('theme-changed', (_event, theme: string) => callback(theme));
  },
  /** 是否运行在 Electron 环境 */
  isElectron: true,
});
