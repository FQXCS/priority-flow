import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { getServerBaseUrl, normalizeServerUrl, saveServerBaseUrl, testServerConnection } from '@/api/server';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';

/** 登录页 */
export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [serverUrl, setServerUrl] = useState(() => getServerBaseUrl());
  const [serverDialogOpen, setServerDialogOpen] = useState(false);
  const [serverDraft, setServerDraft] = useState('');
  const [serverError, setServerError] = useState('');
  const [testing, setTesting] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  function openServerDialog() {
    setServerDraft(serverUrl);
    setServerError('');
    setTesting(false);
    setServerDialogOpen(true);
  }

  async function saveServer() {
    if (testing) {
      return;
    }
    const normalized = normalizeServerUrl(serverDraft);
    if (normalized === null) {
      setServerError('地址格式不正确，示例：http://192.168.1.10:8080');
      return;
    }
    // 打包后 file:// 源无 Vite 代理，必须配置后端绝对地址；开发模式留空即走代理
    if (!normalized && !import.meta.env.DEV) {
      setServerError('请填写服务器地址');
      return;
    }
    if (normalized) {
      setServerError('');
      setTesting(true);
      const reachable = await testServerConnection(normalized);
      setTesting(false);
      if (!reachable) {
        setServerError('连接失败，请检查服务器地址及服务是否已启动');
        return;
      }
    }
    if (!(await saveServerBaseUrl(normalized))) {
      setServerError('地址格式不正确，示例：http://192.168.1.10:8080');
      return;
    }
    setServerUrl(getServerBaseUrl());
    setServerDialogOpen(false);
    toast(normalized ? '连接成功，服务器地址已保存' : '服务器地址已保存', 'success');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!serverUrl.trim() && !import.meta.env.DEV) {
      toast('请先设置服务器地址', 'error');
      openServerDialog();
      return;
    }
    if (!username.trim() || !password) {
      toast('请输入账号与密码', 'error');
      return;
    }
    setLoading(true);
    try {
      await login(username.trim(), password);
      toast('登录成功', 'success');
      navigate('/board', { replace: true });
    } catch (err) {
      toast((err as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid h-screen place-items-center bg-gradient-to-br from-blue-50 via-white to-indigo-100 dark:from-zinc-900 dark:via-zinc-900 dark:to-zinc-800">
      <form
        onSubmit={(e) => void handleSubmit(e)}
        className="w-96 space-y-4 rounded-2xl bg-white p-8 shadow-xl dark:bg-zinc-900"
      >
        <div className="text-center">
          <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">Priority Flow</div>
          <div className="mt-1 text-xs text-zinc-400">研发任务优先级看板</div>
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">账号</label>
          <Input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="用户名" autoFocus />
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">密码</label>
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="密码" />
        </div>
        <Button type="submit" variant="primary" className="w-full" disabled={loading}>
          {loading ? '登录中…' : '登 录'}
        </Button>
        <div className="flex items-center justify-between gap-2 text-[11px]">
          <span className="truncate text-zinc-400" title={serverUrl || undefined}>
            服务器：{serverUrl || '未设置'}
          </span>
          <button
            type="button"
            className="shrink-0 text-zinc-500 hover:text-blue-600 dark:hover:text-blue-400"
            onClick={openServerDialog}
          >
            ⚙ 服务器设置
          </button>
        </div>
        <p className="text-center text-[11px] text-zinc-400">首次启动已创建管理员：admin / admin123</p>
      </form>

      <Dialog
        open={serverDialogOpen}
        title="服务器设置"
        onClose={() => setServerDialogOpen(false)}
        width="max-w-sm"
        footer={
          <>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={testing}
              onClick={() => setServerDialogOpen(false)}
            >
              取消
            </Button>
            <Button type="button" variant="primary" size="sm" disabled={testing} onClick={() => void saveServer()}>
              {testing ? '连接测试中…' : '保存'}
            </Button>
          </>
        }
      >
        <div className="space-y-2">
          <label className="block text-xs text-zinc-500">服务器地址</label>
          <Input
            value={serverDraft}
            invalid={!!serverError}
            placeholder="http://192.168.1.10:8080"
            autoFocus
            disabled={testing}
            onChange={(e) => {
              setServerDraft(e.target.value);
              setServerError('');
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                void saveServer();
              }
            }}
          />
          {serverError ? (
            <p className="text-[11px] text-red-500">{serverError}</p>
          ) : (
            <p className="text-[11px] text-zinc-400">保存前会测试连接；开发调试可留空走本地代理。</p>
          )}
        </div>
      </Dialog>
    </div>
  );
}
