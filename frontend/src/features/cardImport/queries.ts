import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cardStatementApi } from '@/api'
import { expenseAffectedKeys, queryKeys } from '@/api/queryKeys'

/**
 * 카드 이용내역 한 파일을 가져온다.
 *
 * 가져오면 거래·할부가 생기고 모든 통계가 바뀐다. 미분류 카테고리가 처음 만들어질 수도 있으므로
 * 카테고리도 함께 무효화한다.
 */
export function useImportCardStatement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => cardStatementApi.upload(file),
    onSuccess: () =>
      Promise.all(
        [...expenseAffectedKeys, queryKeys.categories.all].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      ),
  })
}
