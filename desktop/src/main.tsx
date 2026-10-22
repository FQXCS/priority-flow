import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { initServerBaseUrl } from './api/server';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

/** 先加载服务器地址再渲染：打包后为 file:// 源，路由与接口都依赖已配置的后端地址 */
async function bootstrap(): Promise<void> {
  await initServerBaseUrl();
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <QueryClientProvider client={queryClient}>
        {/* HashRouter：file:// 下 history.pushState 不可用 */}
        <HashRouter>
          <App />
        </HashRouter>
      </QueryClientProvider>
    </React.StrictMode>,
  );
}

void bootstrap();
