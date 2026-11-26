import type { ExpenseKind, IsoDate, YearMonthString } from './domain'

/** 백엔드 오류 응답 (PRD 6절). */
export interface ApiErrorResponse {
  code: string
  message: string
  fieldErrors?: { field: string; message: string }[]
}

export interface RegisterCategoryRequest {
  name: string
  icon: string
  color: string
}

/** 일반 지출 등록. 고정비·할부는 각자의 요청으로 등록한다. */
export interface RegisterTransactionRequest {
  categoryId: number
  amount: number
  merchant: string
  spentDate: IsoDate
  tags: string[]
  memo?: string
  paymentMethod?: string
}

/** 거래 수정. **바뀐 필드만 담는다.** 메모 삭제는 `clearMemo`. */
export interface UpdateTransactionRequest {
  categoryId?: number
  amount?: number
  merchant?: string
  spentDate?: IsoDate
  tags?: string[]
  memo?: string
  clearMemo?: boolean
  paymentMethod?: string
}

export interface RegisterFixedExpenseRequest {
  categoryId: number
  merchant: string
  amount: number
  dayOfMonth: number
  startMonth: YearMonthString
  tags: string[]
  paymentMethod?: string
}

export interface RegisterInstallmentPlanRequest {
  categoryId: number
  merchant: string
  totalAmount: number
  installmentMonths: number
  firstBillingMonth: YearMonthString
  billingDay: number
  tags: string[]
  paymentMethod?: string
}

/** 거래 목록 조회 조건. 월은 필수다. */
export interface TransactionSearchParams {
  month: YearMonthString
  kind?: ExpenseKind
  categoryId?: number
  tag?: string
  keyword?: string
}
