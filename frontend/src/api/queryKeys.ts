import type { TransactionSearchParams } from '@/types/api'
import type { YearMonthString } from '@/types/domain'

/**
 * 쿼리 키 팩토리.
 *
 * 키를 한곳에서 만들면 무효화(invalidate) 범위를 실수 없이 지정할 수 있다.
 */
export const queryKeys = {
  categories: {
    all: ['categories'] as const,
  },
  transactions: {
    all: ['transactions'] as const,
    search: (params: TransactionSearchParams) => ['transactions', 'search', params] as const,
  },
  fixedExpenses: {
    all: ['fixedExpenses'] as const,
    list: (month: YearMonthString) => ['fixedExpenses', 'list', month] as const,
  },
  installmentPlans: {
    all: ['installmentPlans'] as const,
    list: (month: YearMonthString) => ['installmentPlans', 'list', month] as const,
  },
  statistics: {
    all: ['statistics'] as const,
    monthlySummary: (month: YearMonthString) => ['statistics', 'monthlySummary', month] as const,
    kindTrend: (from: YearMonthString, to: YearMonthString) =>
      ['statistics', 'kindTrend', from, to] as const,
    categoryTrend: (from: YearMonthString, to: YearMonthString, target: YearMonthString) =>
      ['statistics', 'categoryTrend', from, to, target] as const,
    tags: (month: YearMonthString, limit: number) => ['statistics', 'tags', month, limit] as const,
    insight: (month: YearMonthString) => ['statistics', 'insight', month] as const,
    commitmentForecast: (month: YearMonthString, months: number) =>
      ['statistics', 'commitmentForecast', month, months] as const,
  },
} as const

/**
 * 지출이 바뀌면 함께 낡는 영역.
 *
 * 고정비·할부 등록은 거래를 만들고, 거래 변경은 모든 통계를 바꾼다. 한 번에 무효화한다.
 */
export const expenseAffectedKeys = [
  queryKeys.transactions.all,
  queryKeys.statistics.all,
  queryKeys.fixedExpenses.all,
  queryKeys.installmentPlans.all,
] as const
