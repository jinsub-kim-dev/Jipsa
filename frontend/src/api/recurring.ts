import { apiClient } from './client'
import type {
  FixedExpenseConversionResult,
  RegisterFixedExpenseRequest,
  RegisterInstallmentPlanRequest,
} from '@/types/api'
import type { FixedExpense, InstallmentPlan, YearMonthString } from '@/types/domain'

export const fixedExpenseApi = {
  async list(month: YearMonthString): Promise<FixedExpense[]> {
    const { data } = await apiClient.get<FixedExpense[]>('/api/fixed-expenses', {
      params: { month },
    })
    return data
  },

  async register(request: RegisterFixedExpenseRequest): Promise<FixedExpense> {
    const { data } = await apiClient.post<FixedExpense>('/api/fixed-expenses', request)
    return data
  },

  /** 일반 지출로 고정비를 만들고 같은 사용처의 다른 달 일반 지출도 함께 바꾼다. */
  async convertFromTransaction(transactionId: number): Promise<FixedExpenseConversionResult> {
    const { data } = await apiClient.post<FixedExpenseConversionResult>(
      '/api/fixed-expenses/conversions',
      { transactionId },
    )
    return data
  },

  async terminate(id: number, lastMonth: YearMonthString): Promise<FixedExpense> {
    const { data } = await apiClient.post<FixedExpense>(`/api/fixed-expenses/${id}/terminate`, {
      lastMonth,
    })
    return data
  },
}

export const installmentPlanApi = {
  async list(month: YearMonthString): Promise<InstallmentPlan[]> {
    const { data } = await apiClient.get<InstallmentPlan[]>('/api/installment-plans', {
      params: { month },
    })
    return data
  },

  async register(request: RegisterInstallmentPlanRequest): Promise<InstallmentPlan> {
    const { data } = await apiClient.post<InstallmentPlan>('/api/installment-plans', request)
    return data
  },

  async cancel(id: number): Promise<void> {
    await apiClient.delete(`/api/installment-plans/${id}`)
  },
}
