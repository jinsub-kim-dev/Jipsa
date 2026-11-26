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
import { expenseKindLabel } from '@/lib/format'
import type { Transaction } from '@/types/domain'
import {
  formatAmountInput,
  toEditValues,
  toUpdateRequest,
  type TransactionEditValues,
} from './expenseForm'
import { useRemoveTransaction, useUpdateTransaction } from './queries'
import { TagInput } from './TagInput'

/**
 * 거래 수정·삭제. 바꿀 수 없는 값은 입력을 잠그고 이유를 적는다 (frontend/CLAUDE.md 3.5).
 * 잠금 여부(`locked`)와 삭제 가능 여부(`deletable`)는 서버가 판단해 내려준다.
 */
export function TransactionEditDialog({
  transaction,
  onClose,
}: {
  transaction: Transaction | null
  onClose: () => void
}) {
  return (
    <Dialog open={transaction !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {transaction && (
          <EditForm key={transaction.id} transaction={transaction} onClose={onClose} />
        )}
      </DialogContent>
    </Dialog>
  )
}

function EditForm({ transaction, onClose }: { transaction: Transaction; onClose: () => void }) {
  const { data: categories = [] } = useCategories()
  const update = useUpdateTransaction()
  const remove = useRemoveTransaction()
  const [values, setValues] = useState<TransactionEditValues>(() => toEditValues(transaction))
  const set = <K extends keyof TransactionEditValues>(key: K, value: TransactionEditValues[K]) =>
    setValues((previous) => ({ ...previous, [key]: value }))

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const request = toUpdateRequest(transaction, values)
    if (Object.keys(request).length === 0) return onClose()
    await update.mutateAsync({ id: transaction.id, request }).then(onClose, () => undefined)
  }

  const onDelete = async () => {
    if (!window.confirm(`${transaction.merchant} 거래를 삭제할까요? 되돌릴 수 없습니다.`)) return
    await remove.mutateAsync(transaction.id).then(onClose, () => undefined)
  }

  const error = [update.error, remove.error].find((value) => value instanceof ApiError) as
    ApiError | undefined
  const lockReason =
    transaction.kind === 'INSTALLMENT'
      ? '할부 회차의 금액과 날짜는 바꿀 수 없어요. 바꾸려면 고정비·할부 메뉴에서 해지 후 다시 등록하세요.'
      : null

  return (
    <form onSubmit={onSubmit} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>거래 수정</DialogTitle>
        <DialogDescription>{expenseKindLabel(transaction.kind)}</DialogDescription>
      </DialogHeader>
      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="edit-amount">금액</Label>
          <Input
            id="edit-amount"
            inputMode="numeric"
            value={values.amount}
            disabled={transaction.locked}
            onChange={(event) => set('amount', formatAmountInput(event.target.value))}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="edit-date">소비일</Label>
          <Input
            id="edit-date"
            type="date"
            value={values.spentDate}
            disabled={transaction.locked}
            onChange={(event) => set('spentDate', event.target.value)}
          />
        </div>
      </div>
      {lockReason && <p className="text-xs text-violet-600">{lockReason}</p>}
      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="edit-merchant">사용처</Label>
          <Input
            id="edit-merchant"
            value={values.merchant}
            onChange={(event) => set('merchant', event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="edit-category">카테고리</Label>
          <Select
            id="edit-category"
            value={values.categoryId}
            onChange={(event) => set('categoryId', Number(event.target.value))}
          >
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.icon} {category.name}
              </option>
            ))}
          </Select>
        </div>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="edit-tags">태그</Label>
        <TagInput id="edit-tags" value={values.tags} onChange={(tags) => set('tags', tags)} />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="edit-memo">메모</Label>
          <Input
            id="edit-memo"
            value={values.memo}
            onChange={(event) => set('memo', event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="edit-payment">결제 수단</Label>
          <Input
            id="edit-payment"
            value={values.paymentMethod}
            onChange={(event) => set('paymentMethod', event.target.value)}
          />
        </div>
      </div>
      {error && (
        <p className="text-destructive text-sm" role="alert">
          {error.message}
        </p>
      )}
      <DialogFooter className="sm:justify-between">
        {transaction.deletable ? (
          <Button
            type="button"
            variant="destructive"
            onClick={onDelete}
            disabled={remove.isPending}
          >
            삭제
          </Button>
        ) : (
          <span className="text-muted-foreground self-center text-xs">
            {transaction.kind === 'FIXED'
              ? '고정비 거래는 고정비를 종료해 정리해요.'
              : '할부는 계획 단위로 해지해요.'}
          </span>
        )}
        <div className="flex gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={update.isPending}>
            저장
          </Button>
        </div>
      </DialogFooter>
    </form>
  )
}
