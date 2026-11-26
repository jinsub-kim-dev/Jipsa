import { useQuery } from '@tanstack/react-query'
import { statisticsApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { YearMonthString } from '@/types/domain'

export function useMonthlySummary(month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.statistics.monthlySummary(month),
    queryFn: () => statisticsApi.monthlySummary(month),
  })
}

export function useKindTrend(from: YearMonthString, to: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.statistics.kindTrend(from, to),
    queryFn: () => statisticsApi.kindTrend(from, to),
  })
}

export function useCategoryTrend(
  from: YearMonthString,
  to: YearMonthString,
  target: YearMonthString,
) {
  return useQuery({
    queryKey: queryKeys.statistics.categoryTrend(from, to, target),
    queryFn: () => statisticsApi.categoryTrend(from, to, target),
  })
}

export function useTagRanking(month: YearMonthString, limit = 10) {
  return useQuery({
    queryKey: queryKeys.statistics.tags(month, limit),
    queryFn: () => statisticsApi.tags(month, limit),
  })
}

export function useInsight(month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.statistics.insight(month),
    queryFn: () => statisticsApi.insight(month),
  })
}

export function useCommitmentForecast(month: YearMonthString, months = 6) {
  return useQuery({
    queryKey: queryKeys.statistics.commitmentForecast(month, months),
    queryFn: () => statisticsApi.commitmentForecast(month, months),
  })
}
