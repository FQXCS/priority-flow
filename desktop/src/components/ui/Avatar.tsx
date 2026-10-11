import clsx from 'clsx';

/** 姓名头像（取末字，色板按名散列） */
const PALETTE = ['bg-blue-500', 'bg-emerald-500', 'bg-amber-500', 'bg-violet-500', 'bg-rose-500', 'bg-cyan-500'];

export function Avatar({ name, size = 28 }: { name?: string | null; size?: number }) {
  const text = name?.trim() || '?';
  const last = text.charAt(text.length - 1);
  let hash = 0;
  for (const ch of text) {
    hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  }
  return (
    <span
      className={clsx(
        'inline-flex shrink-0 items-center justify-center rounded-full font-medium text-white',
        PALETTE[hash % PALETTE.length],
      )}
      style={{ width: size, height: size, fontSize: size * 0.42 }}
      title={text}
    >
      {last}
    </span>
  );
}
