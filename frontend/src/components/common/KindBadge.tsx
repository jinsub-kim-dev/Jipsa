import { expenseKindColor, expenseKindShortLabel } from '@/lib/format'
import type { ExpenseKind } from '@/types/domain'

export function KindBadge({ kind, suffix }: { kind: ExpenseKind; suffix?: string }) {
  const color = expenseKindColor(kind)
  return (
    <span
      className="rounded-md px-2 py-0.5 text-[11px] font-semibold whitespace-nowrap"
      style={{ color, backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)` }}
    >
      {expenseKindShortLabel(kind)}
      {suffix ? ` ${suffix}` : ''}
    </span>
  )
}
