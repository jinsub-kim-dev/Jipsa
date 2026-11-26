import { apiClient } from './client'
import type {
  CategoryTrend,
  KindTrendPoint,
  MonthlyCommitment,
  MonthlySummary,
  SpendingInsight,
  TagTotal,
  YearMonthString,
} from '@/types/domain'

const BASE_PATH = '/api/statistics'

export const statisticsApi = {
  async monthlySummary(month: YearMonthString): Promise<MonthlySummary> {
    const { data } = await apiClient.get<MonthlySummary>(`${BASE_PATH}/monthly-summary`, {
      params: { month },
    })
    return data
  },

  async kindTrend(from: YearMonthString, to: YearMonthString): Promise<KindTrendPoint[]> {
    const { data } = await apiClient.get<KindTrendPoint[]>(`${BASE_PATH}/kind-trend`, {
      params: { from, to },
    })
    return data
  },

  async categoryTrend(
    from: YearMonthString,
    to: YearMonthString,
    targetMonth: YearMonthString,
  ): Promise<CategoryTrend> {
    const { data } = await apiClient.get<CategoryTrend>(`${BASE_PATH}/category-trend`, {
      params: { from, to, targetMonth },
    })
    return data
  },

  async tags(month: YearMonthString, limit = 10): Promise<TagTotal[]> {
    const { data } = await apiClient.get<TagTotal[]>(`${BASE_PATH}/tags`, {
      params: { month, limit },
    })
    return data
  },

  async insight(month: YearMonthString): Promise<SpendingInsight> {
    const { data } = await apiClient.get<SpendingInsight>(`${BASE_PATH}/insight`, {
      params: { month },
    })
    return data
  },

  async commitmentForecast(month: YearMonthString, months = 6): Promise<MonthlyCommitment[]> {
    const { data } = await apiClient.get<MonthlyCommitment[]>(`${BASE_PATH}/commitment-forecast`, {
      params: { month, months },
    })
    return data
  },
}
