import { describe, expect, it } from 'vitest'
import {
  emptyExpenseForm,
  formatAmountInput,
  parseAmount,
  toEditValues,
  toExpenseSubmission,
  toUpdateRequest,
  validateExpenseForm,
  type ExpenseFormValues,
} from './expenseForm'
import type { Transaction } from '@/types/domain'

const filled = (overrides: Partial<ExpenseFormValues> = {}): ExpenseFormValues => ({
  ...emptyExpenseForm('2026-11-26'),
  amount: '9,000',
  categoryId: 1,
  merchant: ' 김밥천국 ',
  tags: ['점심'],
  ...overrides,
})

const transaction = (overrides: Partial<Transaction> = {}): Transaction => ({
  id: 1,
  kind: 'GENERAL',
  categoryId: 1,
  amount: 9000,
  merchant: '김밥천국',
  spentDate: '2026-11-26',
  tags: ['점심'],
  memo: '메모',
  paymentMethod: '신한카드',
  locked: false,
  deletable: true,
  ...overrides,
})

describe('expenseForm - 지출 추가 폼', () => {
  it.each([
    ['9,000', 9000],
    [' 12000 ', 12000],
    ['0', 0],
    ['', null],
    ['9천', null],
    ['-100', null],
  ])('금액 %s 를 %s 로 읽는다', (raw, expected) => {
    expect(parseAmount(raw)).toBe(expected)
  })

  it('입력하는 동안 천 단위 쉼표를 넣는다', () => {
    expect(formatAmountInput('1234567')).toBe('1,234,567')
    expect(formatAmountInput('12a3')).toBe('123')
    expect(formatAmountInput('')).toBe('')
  })

  it('필수 입력이 비어 있으면 알린다', () => {
    const errors = validateExpenseForm({ ...emptyExpenseForm(''), merchant: ' ' })

    expect(Object.keys(errors).sort()).toEqual(['amount', 'categoryId', 'merchant', 'spentDate'])
    expect(validateExpenseForm(filled())).toEqual({})
  })

  it('유효하지 않은 폼은 요청으로 바꾸지 않는다', () => {
    expect(() => toExpenseSubmission(emptyExpenseForm('2026-11-26'))).toThrow(
      '폼이 유효하지 않습니다',
    )
  })

  it('일반 지출은 거래 등록 요청이 된다', () => {
    expect(toExpenseSubmission(filled({ memo: ' ', paymentMethod: '현금' }))).toEqual({
      kind: 'GENERAL',
      request: {
        categoryId: 1,
        amount: 9000,
        merchant: '김밥천국',
        spentDate: '2026-11-26',
        tags: ['점심'],
        memo: undefined,
        paymentMethod: '현금',
      },
    })
  })

  it('고정비는 고른 날짜의 일을 납부일, 월을 시작월로 쓴다', () => {
    const submission = toExpenseSubmission(
      filled({ kind: 'FIXED', spentDate: '2026-12-05', amount: '17000' }),
    )

    expect(submission).toEqual({
      kind: 'FIXED',
      request: {
        categoryId: 1,
        merchant: '김밥천국',
        amount: 17000,
        dayOfMonth: 5,
        startMonth: '2026-12',
        tags: ['점심'],
        paymentMethod: undefined,
      },
    })
  })

  it('할부는 입력 금액을 총액으로, 고른 날짜를 1회차로 쓴다', () => {
    const submission = toExpenseSubmission(
      filled({
        kind: 'INSTALLMENT',
        amount: '288,000',
        installmentMonths: 3,
        spentDate: '2026-11-15',
      }),
    )

    expect(submission).toEqual({
      kind: 'INSTALLMENT',
      request: {
        categoryId: 1,
        merchant: '김밥천국',
        totalAmount: 288000,
        installmentMonths: 3,
        firstBillingMonth: '2026-11',
        billingDay: 15,
        tags: ['점심'],
        paymentMethod: undefined,
      },
    })
  })
})

describe('expenseForm - 거래 수정', () => {
  it('바뀌지 않았으면 빈 요청이다', () => {
    const original = transaction()

    expect(toUpdateRequest(original, toEditValues(original))).toEqual({})
  })

  it('바뀐 필드만 담는다', () => {
    const original = transaction()
    const values = {
      ...toEditValues(original),
      amount: '12,000',
      tags: ['저녁'],
      merchant: '본죽 ',
    }

    expect(toUpdateRequest(original, values)).toEqual({
      amount: 12000,
      tags: ['저녁'],
      merchant: '본죽',
    })
  })

  it('소비일·카테고리·결제 수단 변경을 담는다', () => {
    const original = transaction()
    const values = {
      ...toEditValues(original),
      spentDate: '2026-11-25',
      categoryId: 2,
      paymentMethod: '현금',
    }

    expect(toUpdateRequest(original, values)).toEqual({
      spentDate: '2026-11-25',
      categoryId: 2,
      paymentMethod: '현금',
    })
  })

  it('메모를 지우면 clearMemo 로 보낸다', () => {
    const original = transaction()

    expect(toUpdateRequest(original, { ...toEditValues(original), memo: '  ' })).toEqual({
      clearMemo: true,
    })
    expect(toUpdateRequest(original, { ...toEditValues(original), memo: '새 메모' })).toEqual({
      memo: '새 메모',
    })
  })

  it('메모와 결제 수단이 없던 거래도 다룬다', () => {
    const original = transaction({ memo: undefined, paymentMethod: undefined })
    const values = toEditValues(original)

    expect(values.memo).toBe('')
    expect(toUpdateRequest(original, { ...values, paymentMethod: '  ' })).toEqual({})
  })

  it('잠긴 할부 회차는 금액과 소비일을 보내지 않는다', () => {
    const original = transaction({ kind: 'INSTALLMENT', locked: true, deletable: false })
    const values = {
      ...toEditValues(original),
      amount: '1',
      spentDate: '2026-01-01',
      memo: '회사 지원',
    }

    expect(toUpdateRequest(original, values)).toEqual({ memo: '회사 지원' })
  })

  it('금액이 숫자가 아니면 보내지 않는다', () => {
    const original = transaction()

    expect(toUpdateRequest(original, { ...toEditValues(original), amount: '만원' })).toEqual({})
  })
})
