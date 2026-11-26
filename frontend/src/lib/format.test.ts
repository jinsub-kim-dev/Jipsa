import { describe, expect, it } from 'vitest'
import {
  expenseKindColor,
  expenseKindLabel,
  expenseKindShortLabel,
  formatKrw,
  formatKrwCompact,
  formatMonthDay,
  formatMonthShort,
  formatPercentage,
  formatSignedPercentage,
  formatTag,
  formatYearMonth,
  installmentLabel,
} from './format'
import type { ExpenseKind } from '@/types/domain'

describe('format - 표시 포맷', () => {
  it('금액을 원화로 표기한다', () => {
    expect(formatKrw(1234567)).toBe('1,234,567원')
    expect(formatKrw(0)).toBe('0원')
  })

  it.each([
    [9_000, '9,000'],
    [10_000, '1만'],
    [3_264_820, '326.5만'],
    [120_000_000, '1.2억'],
    [-15_000, '-1.5만'],
  ])('%d 원을 %s 로 축약한다', (amount, expected) => {
    expect(formatKrwCompact(amount)).toBe(expected)
  })

  it('날짜와 연월을 표기한다', () => {
    expect(formatMonthDay('2026-11-26')).toBe('11.26')
    expect(formatYearMonth('2026-11')).toBe('2026년 11월')
    expect(formatMonthShort('2026-06')).toBe('6월')
  })

  it('날짜 형식이 틀리면 알린다', () => {
    expect(() => formatMonthDay('2026-11')).toThrow('yyyy-MM-dd')
  })

  it('비율은 정의되지 않으면 대시로 표기한다', () => {
    expect(formatPercentage(52.34)).toBe('52.3%')
    expect(formatPercentage(undefined)).toBe('–')
  })

  it.each([
    [12.5, '+12.5%'],
    [-30, '-30.0%'],
    [0, '0.0%'],
  ])('증감률 %d 를 부호와 함께 %s 로 표기한다', (rate, expected) => {
    expect(formatSignedPercentage(rate)).toBe(expected)
  })

  it('정의되지 않은 증감률은 0% 가 아니라 신규로 표기한다', () => {
    expect(formatSignedPercentage(undefined)).toBe('신규')
    expect(formatSignedPercentage(undefined, 'NEW')).toBe('NEW')
  })

  it.each<[ExpenseKind, string, string, string]>([
    ['GENERAL', '일반 지출', '일반', 'general'],
    ['FIXED', '고정비', '고정', 'fixed'],
    ['INSTALLMENT', '할부', '할부', 'installment'],
  ])('%s 의 이름과 색을 준다', (kind, label, short, token) => {
    expect(expenseKindLabel(kind)).toBe(label)
    expect(expenseKindShortLabel(kind)).toBe(short)
    expect(expenseKindColor(kind)).toBe(`var(--color-kind-${token})`)
  })

  it('할부 회차와 태그를 표기한다', () => {
    expect(installmentLabel(9, 12)).toBe('9/12')
    expect(formatTag('점심')).toBe('#점심')
  })
})
