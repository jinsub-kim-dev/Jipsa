import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fixedExpenseApi, installmentPlanApi } from '@/api'
import { expenseAffectedKeys, queryKeys } from '@/api/queryKeys'
import type { YearMonthString } from '@/types/domain'

export function useFixedExpenses(month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.fixedExpenses.list(month),
    queryFn: () => fixedExpenseApi.list(month),
  })
}

export function useInstallmentPlans(month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.installmentPlans.list(month),
    queryFn: () => installmentPlanApi.list(month),
  })
}

function useInvalidate() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(expenseAffectedKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
}

export function useTerminateFixedExpense() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ id, lastMonth }: { id: number; lastMonth: YearMonthString }) =>
      fixedExpenseApi.terminate(id, lastMonth),
    onSuccess: invalidate,
  })
}

export function useCancelInstallmentPlan() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (id: number) => installmentPlanApi.cancel(id),
    onSuccess: invalidate,
  })
}
