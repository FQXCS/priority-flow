import { useEffect, useState } from 'react';
import type { MilestoneSaveBody } from '@/api';
import type { MilestoneVo, ProjectMemberVo } from '@/api/types';
import { toast } from '@/stores/toast';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

interface Props {
  open: boolean;
  milestone: MilestoneVo | null;
  prefillDate?: string;
  members: ProjectMemberVo[];
  saving: boolean;
  deleting: boolean;
  onSave: (body: MilestoneSaveBody) => void;
  onDelete: () => void;
  onClose: () => void;
}

/** 新增/编辑共用弹窗：编辑模式额外提供删除入口 */
export function MilestoneFormDialog(props: Props) {
  const { open, milestone, prefillDate, members, saving, deleting, onSave, onDelete, onClose } = props;
  const editing = !!milestone;

  const [name, setName] = useState('');
  const [ownerId, setOwnerId] = useState('');
  const [expectedDate, setExpectedDate] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setName(milestone?.name ?? '');
    setOwnerId(milestone?.ownerId ?? '');
    setExpectedDate(milestone?.expectedDate ?? prefillDate ?? '');
  }, [open, milestone, prefillDate]);

  const handleSave = () => {
    if (!name.trim()) {
      toast('目标名称不能为空', 'error');
      return;
    }
    onSave({
      name: name.trim(),
      ownerId: ownerId || undefined,
      expectedDate: expectedDate || undefined,
    });
  };

  return (
    <Dialog
      open={open}
      title={editing ? '编辑里程碑' : '新增里程碑'}
      onClose={onClose}
      footer={
        <>
          {editing && (
            <Button variant="danger" className="mr-auto" disabled={deleting} onClick={onDelete}>
              删除
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button variant="primary" disabled={saving} onClick={handleSave}>
            {editing ? '保存修改' : '创建里程碑'}
          </Button>
        </>
      }
    >
      {editing && <div className="mb-3 text-sm text-zinc-400">{milestone?.name}</div>}
      <label className="mb-1 block text-xs text-zinc-500">目标名称 *</label>
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="例如：完成项目联调验收"
        maxLength={255}
      />
      <label className="mb-1 mt-3 block text-xs text-zinc-500">负责人</label>
      <Select
        value={ownerId}
        onChange={(e) => setOwnerId(e.target.value)}
        options={[
          { value: '', label: '未指定' },
          ...members.map((m) => ({ value: m.userId, label: m.user?.displayName ?? m.userId })),
        ]}
      />
      <label className="mb-1 mt-3 block text-xs text-zinc-500">预计完成日期</label>
      <Input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} />
      <div className="mt-1 text-xs text-zinc-400">留空将放入未规划日期</div>
    </Dialog>
  );
}
