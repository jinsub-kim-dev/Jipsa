import { create } from 'zustand'
import { currentYearMonth, shiftYearMonth } from '@/lib/period'
import type { YearMonthString } from '@/types/domain'

/**
 * 조회 기준 연월. 모든 메뉴가 공유한다 (PRD 5절).
 *
 * 대시보드에서 10월을 보다가 캘린더로 가면 10월이 보여야 한다. 그래서 화면 상태가 아니라
 * 전역 스토어에 둔다. 서버 데이터는 TanStack Query 가 담당한다.
 */
export interface PeriodState {
  yearMonth: YearMonthString
  setYearMonth: (yearMonth: YearMonthString) => void
  goToPreviousMonth: () => void
  goToNextMonth: () => void
  goToCurrentMonth: () => void
}

export const usePeriodStore = create<PeriodState>((set) => ({
  yearMonth: currentYearMonth(),
  setYearMonth: (yearMonth) => set({ yearMonth }),
  goToPreviousMonth: () => set((state) => ({ yearMonth: shiftYearMonth(state.yearMonth, -1) })),
  goToNextMonth: () => set((state) => ({ yearMonth: shiftYearMonth(state.yearMonth, 1) })),
  goToCurrentMonth: () => set({ yearMonth: currentYearMonth() }),
}))

/** 대시보드 추이·히트맵에 쓰는 구간 길이(개월). */
export const TREND_MONTHS = 6

/** 기준월에서 끝나는 추이 구간. 스토어 상태에서 파생되므로 상태로 저장하지 않는다. */
export function trendRangeOf(yearMonth: YearMonthString): {
  from: YearMonthString
  to: YearMonthString
} {
  return { from: shiftYearMonth(yearMonth, -(TREND_MONTHS - 1)), to: yearMonth }
}
