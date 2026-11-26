import type { IsoDate, YearMonthString } from '@/types/domain'
import { lastDayOfMonth, parseYearMonth } from './period'

/**
 * 월 달력 격자 계산. 일요일 시작이다.
 *
 * `new Date('2026-11-01')` 은 UTC 로 해석되어 하루 밀릴 수 있으므로 연/월/일 숫자로만 계산한다.
 */

export interface CalendarCell {
  /** 앞뒤를 채우는 빈 칸이면 `null` */
  date: IsoDate | null
  day: number | null
}

/** 달력 격자. 7의 배수 칸이며 앞뒤는 빈 칸으로 채운다. */
export function buildMonthGrid(yearMonth: YearMonthString): CalendarCell[] {
  const { year, month } = parseYearMonth(yearMonth)
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const days = lastDayOfMonth(year, month)
  const monthPart = String(month).padStart(2, '0')

  const leading: CalendarCell[] = Array.from({ length: firstWeekday }, () => ({
    date: null,
    day: null,
  }))
  const body: CalendarCell[] = Array.from({ length: days }, (_, index) => ({
    date: `${year}-${monthPart}-${String(index + 1).padStart(2, '0')}`,
    day: index + 1,
  }))
  const trailingCount = (7 - ((leading.length + body.length) % 7)) % 7
  const trailing: CalendarCell[] = Array.from({ length: trailingCount }, () => ({
    date: null,
    day: null,
  }))

  return [...leading, ...body, ...trailing]
}

/** 날짜별로 묶는다. 서버가 준 순서(최근 등록 먼저)를 묶음 안에서 유지한다. */
export function groupByDate<T extends { spentDate: IsoDate }>(
  items: readonly T[],
): Map<IsoDate, T[]> {
  const grouped = new Map<IsoDate, T[]>()
  for (const item of items) {
    const bucket = grouped.get(item.spentDate)
    if (bucket) bucket.push(item)
    else grouped.set(item.spentDate, [item])
  }
  return grouped
}

/** `yyyy-MM-dd` 의 요일 이름. */
export function weekdayLabel(date: IsoDate): string {
  const [year, month, day] = date.split('-').map(Number)
  return '일월화수목금토'[new Date(year, month - 1, day).getDay()]
}

/** 그 달 안에서 기본으로 선택할 날. 오늘이 그 달이면 오늘, 아니면 1일. */
export function defaultSelectedDate(yearMonth: YearMonthString, today: IsoDate): IsoDate {
  return today.startsWith(yearMonth) ? today : `${yearMonth}-01`
}
