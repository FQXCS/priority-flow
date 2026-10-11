import { create } from 'zustand';

/**
 * UI 偏好：主题（亮/暗/跟随系统）、视图密度。
 */
interface UiState {
  theme: 'light' | 'dark' | 'system';
  density: 'compact' | 'comfortable';
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
  setDensity: (density: 'compact' | 'comfortable') => void;
}

function applyTheme(theme: 'light' | 'dark' | 'system'): void {
  const dark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.toggle('dark', dark);
}

const saved = (localStorage.getItem('pf.theme') as 'light' | 'dark' | 'system' | null) ?? 'system';
const savedDensity = (localStorage.getItem('pf.density') as 'compact' | 'comfortable' | null) ?? 'comfortable';
applyTheme(saved);

export const useUiStore = create<UiState>((set, get) => ({
  theme: saved,
  density: savedDensity,
  setTheme: (theme) => {
    localStorage.setItem('pf.theme', theme);
    applyTheme(theme);
    set({ theme });
  },
  setDensity: (density) => {
    localStorage.setItem('pf.density', density);
    set({ density });
  },
}));

// Electron 环境跟随系统主题变化
if ((window as unknown as { electronAPI?: { onThemeChange: (cb: (t: string) => void) => void } }).electronAPI) {
  (window as unknown as { electronAPI: { onThemeChange: (cb: (t: string) => void) => void } }).electronAPI.onThemeChange(
    (systemTheme) => {
      if (useUiStore.getState().theme === 'system') {
        document.documentElement.classList.toggle('dark', systemTheme === 'dark');
      }
    },
  );
}
