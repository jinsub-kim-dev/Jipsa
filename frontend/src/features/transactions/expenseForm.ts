import type {
  RegisterFixedExpenseRequest,
  RegisterInstallmentPlanRequest,
  RegisterTransactionRequest,
  UpdateTransactionRequest,
} from '@/types/api'
import type { ExpenseKind, IsoDate, Transaction } from '@/types/domain'

/**
 * 지출 추가 폼 → 요청 변환.
 *
 * 한 폼에서 종류를 고르지만 서버의 요청은 셋으로 나뉜다 (PRD 3절).
 * - 일반 지출 → 거래 등록
 * - 고정비 → 고정비 정의 등록. 고른 날짜의 **일**이 납부일, **월**이 시작월이다
 * - 할부 → 할부 계획 등록. 입력 금액은 **총액**이고, 고른 날짜가 1회차다
 *
 * 할부의 회차별 금액은 여기서 계산하지 않는다. 등록 응답에 담겨 온다 (frontend/CLAUDE.md 3.5).
 */
export interface ExpenseFormValues {
  kind: ExpenseKind
  /** 입력 그대로의 문자열. 쉼표가 섞여 있을 수 있다. */
  amount: string
  spentDate: IsoDate
  categoryId: number | null
  merchant: string
  tags: string[]
  paymentMethod: string
  memo: string
  installmentMonths: number
}

export type ExpenseSubmission =
  | { kind: 'GENERAL'; request: RegisterTransactionRequest }
  | { kind: 'FIXED'; request: RegisterFixedExpenseRequest }
  | { kind: 'INSTALLMENT'; request: RegisterInstallmentPlanRequest }

export type ExpenseFormErrors = Partial<
  Record<'amount' | 'categoryId' | 'merchant' | 'spentDate', string>
>

export const INSTALLMENT_MONTH_OPTIONS = [2, 3, 6, 10, 12, 24] as const

export function emptyExpenseForm(spentDate: IsoDate): ExpenseFormValues {
  return {
    kind: 'GENERAL',
    amount: '',
    spentDate,
    categoryId: null,
    merchant: '',
    tags: [],
    paymentMethod: '',
    memo: '',
    installmentMonths: 3,
  }
}

/** 금액 입력을 원 단위 정수로 읽는다. 숫자가 아니면 `null`. */
export function parseAmount(raw: string): number | null {
  const digits = raw.replaceAll(',', '').trim()
  if (!/^\d+$/.test(digits)) return null
  return Number(digits)
}

/** 입력하는 동안 천 단위 쉼표를 넣는다. 숫자 외의 문자는 지운다. */
export function formatAmountInput(raw: string): string {
  const digits = raw.replace(/\D/g, '')
  return digits === '' ? '' : Number(digits).toLocaleString('ko-KR')
}

/**
 * 필수 입력만 확인한다. 금액 범위·길이 같은 규칙은 서버 도메인이 검증하고 메시지를 준다.
 * 같은 규칙을 화면에 다시 두면 어긋난다.
 */
export function validateExpenseForm(values: ExpenseFormValues): ExpenseFormErrors {
  const errors: ExpenseFormErrors = {}
  if (parseAmount(values.amount) === null) errors.amount = '금액을 숫자로 입력하세요.'
  if (values.categoryId === null) errors.categoryId = '카테고리를 고르세요.'
  if (values.merchant.trim() === '') errors.merchant = '사용처를 입력하세요.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(values.spentDate)) errors.spentDate = '날짜를 고르세요.'
  return errors
}

const optional = (value: string): string | undefined =>
  value.trim() === '' ? undefined : value.trim()

/** 검증을 통과한 폼을 종류에 맞는 요청으로 바꾼다. */
export function toExpenseSubmission(values: ExpenseFormValues): ExpenseSubmission {
  const errors = validateExpenseForm(values)
  if (Object.keys(errors).length > 0) {
    throw new Error(`폼이 유효하지 않습니다: ${Object.values(errors).join(' ')}`)
  }
  const amount = parseAmount(values.amount) as number
  const categoryId = values.categoryId as number
  const merchant = values.merchant.trim()
  const paymentMethod = optional(values.paymentMethod)
  const yearMonth = values.spentDate.slice(0, 7)
  const day = Number(values.spentDate.slice(8, 10))

  switch (values.kind) {
    case 'GENERAL':
      return {
        kind: 'GENERAL',
        request: {
          categoryId,
          amount,
          merchant,
          spentDate: values.spentDate,
          tags: values.tags,
          memo: optional(values.memo),
          paymentMethod,
        },
      }
    case 'FIXED':
      return {
        kind: 'FIXED',
        request: {
          categoryId,
          merchant,
          amount,
          dayOfMonth: day,
          startMonth: yearMonth,
          tags: values.tags,
          paymentMethod,
        },
      }
    case 'INSTALLMENT':
      return {
        kind: 'INSTALLMENT',
        request: {
          categoryId,
          merchant,
          totalAmount: amount,
          installmentMonths: values.installmentMonths,
          firstBillingMonth: yearMonth,
          billingDay: day,
          tags: values.tags,
          paymentMethod,
        },
      }
  }
}

/** 수정 폼의 값. */
export interface TransactionEditValues {
  amount: string
  spentDate: IsoDate
  categoryId: number
  merchant: string
  tags: string[]
  memo: string
  paymentMethod: string
}

export function toEditValues(transaction: Transaction): TransactionEditValues {
  return {
    amount: transaction.amount.toLocaleString('ko-KR'),
    spentDate: transaction.spentDate,
    categoryId: transaction.categoryId,
    merchant: transaction.merchant,
    tags: [...transaction.tags],
    memo: transaction.memo ?? '',
    paymentMethod: transaction.paymentMethod ?? '',
  }
}

/**
 * 수정 요청. **바뀐 필드만 담는다** (frontend/CLAUDE.md 3.5).
 *
 * 메모를 지운 경우는 빈 문자열이 아니라 `clearMemo` 로 보낸다. 잠긴 거래(할부 회차)의
 * 금액·소비일은 입력이 잠겨 있으므로 바뀔 일이 없지만, 혹시 바뀌어도 보내지 않는다.
 */
export function toUpdateRequest(
  original: Transaction,
  values: TransactionEditValues,
): UpdateTransactionRequest {
  const request: UpdateTransactionRequest = {}
  const amount = parseAmount(values.amount)
  if (!original.locked && amount !== null && amount !== original.amount) request.amount = amount
  if (!original.locked && values.spentDate !== original.spentDate)
    request.spentDate = values.spentDate
  if (values.categoryId !== original.categoryId) request.categoryId = values.categoryId
  if (values.merchant.trim() !== original.merchant) request.merchant = values.merchant.trim()
  if (values.tags.join('\u0000') !== original.tags.join('\u0000')) request.tags = values.tags

  const memo = values.memo.trim()
  if (memo !== (original.memo ?? '')) {
    if (memo === '') request.clearMemo = true
    else request.memo = memo
  }

  const paymentMethod = values.paymentMethod.trim()
  if (paymentMethod !== '' && paymentMethod !== (original.paymentMethod ?? ''))
    request.paymentMethod = paymentMethod
  return request
}
