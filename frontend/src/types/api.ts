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

/** 카드 이용내역을 낸 카드사 (PRD 3.5). 경로와 응답의 `issuer` 에 쓰인다. */
export type CardIssuer = 'samsung' | 'woori' | 'hyundai'

/**
 * 카드 이용내역 가져오기 결과 (PRD 3.5).
 *
 * 파일의 줄은 추가 / 중복(무시) / 처리 안 됨 중 하나로 끝난다. 추가된 것 중 일부는 미분류다.
 * 취소는 승인 줄과 취소 줄이 한 건으로 묶이므로 줄 수와 건수의 합이 꼭 같지는 않다.
 */
export interface CardImportResult {
  fileName?: string
  issuer: CardIssuer
  /** 가져온 거래의 결제 수단. 예: `삼성카드` */
  paymentMethod: string
  /** 파일의 데이터 줄 수. */
  totalRowCount: number
  importedCount: number
  importedTransactionCount: number
  importedInstallmentPlanCount: number
  /** 이미 있어서 무시한 구매 수. */
  duplicateCount: number
  uncategorizedCategoryId: number
  uncategorizedCount: number
  uncategorized: CardImportItem[]
  unprocessedCount: number
  unprocessed: CardImportUnprocessed[]
}

export interface CardImportItem {
  spentDate: IsoDate
  merchant: string
  amount: number
  /** 할부면 개월 수. 일시불이면 없다. */
  installmentMonths?: number
}

/**
 * 가져오지 않은 것과 그 사유.
 *
 * 읽지 못한 줄은 `rowNumber`·`content` 를, 읽었지만 들이지 않은 구매(취소 등)는
 * `spentDate`·`merchant`·`amount` 를 갖는다.
 */
export interface CardImportUnprocessed {
  reason: string
  rowNumber?: number
  content?: string
  spentDate?: IsoDate
  merchant?: string
  amount?: number
}
