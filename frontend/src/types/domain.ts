/**
 * 백엔드 응답을 그대로 반영한 도메인 타입.
 *
 * 이름은 JIPSA_PRD.md 의 용어를 그대로 쓴다. (`spentDate` 를 `date` 로 바꿔 부르지 않는다)
 * 서버가 `null` 필드를 응답에서 빼므로(non_null) 선택 필드는 `?` 로 표현한다.
 * 증감률·비율이 없으면 정의할 수 없다는 뜻이다(이전 값이 0원). 0 으로 바꿔 쓰지 않는다.
 */

/** `yyyy-MM-dd` */
export type IsoDate = string
/** `yyyy-MM` */
export type YearMonthString = string

/** 지출 종류. 일반 지출 / 고정비 / 할부. */
export type ExpenseKind = 'GENERAL' | 'FIXED' | 'INSTALLMENT'

export const EXPENSE_KINDS: readonly ExpenseKind[] = ['GENERAL', 'FIXED', 'INSTALLMENT']

export interface Category {
  id: number
  name: string
  icon: string
  /** `#RRGGBB` */
  color: string
}

export interface Transaction {
  id: number
  kind: ExpenseKind
  categoryId: number
  amount: number
  merchant: string
  spentDate: IsoDate
  tags: string[]
  memo?: string
  paymentMethod?: string
  fixedExpenseId?: number
  installmentPlanId?: number
  installmentSequence?: number
  /** 금액과 소비일을 바꿀 수 없다 (할부 회차). 서버가 판단한다. */
  locked: boolean
  /** 하나만 따로 삭제할 수 있다 (일반 지출). 서버가 판단한다. */
  deletable: boolean
}

export interface FixedExpense {
  id: number
  categoryId: number
  merchant: string
  amount: number
  dayOfMonth: number
  tags: string[]
  paymentMethod?: string
  startMonth: YearMonthString
  endMonth?: YearMonthString
  /** 조회 기준월 */
  month: YearMonthString
  activeInMonth: boolean
  /** 기준월의 실제 납부일. 말일 보정이 반영되어 있다. */
  dueDateInMonth: IsoDate
}

export interface InstallmentProgress {
  currentSequence: number
  installmentMonths: number
  amountThisMonth: number
  paidAmount: number
  remainingAmount: number
  remainingSequences: number
  lastSequence: boolean
}

export interface InstallmentPlan {
  id: number
  categoryId: number
  merchant: string
  totalAmount: number
  installmentMonths: number
  monthlyAmount: number
  firstInstallmentAmount: number
  firstBillingMonth: YearMonthString
  lastBillingMonth: YearMonthString
  billingDay: number
  tags: string[]
  paymentMethod?: string
  month: YearMonthString
  /** 기준월이 할부 기간이 아니면 없다. */
  progress?: InstallmentProgress
}

export interface KindSummary {
  kind: ExpenseKind
  amount: number
  previousAmount: number
  ratio?: number
  changeRate?: number
}

export interface MonthlySummary {
  month: YearMonthString
  total: number
  previousTotal: number
  totalChangeRate?: number
  /** 일반 지출 · 고정비 · 할부 순서 */
  kinds: KindSummary[]
  commitmentRatio?: number
}

export interface KindTrendPoint {
  month: YearMonthString
  general: number
  fixed: number
  installment: number
  total: number
}

export interface CategoryTrendRow {
  categoryId: number
  /** `CategoryTrend.months` 와 같은 순서 */
  monthlyAmounts: number[]
  targetAmount: number
  changeRate?: number
}

export interface CategoryTrend {
  months: YearMonthString[]
  targetMonth: YearMonthString
  /** 기준월 지출 내림차순. 서버가 정렬을 확정한다. */
  categories: CategoryTrendRow[]
}

export interface TagTotal {
  tag: string
  amount: number
  transactionCount: number
}

export interface SpendingInsight {
  month: YearMonthString
  baselineMonths: number
  total: number
  baselineAverage?: number
  changeRateFromBaseline?: number
  topIncreasedCategory?: { categoryId: number; increase: number }
  commitmentRatio?: number
}

export interface MonthlyCommitment {
  month: YearMonthString
  fixed: number
  installment: number
  total: number
  installmentMerchants: string[]
}
