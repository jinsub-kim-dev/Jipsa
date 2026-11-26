import { beforeEach, describe, expect, it } from 'vitest'
import { useUiStore } from './uiStore'

describe('uiStore - 지출 추가 모달', () => {
  beforeEach(() => {
    useUiStore.setState({ expenseFormOpen: false, expenseFormDate: null })
  })

  it('날짜 없이 열 수 있다', () => {
    useUiStore.getState().openExpenseForm()

    expect(useUiStore.getState()).toMatchObject({ expenseFormOpen: true, expenseFormDate: null })
  })

  it('캘린더에서 고른 날짜를 채워 열고, 닫으면 날짜를 비운다', () => {
    useUiStore.getState().openExpenseForm('2026-11-15')
    expect(useUiStore.getState().expenseFormDate).toBe('2026-11-15')

    useUiStore.getState().closeExpenseForm()
    expect(useUiStore.getState()).toMatchObject({ expenseFormOpen: false, expenseFormDate: null })
  })
})
