import { describe, expect, it } from 'vitest';
import { resolveSortRef } from './dragSortRef';

/** 拖拽落点引用解析单测（契约：beforeId=插到它前面 / afterId=插到它后面，与后端 TaskSortDto 一致） */
describe('dragSortRef 落点解析', () => {
  const list = [{ id: 'A' }, { id: 'B' }, { id: 'C' }];

  it('向上拖：落到目标卡片之前（中间插入不得越位到队首）', () => {
    // 回归场景：队尾 C 拖到 A、B 之间 → 应落在 B 前（A 之后），而不是 A 之前（队首）
    expect(resolveSortRef(list, 'C', 'B')).toEqual({ beforeId: 'B' });
  });

  it('向上拖到队首：落到第一张卡片之前', () => {
    expect(resolveSortRef(list, 'C', 'A')).toEqual({ beforeId: 'A' });
  });

  it('向下拖：落到目标卡片之后', () => {
    expect(resolveSortRef(list, 'A', 'C')).toEqual({ afterId: 'C' });
  });

  it('落点为拖拽任务自身：原位，无需请求', () => {
    expect(resolveSortRef(list, 'B', 'B')).toBeNull();
  });

  it('落点为列空白处：尾插', () => {
    expect(resolveSortRef(list, 'A', undefined)).toEqual({});
  });

  it('落点不在当前列表（异常兜底）：尾插', () => {
    expect(resolveSortRef(list, 'A', 'X')).toEqual({});
  });
});
