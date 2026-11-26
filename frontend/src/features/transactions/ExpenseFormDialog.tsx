import { useState, type FormEvent } from 'react'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { useCategories } from '@/features/categories/queries'
import { useTagRanking } from '@/features/dashboard/queries'
import { expenseKindColor, expenseKindLabel } from '@/lib/format'
import { today } from '@/lib/period'
import { cn } from '@/lib/utils'
import { usePeriodStore } from '@/store/periodStore'
import { useUiStore } from '@/store/uiStore'
import { EXPENSE_KINDS, type ExpenseKind } from '@/types/domain'
import {
  emptyExpenseForm,
  formatAmountInput,
  INSTALLMENT_MONTH_OPTIONS,
  toExpenseSubmission,
  validateExpenseForm,
  type ExpenseFormErrors,
  type ExpenseFormValues,
} from './expenseForm'
import { useRegisterExpense } from './queries'
import { TagInput } from './TagInput'

const KIND_HINT: Record<ExpenseKind, string> = {
  GENERAL: '',
  FIXED: '고른 날짜의 일(日)에 매월 자동으로 기록돼요. 고른 달부터 시작합니다.',
  INSTALLMENT: '총 결제금액을 입력하세요. 고른 날짜가 1회차이고, 회차 금액은 등록 후 알려드려요.',
}

/**
 * 지출 추가 모달. 모든 화면에서 연다 (PRD 5절).
 *
 * 등록 후 닫지 않고 결과 문장을 보여주며 금액·사용처·메모만 비운다. 연속 입력을 돕기 위함이다.
 */
export function ExpenseFormDialog() {
  const { expenseFormOpen, expenseFormDate, closeExpenseForm } = useUiStore()

  return (
    <Dialog open={expenseFormOpen} onOpenChange={(open) => !open && closeExpenseForm()}>
      <DialogContent>
        {/* 열 때마다 폼 상태를 새로 만든다 */}
        {expenseFormOpen && (
          <ExpenseForm initialDate={expenseFormDate} onClose={closeExpenseForm} />
        )}
      </DialogContent>
    </Dialog>
  )
}

function ExpenseForm({
  initialDate,
  onClose,
}: {
  initialDate: string | null
  onClose: () => void
}) {
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const { data: categories = [] } = useCategories()
  const { data: tagRanking = [] } = useTagRanking(yearMonth, 12)
  const register = useRegisterExpense()

  const defaultDate = initialDate ?? (today().startsWith(yearMonth) ? today() : `${yearMonth}-01`)
  const [values, setValues] = useState<ExpenseFormValues>(() => emptyExpenseForm(defaultDate))
  const [errors, setErrors] = useState<ExpenseFormErrors>({})
  const [result, setResult] = useState<string | null>(null)

  const set = <K extends keyof ExpenseFormValues>(key: K, value: ExpenseFormValues[K]) =>
    setValues((previous) => ({ ...previous, [key]: value }))

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const found = validateExpenseForm(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    const message = await register.mutateAsync(toExpenseSubmission(values)).catch(() => null)
    if (message) {
      setResult(message)
      setValues((previous) => ({ ...previous, amount: '', merchant: '', memo: '' }))
    }
  }

  const serverError = register.error instanceof ApiError ? register.error : null

  return (
    <form onSubmit={onSubmit} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>지출 추가</DialogTitle>
        <DialogDescription>
          종류를 고르면 일반 지출 · 고정비 · 할부로 나뉘어 등록돼요.
        </DialogDescription>
      </DialogHeader>

      <div className="grid gap-1.5">
        <Label htmlFor="expense-amount">
          {values.kind === 'INSTALLMENT' ? '총 결제금액' : '금액'}
        </Label>
        <Input
          id="expense-amount"
          inputMode="numeric"
          placeholder="0원"
          className="h-11 text-xl font-bold"
          value={values.amount}
          aria-invalid={errors.amount !== undefined}
          onChange={(event) => set('amount', formatAmountInput(event.target.value))}
        />
        {errors.amount && <p className="text-destructive text-xs">{errors.amount}</p>}
      </div>

      <div className="grid gap-1.5">
        <span className="text-muted-foreground text-xs font-medium">종류</span>
        <div
          className="grid grid-cols-3 overflow-hidden rounded-lg border"
          role="radiogroup"
          aria-label="지출 종류"
        >
          {EXPENSE_KINDS.map((kind) => (
            <button
              key={kind}
              type="button"
              role="radio"
              aria-checked={values.kind === kind}
              onClick={() => set('kind', kind)}
              className={cn(
                'border-r py-2 text-sm last:border-r-0',
                values.kind === kind && 'font-semibold text-white',
              )}
              style={values.kind === kind ? { backgroundColor: expenseKindColor(kind) } : undefined}
            >
              {expenseKindLabel(kind)}
            </button>
          ))}
        </div>
        {values.kind === 'INSTALLMENT' && (
          <Select
            aria-label="할부 개월 수"
            value={values.installmentMonths}
            onChange={(event) => set('installmentMonths', Number(event.target.value))}
          >
            {INSTALLMENT_MONTH_OPTIONS.map((months) => (
              <option key={months} value={months}>
                {months}개월
              </option>
            ))}
          </Select>
        )}
        {KIND_HINT[values.kind] && (
          <p className="text-xs text-violet-600">{KIND_HINT[values.kind]}</p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="expense-date">
            {values.kind === 'GENERAL'
              ? '소비일'
              : values.kind === 'FIXED'
                ? '첫 납부일'
                : '1회차 날짜'}
          </Label>
          <Input
            id="expense-date"
            type="date"
            value={values.spentDate}
            onChange={(event) => set('spentDate', event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="expense-category">카테고리</Label>
          <Select
            id="expense-category"
            value={values.categoryId ?? ''}
            aria-invalid={errors.categoryId !== undefined}
            onChange={(event) =>
              set('categoryId', event.target.value === '' ? null : Number(event.target.value))
            }
          >
            <option value="">선택</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.icon} {category.name}
              </option>
            ))}
          </Select>
          {errors.categoryId && <p className="text-destructive text-xs">{errors.categoryId}</p>}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="expense-merchant">사용처</Label>
          <Input
            id="expense-merchant"
            placeholder="예: 스타벅스"
            value={values.merchant}
            aria-invalid={errors.merchant !== undefined}
            onChange={(event) => set('merchant', event.target.value)}
          />
          {errors.merchant && <p className="text-destructive text-xs">{errors.merchant}</p>}
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="expense-payment">결제 수단 (선택)</Label>
          <Input
            id="expense-payment"
            placeholder="예: 신한카드"
            value={values.paymentMethod}
            onChange={(event) => set('paymentMethod', event.target.value)}
          />
        </div>
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="expense-tags">태그</Label>
        <TagInput
          id="expense-tags"
          value={values.tags}
          onChange={(tags) => set('tags', tags)}
          suggestions={tagRanking.map((item) => item.tag)}
        />
      </div>

      {values.kind === 'GENERAL' && (
        <div className="grid gap-1.5">
          <Label htmlFor="expense-memo">메모 (선택)</Label>
          <Input
            id="expense-memo"
            value={values.memo}
            onChange={(event) => set('memo', event.target.value)}
          />
        </div>
      )}

      {serverError && (
        <p className="text-destructive text-sm" role="alert">
          {serverError.message}
        </p>
      )}
      {result && !register.isPending && (
        <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">
          {result}
        </p>
      )}

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>
          닫기
        </Button>
        <Button type="submit" disabled={register.isPending}>
          {register.isPending ? '저장 중…' : '저장'}
        </Button>
      </DialogFooter>
    </form>
  )
}
