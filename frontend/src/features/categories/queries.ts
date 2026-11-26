import { useQuery } from '@tanstack/react-query'
import { categoryApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { Category } from '@/types/domain'

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories.all,
    queryFn: () => categoryApi.list(),
    staleTime: 5 * 60_000,
  })
}

/** 거래·통계 응답의 `categoryId` 를 이름·아이콘·색으로 바꿔 그릴 때 쓴다. */
export function useCategoryMap(): Map<number, Category> {
  const { data } = useCategories()
  return new Map((data ?? []).map((category) => [category.id, category]))
}
