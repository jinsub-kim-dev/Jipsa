import { apiClient } from './client'
import type {
  RegisterTransactionRequest,
  TransactionSearchParams,
  UpdateTransactionRequest,
} from '@/types/api'
import type { Transaction } from '@/types/domain'

const BASE_PATH = '/api/transactions'

export const transactionApi = {
  async search(params: TransactionSearchParams): Promise<Transaction[]> {
    const { data } = await apiClient.get<Transaction[]>(BASE_PATH, { params })
    return data
  },

  async register(request: RegisterTransactionRequest): Promise<Transaction> {
    const { data } = await apiClient.post<Transaction>(BASE_PATH, request)
    return data
  },

  async update(id: number, request: UpdateTransactionRequest): Promise<Transaction> {
    const { data } = await apiClient.patch<Transaction>(`${BASE_PATH}/${id}`, request)
    return data
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`${BASE_PATH}/${id}`)
  },
}
