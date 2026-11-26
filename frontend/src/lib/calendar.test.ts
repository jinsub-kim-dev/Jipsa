import { describe, expect, it } from 'vitest'
import { buildMonthGrid, defaultSelectedDate, groupByDate, weekdayLabel } from './calendar'

describe('calendar - 월 달력 격자', () => {
  it('2026년 11월은 일요일에 시작하고 30일까지다', () => {
    const grid = buildMonthGrid('2026-11')

    expect(grid[0]).toEqual({ date: '2026-11-01', day: 1 })
    expect(grid.filter((cell) => cell.date !== null)).toHaveLength(30)
    expect(grid.length % 7).toBe(0)
  })

  it('첫날 요일만큼 앞을 빈 칸으로 채운다', () => {
    // 2026-12-01 은 화요일
    const grid = buildMonthGrid('2026-12')

    expect(grid.slice(0, 2).every((cell) => cell.date === null)).toBe(true)
    expect(grid[2].date).toBe('2026-12-01')
  })

  it('윤년 2월은 29일까지다', () => {
    expect(
      buildMonthGrid('2028-02')
        .filter((cell) => cell.day !== null)
        .at(-1)?.date,
    ).toBe('2028-02-29')
  })

  it('딱 맞아떨어지는 달은 뒤를 채우지 않는다', () => {
    // 2026-02 는 일요일 시작, 28일 → 정확히 4주
    expect(buildMonthGrid('2026-02')).toHaveLength(28)
  })

  it('날짜별로 묶고 묶음 안의 순서를 유지한다', () => {
    const grouped = groupByDate([
      { spentDate: '2026-11-26', id: 3 },
      { spentDate: '2026-11-25', id: 2 },
      { spentDate: '2026-11-26', id: 1 },
    ])

    expect(grouped.get('2026-11-26')?.map((item) => item.id)).toEqual([3, 1])
    expect(grouped.get('2026-11-25')).toHaveLength(1)
  })

  it('요일 이름을 로컬 날짜 기준으로 준다', () => {
    expect(weekdayLabel('2026-11-26')).toBe('목')
    expect(weekdayLabel('2026-11-01')).toBe('일')
  })

  it('오늘이 그 달이면 오늘을, 아니면 1일을 고른다', () => {
    expect(defaultSelectedDate('2026-11', '2026-11-26')).toBe('2026-11-26')
    expect(defaultSelectedDate('2026-10', '2026-11-26')).toBe('2026-10-01')
  })
})
