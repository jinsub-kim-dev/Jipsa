import { beforeEach, describe, expect, it } from 'vitest'
import { currentYearMonth } from '@/lib/period'
import { trendRangeOf, usePeriodStore } from './periodStore'

describe('periodStore - 조회 연월', () => {
  beforeEach(() => {
    usePeriodStore.setState({ yearMonth: '2026-11' })
  })

  it('이전 달과 다음 달로 이동하며 해를 넘긴다', () => {
    usePeriodStore.getState().goToNextMonth()
    usePeriodStore.getState().goToNextMonth()
    expect(usePeriodStore.getState().yearMonth).toBe('2027-01')

    usePeriodStore.getState().goToPreviousMonth()
    expect(usePeriodStore.getState().yearMonth).toBe('2026-12')
  })

  it('연월을 직접 정하고 이번 달로 돌아온다', () => {
    usePeriodStore.getState().setYearMonth('2026-06')
    expect(usePeriodStore.getState().yearMonth).toBe('2026-06')

    usePeriodStore.getState().goToCurrentMonth()
    expect(usePeriodStore.getState().yearMonth).toBe(currentYearMonth())
  })

  it('추이 구간은 기준월에서 끝나는 6개월이다', () => {
    expect(trendRangeOf('2026-03')).toEqual({ from: '2025-10', to: '2026-03' })
  })
})
