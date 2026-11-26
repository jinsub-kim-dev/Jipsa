import type { ExpenseKind, IsoDate, YearMonthString } from '@/types/domain'
import { parseYearMonth } from './period'

/**
 * 표시 포맷 유틸리티. 계산하지 않고 서버가 준 값을 읽기 좋게 바꾸기만 한다.
 */

/** 금액을 원화로 표기한다. 예: `1,234,567원` */
export function formatKrw(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`
}

/** 금액을 만원 단위로 축약한다. 차트 축처럼 좁은 공간에 쓴다. 예: `326.5만`, `1.2억` */
export function formatKrwCompact(amount: number): string {
  const absolute = Math.abs(amount)
  const sign = amount < 0 ? '-' : ''
  if (absolute >= 100_000_000) return `${sign}${trimZero(absolute / 100_000_000)}억`
  if (absolute >= 10_000) return `${sign}${trimZero(absolute / 10_000)}만`
  return `${sign}${absolute.toLocaleString('ko-KR')}`
}

function trimZero(value: number): string {
  return value.toFixed(1).replace(/\.0$/, '')
}

/** `yyyy-MM-dd` 를 `MM.dd` 로 표기한다. */
export function formatMonthDay(date: IsoDate): string {
  const parts = date.split('-')
  if (parts.length !== 3) {
    throw new Error(`날짜 형식이 올바르지 않습니다: ${date} (yyyy-MM-dd 이어야 합니다)`)
  }
  return `${parts[1]}.${parts[2]}`
}

/** `yyyy-MM` 을 `2026년 11월` 로 표기한다. */
export function formatYearMonth(yearMonth: YearMonthString): string {
  const { year, month } = parseYearMonth(yearMonth)
  return `${year}년 ${month}월`
}

/** `yyyy-MM` 을 `11월` 로 표기한다. 차트 축에 쓴다. */
export function formatMonthShort(yearMonth: YearMonthString): string {
  return `${parseYearMonth(yearMonth).month}월`
}

/** 비율 표기. 정의되지 않으면(전체 0원) `–`. 예: `52.3%` */
export function formatPercentage(percentage: number | undefined): string {
  return percentage === undefined ? '–' : `${percentage.toFixed(1)}%`
}

/**
 * 증감률 표기. 부호를 드러낸다. 예: `+12.5%`, `-30.0%`
 *
 * 정의되지 않은 증감률(이전 값 0원)은 **0% 가 아니라 [undefinedLabel]** 이다.
 * 0% 로 쓰면 "변동 없음" 으로 정반대로 읽힌다 (PRD 4절).
 */
export function formatSignedPercentage(
  percentage: number | undefined,
  undefinedLabel = '신규',
): string {
  if (percentage === undefined) return undefinedLabel
  const sign = percentage > 0 ? '+' : ''
  return `${sign}${percentage.toFixed(1)}%`
}

export function expenseKindLabel(kind: ExpenseKind): string {
  switch (kind) {
    case 'GENERAL':
      return '일반 지출'
    case 'FIXED':
      return '고정비'
    case 'INSTALLMENT':
      return '할부'
  }
}

export function expenseKindShortLabel(kind: ExpenseKind): string {
  switch (kind) {
    case 'GENERAL':
      return '일반'
    case 'FIXED':
      return '고정'
    case 'INSTALLMENT':
      return '할부'
  }
}

/** 지출 종류의 색 토큰(index.css). 화면 어디서나 같은 종류를 같은 색으로 그린다. */
export function expenseKindColor(kind: ExpenseKind): string {
  switch (kind) {
    case 'GENERAL':
      return 'var(--color-kind-general)'
    case 'FIXED':
      return 'var(--color-kind-fixed)'
    case 'INSTALLMENT':
      return 'var(--color-kind-installment)'
  }
}

/** 할부 회차 표기. 예: `9/12` */
export function installmentLabel(sequence: number, totalMonths: number): string {
  return `${sequence}/${totalMonths}`
}

/** 태그 표기. 서버는 `#` 없이 저장한다. 예: `#점심` */
export function formatTag(tag: string): string {
  return `#${tag}`
}
