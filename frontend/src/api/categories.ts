import { apiClient } from './client'
import type { RegisterCategoryRequest } from '@/types/api'
import type { Category } from '@/types/domain'

const BASE_PATH = '/api/categories'

export const categoryApi = {
  async list(): Promise<Category[]> {
    const { data } = await apiClient.get<Category[]>(BASE_PATH)
    return data
  },

  async register(request: RegisterCategoryRequest): Promise<Category> {
    const { data } = await apiClient.post<Category>(BASE_PATH, request)
    return data
  },
}
