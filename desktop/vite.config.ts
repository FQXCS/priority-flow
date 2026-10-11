import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// 开发模式经 Vite 代理访问后端（API + WebSocket）
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  base: './',
  // sockjs-client 为 CJS 包，裸引用 Node 的 global；开发模式预构建需注入垫片，否则渲染进程启动即抛
  // "Uncaught ReferenceError: global is not defined" 导致整个应用白屏（生产构建由 esbuild 自动垫片，不受影响）
  optimizeDeps: {
    esbuildOptions: {
      define: {
        global: 'globalThis',
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
  },
});
