import { create } from 'zustand';

/**
 * 全局轻提示（Toast）。
 */
export interface ToastItem {
  id: number;
  text: string;
  type: 'info' | 'success' | 'error';
}

interface ToastState {
  toasts: ToastItem[];
  push: (text: string, type?: ToastItem['type']) => void;
  remove: (id: number) => void;
}

let seq = 1;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (text, type = 'info') => {
    const id = seq++;
    set((state) => ({ toasts: [...state.toasts, { id, text, type }] }));
    setTimeout(() => {
      set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
    }, 3200);
  },
  remove: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));

export function toast(text: string, type: ToastItem['type'] = 'info'): void {
  useToastStore.getState().push(text, type);
}
