import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { QueryState } from '@/components/common/QueryState'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { useCategories } from '@/features/categories/queries'
import { useInstallmentPlans } from '@/features/recurring/queries'
import { useTransactions } from '@/features/transactions/queries'
import { TransactionEditDialog } from '@/features/transactions/TransactionEditDialog'
import {
  hasActiveFilter,
  parseTransactionFilter,
  serializeTransactionFilter,
  type TransactionFilter,
} from '@/features/transactions/transactionFilter'
import { TransactionTable } from '@/features/transactions/TransactionTable'
import {
  expenseKindColor,
  expenseKindLabel,
  expenseKindShortLabel,
  formatKrw,
  formatTag,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import { usePeriodStore } from '@/store/periodStore'
import { EXPENSE_KINDS, type Transaction } from '@/types/domain'

const PAGE_SIZE = 30

/**
 * 거래 내역. 필터는 주소에 담는다(대시보드에서 드릴다운으로 들어온다).
 * 검색어는 **제출할 때만** 주소에 반영한다. 타이핑마다 바꾸면 매 글자에서 조회가 나간다.
 */
export function TransactionsPage() {
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseTransactionFilter(searchParams)
  const [keywordDraft, setKeywordDraft] = useState(filter.keyword ?? '')
  const [showAll, setShowAll] = useState(false)
  const [editing, setEditing] = useState<Transaction | null>(null)

  const { data: categories = [] } = useCategories()
  const plans = useInstallmentPlans(yearMonth)
  const transactions = useTransactions({ month: yearMonth, ...filter })
  const rows = transactions.data ?? []
  const installmentMonths = new Map(
    (plans.data ?? []).map((plan) => [plan.id, plan.installmentMonths]),
  )

  const apply = (next: TransactionFilter) => {
    setShowAll(false)
    setSearchParams(serializeTransactionFilter(next))
  }
  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    apply({ ...filter, keyword: keywordDraft.trim() || undefined })
  }

  const sumOf = (list: Transaction[]) => list.reduce((sum, row) => sum + row.amount, 0)

  return (
    <Card className="p-5">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <form onSubmit={onSearch} className="flex gap-1.5">
          <Input
            aria-label="사용처·태그 검색어"
            placeholder="🔍 사용처·메모·태그"
            className="w-56"
            value={keywordDraft}
            onChange={(event) => setKeywordDraft(event.target.value)}
          />
          <button type="submit" className="rounded-md border px-3 text-sm hover:bg-slate-50">
            검색
          </button>
        </form>
        <Select
          aria-label="카테고리"
          className="w-40"
          value={filter.categoryId ?? ''}
          onChange={(event) =>
            apply({
              ...filter,
              categoryId: event.target.value ? Number(event.target.value) : undefined,
            })
          }
        >
          <option value="">전체 카테고리</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.icon} {category.name}
            </option>
          ))}
        </Select>
        <div className="flex gap-1">
          <KindChip active={!filter.kind} onClick={() => apply({ ...filter, kind: undefined })}>
            전체
          </KindChip>
          {EXPENSE_KINDS.map((kind) => (
            <KindChip
              key={kind}
              active={filter.kind === kind}
              onClick={() => apply({ ...filter, kind })}
            >
              {expenseKindLabel(kind)}
            </KindChip>
          ))}
        </div>
        {filter.tag && (
          <button
            type="button"
            onClick={() => apply({ ...filter, tag: undefined })}
            className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-xs text-indigo-700"
          >
            {formatTag(filter.tag)} ✕
          </button>
        )}
        {hasActiveFilter(filter) && (
          <button
            type="button"
            className="text-muted-foreground text-xs underline"
            onClick={() => {
              setKeywordDraft('')
              apply({})
            }}
          >
            필터 지우기
          </button>
        )}
      </div>

      <div className="text-muted-foreground mb-2 flex flex-wrap gap-4 text-[13px]">
        <span>
          <b className="text-foreground">{rows.length}건</b> · 합계{' '}
          <b className="text-foreground">{formatKrw(sumOf(rows))}</b>
        </span>
        {EXPENSE_KINDS.map((kind) => (
          <span key={kind}>
            <i
              className="mr-1 inline-block size-2 rounded-full"
              style={{ backgroundColor: expenseKindColor(kind) }}
            />
            {expenseKindShortLabel(kind)}{' '}
            {formatKrw(sumOf(rows.filter((row) => row.kind === kind)))}
          </span>
        ))}
      </div>

      <QueryState
        isPending={transactions.isPending}
        error={transactions.error}
        isEmpty={rows.length === 0}
        emptyMessage="조건에 맞는 내역이 없어요."
      >
        <TransactionTable
          transactions={showAll ? rows : rows.slice(0, PAGE_SIZE)}
          installmentMonthsByPlan={installmentMonths}
          onSelect={setEditing}
          onTagClick={(tag) => apply({ ...filter, tag })}
          showPaymentMethod
        />
        {rows.length > PAGE_SIZE && (
          <div className="pt-3 text-center">
            <button
              type="button"
              className="rounded-full border px-4 py-1.5 text-sm"
              onClick={() => setShowAll((value) => !value)}
            >
              {showAll ? '접기' : `${rows.length - PAGE_SIZE}건 더 보기`}
            </button>
          </div>
        )}
      </QueryState>

      <TransactionEditDialog transaction={editing} onClose={() => setEditing(null)} />
    </Card>
  )
}

function KindChip(props: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      aria-pressed={props.active}
      className={cn(
        'rounded-full border px-3 py-1.5 text-[13px] whitespace-nowrap',
        props.active && 'border-slate-900 bg-slate-900 text-white',
      )}
    >
      {props.children}
    </button>
  )
}
