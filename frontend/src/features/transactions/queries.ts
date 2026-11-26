import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fixedExpenseApi, installmentPlanApi, transactionApi } from '@/api'
import { expenseAffectedKeys, queryKeys } from '@/api/queryKeys'
import type { TransactionSearchParams, UpdateTransactionRequest } from '@/types/api'
import type { ExpenseSubmission } from './expenseForm'

export function useTransactions(params: TransactionSearchParams) {
  return useQuery({
    queryKey: queryKeys.transactions.search(params),
    queryFn: () => transactionApi.search(params),
  })
}

function useInvalidateExpenses() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(expenseAffectedKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
}

/** 지출 추가 모달의 제출. 종류에 따라 다른 API 를 부르고, 무엇이 만들어졌는지 문장으로 돌려준다. */
export function useRegisterExpense() {
  const invalidate = useInvalidateExpenses()
  return useMutation({
    mutationFn: async (submission: ExpenseSubmission): Promise<string> => {
      switch (submission.kind) {
        case 'GENERAL': {
          const saved = await transactionApi.register(submission.request)
          return `${saved.merchant} ${saved.amount.toLocaleString('ko-KR')}원을 기록했어요.`
        }
        case 'FIXED': {
          const saved = await fixedExpenseApi.register(submission.request)
          return `고정비 ${saved.merchant}을(를) 등록했어요. 매월 ${saved.dayOfMonth}일에 자동으로 기록돼요.`
        }
        case 'INSTALLMENT': {
          // 회차 금액은 서버가 계산한 값을 그대로 보여준다 (나머지는 1회차에 더해진다)
          const saved = await installmentPlanApi.register(submission.request)
          const first = saved.firstInstallmentAmount.toLocaleString('ko-KR')
          const monthly = saved.monthlyAmount.toLocaleString('ko-KR')
          return saved.firstInstallmentAmount === saved.monthlyAmount
            ? `${saved.merchant} ${saved.installmentMonths}개월 할부를 등록했어요. 월 ${monthly}원`
            : `${saved.merchant} ${saved.installmentMonths}개월 할부를 등록했어요. 1회차 ${first}원, 이후 월 ${monthly}원`
        }
      }
    },
    onSuccess: invalidate,
  })
}

/** 일반 지출을 고정비로 전환한다. 같은 사용처의 다른 달 거래도 바뀌므로 지출 영역 전체를 새로 읽는다. */
export function useConvertToFixedExpense() {
  const invalidate = useInvalidateExpenses()
  return useMutation({
    mutationFn: (transactionId: number) => fixedExpenseApi.convertFromTransaction(transactionId),
    onSuccess: invalidate,
  })
}

export function useUpdateTransaction() {
  const invalidate = useInvalidateExpenses()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateTransactionRequest }) =>
      transactionApi.update(id, request),
    onSuccess: invalidate,
  })
}

export function useRemoveTransaction() {
  const invalidate = useInvalidateExpenses()
  return useMutation({
    mutationFn: (id: number) => transactionApi.remove(id),
    onSuccess: invalidate,
  })
}
