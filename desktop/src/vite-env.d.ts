/// <reference types="vite/client" />

/** Electron preload 暴露的 API 形状 */
interface Window {
  electronAPI?: {
    secureStore: {
      set: (key: string, value: string) => Promise<void>;
      get: (key: string) => Promise<string | null>;
      clear: () => Promise<void>;
    };
    notify: (title: string, body: string) => Promise<void>;
    onThemeChange: (callback: (theme: string) => void) => void;
    isElectron: boolean;
  };
}
