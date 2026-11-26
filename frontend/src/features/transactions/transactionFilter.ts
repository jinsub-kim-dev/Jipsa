import { EXPENSE_KINDS, type ExpenseKind } from '@/types/domain'

/**
 * 거래 목록 필터. **URL 쿼리 파라미터에 담는다** (frontend/CLAUDE.md 3.2).
 *
 * 대시보드의 히트맵 행이나 태그를 눌러 그 내역으로 들어가는 드릴다운이 이 가계부의 복기
 * 흐름이고, 그러려면 필터가 주소에 담겨야 한다. 뒤로 가기와 새로고침도 자연히 동작한다.
 * 연월은 전역 스토어가 갖는다(모든 메뉴가 공유).
 */
export interface TransactionFilter {
  kind?: ExpenseKind
  categoryId?: number
  tag?: string
  keyword?: string
}

/** 주소의 쿼리를 필터로 읽는다. 알 수 없거나 잘못된 값은 무시한다. */
export function parseTransactionFilter(params: URLSearchParams): TransactionFilter {
  const filter: TransactionFilter = {}

  const kind = params.get('kind')
  if (kind && (EXPENSE_KINDS as readonly string[]).includes(kind)) filter.kind = kind as ExpenseKind

  const categoryId = Number(params.get('categoryId'))
  if (Number.isInteger(categoryId) && categoryId > 0) filter.categoryId = categoryId

  const tag = params.get('tag')?.trim()
  if (tag) filter.tag = tag

  const keyword = params.get('keyword')?.trim()
  if (keyword) filter.keyword = keyword

  return filter
}

/** 필터를 쿼리로 쓴다. 값이 없는 조건은 주소에 남기지 않는다. */
export function serializeTransactionFilter(filter: TransactionFilter): URLSearchParams {
  const params = new URLSearchParams()
  if (filter.kind) params.set('kind', filter.kind)
  if (filter.categoryId) params.set('categoryId', String(filter.categoryId))
  if (filter.tag) params.set('tag', filter.tag)
  if (filter.keyword) params.set('keyword', filter.keyword)
  return params
}

/** 거래 목록으로 드릴다운하는 주소. 대시보드·캘린더에서 쓴다. */
export function transactionsLink(filter: TransactionFilter): string {
  const query = serializeTransactionFilter(filter).toString()
  return query ? `/transactions?${query}` : '/transactions'
}

/** 필터가 하나라도 걸려 있는지. */
export function hasActiveFilter(filter: TransactionFilter): boolean {
  return Object.values(filter).some((value) => value !== undefined)
}
