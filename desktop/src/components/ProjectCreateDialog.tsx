import { FormEvent, useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { projectApi, systemApi } from '@/api';
import { toast } from '@/stores/toast';
import type { UserVo } from '@/api/types';

export interface ProjectCreateDialogProps {
  open: boolean;
  onClose: () => void;
  /** 创建成功回调（调用方刷新项目清单） */
  onCreated: (projectId: string) => void;
}

/** 创建项目（系统管理员专用）：名称 + 编号前缀 + 第一任项目分发者 */
export function ProjectCreateDialog({ open, onClose, onCreated }: ProjectCreateDialogProps) {
  const [projectName, setProjectName] = useState('');
  const [projectKey, setProjectKey] = useState('');
  const [remark, setRemark] = useState('');
  const [users, setUsers] = useState<UserVo[]>([]);
  const [distributorId, setDistributorId] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setProjectName('');
    setProjectKey('');
    setRemark('');
    setDistributorId('');
    // 拉取系统用户供选择第一任分发者（仅管理员可达此弹窗）
    systemApi
      .listUsers('', 1, 200)
      .then((page) => setUsers(page.list))
      .catch(() => setUsers([]));
  }, [open]);

  async function handleCreate() {
    if (!projectName.trim()) {
      toast('项目名称不能为空', 'error');
      return;
    }
    if (!/^[A-Z]{2,8}$/.test(projectKey.trim())) {
      toast('任务编号前缀需为 2~8 位大写字母', 'error');
      return;
    }
    if (!distributorId) {
      toast('请选择第一任项目分发者', 'error');
      return;
    }
    setSaving(true);
    try {
      const vo = await projectApi.create({
        projectName: projectName.trim(),
        projectKey: projectKey.trim(),
        distributorUserId: distributorId,
        remark: remark.trim() || undefined,
      });
      toast('项目已创建，已指定第一任分发者', 'success');
      onCreated(vo.id);
      onClose();
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog
      open={open}
      title="新建项目"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button variant="primary" disabled={saving} onClick={() => void handleCreate()}>
            创建
          </Button>
        </>
      }
    >
      <form className="space-y-3" onSubmit={(e: FormEvent) => e.preventDefault()}>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">项目名称 *</label>
          <Input value={projectName} onChange={(e) => setProjectName(e.target.value)} placeholder="如：数字银行研发" />
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">任务编号前缀 *（2~8 位大写字母，如 BANK）</label>
          <Input
            value={projectKey}
            onChange={(e) => setProjectKey(e.target.value.toUpperCase())}
            placeholder="BANK"
            maxLength={8}
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">第一任项目分发者 *</label>
          <Select
            value={distributorId}
            onChange={(e) => setDistributorId(e.target.value)}
            options={users.map((u) => ({ value: u.id, label: `${u.displayName}（${u.username}）` }))}
            placeholder="选择用户"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs text-zinc-500">备注</label>
          <Input value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="可选" />
        </div>
      </form>
    </Dialog>
  );
}
