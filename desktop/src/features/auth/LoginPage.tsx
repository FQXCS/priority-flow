import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';

/** 登录页 */
export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
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
        <p className="text-center text-[11px] text-zinc-400">首次启动已创建管理员：admin / admin123</p>
      </form>
    </div>
  );
}
