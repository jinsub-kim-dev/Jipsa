import { describe, expect, it } from 'vitest'
import { conversionConfirmMessage, conversionSummary } from './fixedExpenseConversion'
import type { FixedExpenseConversionResult } from '@/types/api'
import type { FixedExpense, Transaction } from '@/types/domain'

const fixedExpense: FixedExpense = {
  id: 4,
  categoryId: 9,
  merchant: 'OO보험',
  amount: 15_000,
  dayOfMonth: 27,
  tags: [],
  startMonth: '2026-09',
  month: '2026-11',
  activeInMonth: true,
  dueDateInMonth: '2026-11-27',
}

function transaction(id: number): Transaction {
  return {
    id,
    kind: 'FIXED',
    categoryId: 9,
    amount: 15_000,
    merchant: 'OO보험',
    spentDate: '2026-10-27',
    tags: [],
    fixedExpenseId: 4,
    locked: false,
    deletable: false,
  }
}

function result(
  overrides: Partial<FixedExpenseConversionResult> = {},
): FixedExpenseConversionResult {
  return {
    fixedExpense,
    convertedTransactionCount: 1,
    convertedTransactions: [transaction(1)],
    ambiguousMonths: [],
    ...overrides,
  }
}

describe('fixedExpenseConversion - 전환 결과 문장', () => {
  it('고른 거래 하나만 바뀌면 그렇게 알린다', () => {
    expect(conversionSummary(result())).toEqual([
      'OO보험을(를) 매월 27일 고정비로 등록했어요.',
      '이 거래를 고정비 거래로 바꿨어요.',
    ])
  })

  it('같은 사용처의 거래가 함께 바뀌면 건수를 알린다', () => {
    const summary = conversionSummary(
      result({ convertedTransactionCount: 3, convertedTransactions: [1, 2, 3].map(transaction) }),
    )

    expect(summary[1]).toBe('같은 사용처의 거래까지 3건을 고정비 거래로 바꿨어요.')
  })

  it('그대로 둔 달이 있으면 그 달들을 알린다', () => {
    const summary = conversionSummary(result({ ambiguousMonths: ['2026-08', '2026-09'] }))

    expect(summary).toHaveLength(3)
    expect(summary[2]).toBe('2026년 8월, 2026년 9월은(는) 같은 사용처가 여러 건이라 그대로 뒀어요.')
  })

  it('전환 전에 다른 거래도 함께 바뀐다는 것을 묻는다', () => {
    const message = conversionConfirmMessage('OO보험')

    expect(message).toContain('OO보험을(를) 고정비로 전환할까요?')
    expect(message).toContain('같은 사용처의 다른 달 일반 지출도 함께')
  })
})
