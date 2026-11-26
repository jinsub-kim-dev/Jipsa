import type { ExpenseKind, Transaction } from '@/types/domain'

/** 캘린더 하이라이트 조건. 지출 종류 하나 또는 태그 하나다. 없으면 전체. */
export type CalendarHighlight =
  { type: 'kind'; value: ExpenseKind } | { type: 'tag'; value: string } | null

export function matchesHighlight(
  transaction: Pick<Transaction, 'kind' | 'tags'>,
  highlight: CalendarHighlight,
): boolean {
  if (highlight === null) return true
  return highlight.type === 'kind'
    ? transaction.kind === highlight.value
    : transaction.tags.includes(highlight.value)
}
