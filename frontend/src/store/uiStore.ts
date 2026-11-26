import { create } from 'zustand'
import type { IsoDate } from '@/types/domain'

/**
 * 순수 UI 상태. 서버 데이터와 무관한 화면 상태만 담는다.
 *
 * 지출 추가 모달은 모든 화면에서 열리므로(PRD 5절) 레이아웃에 한 번만 두고 여기서 연다.
 * 캘린더에서 날짜를 골라 열면 그 날짜가 채워진다.
 */
export interface UiState {
  expenseFormOpen: boolean
  /** 모달을 열 때 채울 소비일. 없으면 폼이 정한다. */
  expenseFormDate: IsoDate | null
  openExpenseForm: (date?: IsoDate) => void
  closeExpenseForm: () => void
}

export const useUiStore = create<UiState>((set) => ({
  expenseFormOpen: false,
  expenseFormDate: null,
  openExpenseForm: (date) => set({ expenseFormOpen: true, expenseFormDate: date ?? null }),
  closeExpenseForm: () => set({ expenseFormOpen: false, expenseFormDate: null }),
}))
