import { useEffect, useState } from 'react'
import { QueryState } from '@/components/common/QueryState'
import { Section } from '@/components/common/Section'
import { Button } from '@/components/ui/button'
import { CalendarGrid } from '@/features/calendar/CalendarGrid'
import { matchesHighlight, type CalendarHighlight } from '@/features/calendar/highlight'
import { useCategoryMap } from '@/features/categories/queries'
import { useMonthlySummary, useTagRanking } from '@/features/dashboard/queries'
import { useInstallmentPlans } from '@/features/recurring/queries'
import { useTransactions } from '@/features/transactions/queries'
import { TransactionEditDialog } from '@/features/transactions/TransactionEditDialog'
import { defaultSelectedDate, groupByDate, weekdayLabel } from '@/lib/calendar'
import {
  expenseKindColor,
  expenseKindLabel,
  expenseKindShortLabel,
  formatKrw,
  formatKrwCompact,
  formatPercentage,
  formatTag,
} from '@/lib/format'
import { today as todayOf } from '@/lib/period'
import { cn } from '@/lib/utils'
import { usePeriodStore } from '@/store/periodStore'
import { useUiStore } from '@/store/uiStore'
import { EXPENSE_KINDS, type Transaction } from '@/types/domain'

/** 캘린더. 언제 쓰는지(날짜·요일)를 보는 화면이다. 월은 다른 메뉴와 공유한다. */
export function CalendarPage() {
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const openExpenseForm = useUiStore((state) => state.openExpenseForm)
  const today = todayOf()
  const [selectedDate, setSelectedDate] = useState(() => defaultSelectedDate(yearMonth, today))
  const [highlight, setHighlight] = useState<CalendarHighlight>(null)
  const [editing, setEditing] = useState<Transaction | null>(null)
  const categories = useCategoryMap()

  // 달이 바뀌면 그 달의 기본 날짜를 고른다
  useEffect(() => setSelectedDate(defaultSelectedDate(yearMonth, today)), [yearMonth, today])

  const transactions = useTransactions({ month: yearMonth })
  const summary = useMonthlySummary(yearMonth)
  const tags = useTagRanking(yearMonth)
  const plans = useInstallmentPlans(yearMonth)
  const installmentMonths = new Map(
    (plans.data ?? []).map((plan) => [plan.id, plan.installmentMonths]),
  )

  const rows = transactions.data ?? []
  const byDate = groupByDate(rows)
  const dayRows = byDate.get(selectedDate) ?? []
  const commitments = [...rows]
    .filter((row) => row.kind !== 'GENERAL')
    .sort((a, b) => a.spentDate.localeCompare(b.spentDate))
  const paid = commitments.filter((row) => row.spentDate <= today)
  const planned = commitments.filter((row) => row.spentDate > today)

  return (
    <div>
      <div className="mb-3.5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground text-sm">이번 달 지출</p>
          <p className="tabular-amount text-2xl font-extrabold">
            {summary.data ? formatKrw(summary.data.total) : '–'}
          </p>
        </div>
        {summary.data && summary.data.total > 0 && (
          <div className="max-w-lg min-w-72 flex-1">
            <div className="flex h-2.5 overflow-hidden rounded-full">
              {summary.data.kinds.map((kind) => (
                <div
                  key={kind.kind}
                  style={{
                    width: `${kind.ratio ?? 0}%`,
                    backgroundColor: expenseKindColor(kind.kind),
                  }}
                />
              ))}
            </div>
            <div className="text-muted-foreground mt-2 flex flex-wrap gap-3.5 text-xs">
              {summary.data.kinds.map((kind) => (
                <span key={kind.kind}>
                  <i
                    className="mr-1 inline-block size-2 rounded-full"
                    style={{ backgroundColor: expenseKindColor(kind.kind) }}
                  />
                  {expenseKindLabel(kind.kind)}{' '}
                  <b className="text-foreground">{formatKrwCompact(kind.amount)}</b> (
                  {formatPercentage(kind.ratio)})
                </span>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-1.5">
        <span className="text-muted-foreground mr-1 text-xs">하이라이트</span>
        <HighlightChip active={highlight === null} onClick={() => setHighlight(null)}>
          전체
        </HighlightChip>
        {EXPENSE_KINDS.map((kind) => (
          <HighlightChip
            key={kind}
            active={highlight?.type === 'kind' && highlight.value === kind}
            onClick={() => setHighlight({ type: 'kind', value: kind })}
          >
            <i
              className="mr-1 inline-block size-2 rounded-full"
              style={{ backgroundColor: expenseKindColor(kind) }}
            />
            {expenseKindLabel(kind)}
          </HighlightChip>
        ))}
        <span className="text-muted-foreground mr-1 ml-2.5 text-xs">태그</span>
        {(tags.data ?? []).map(({ tag }) => (
          <HighlightChip
            key={tag}
            active={highlight?.type === 'tag' && highlight.value === tag}
            onClick={() => setHighlight({ type: 'tag', value: tag })}
          >
            {formatTag(tag)}
          </HighlightChip>
        ))}
      </div>

      <div className="grid items-start gap-4 xl:grid-cols-[1fr_340px]">
        <QueryState isPending={transactions.isPending} error={transactions.error}>
          <CalendarGrid
            yearMonth={yearMonth}
            byDate={byDate}
            installmentMonthsByPlan={installmentMonths}
            selectedDate={selectedDate}
            today={today}
            highlight={highlight}
            onSelectDate={setSelectedDate}
          />
        </QueryState>

        <div className="grid gap-4">
          <Section
            title={`${Number(selectedDate.slice(5, 7))}월 ${Number(selectedDate.slice(8))}일 (${weekdayLabel(selectedDate)})`}
            hint={
              dayRows.length > 0
                ? `${dayRows.length}건 · ${formatKrw(dayRows.reduce((sum, row) => sum + row.amount, 0))}`
                : undefined
            }
          >
            {dayRows.length === 0 && (
              <p className="text-muted-foreground py-4 text-center text-sm">
                이 날은 지출이 없어요 🎉
              </p>
            )}
            <ul>
              {dayRows.map((row) => {
                const category = categories.get(row.categoryId)
                return (
                  <li key={row.id}>
                    <button
                      type="button"
                      onClick={() => setEditing(row)}
                      className={cn(
                        'flex w-full items-center gap-2.5 border-b border-gray-100 py-2.5 text-left last:border-0',
                        !matchesHighlight(row, highlight) && 'opacity-35',
                      )}
                    >
                      <span
                        className="flex size-9 shrink-0 items-center justify-center rounded-lg text-lg"
                        style={{
                          backgroundColor: `color-mix(in srgb, ${category?.color ?? '#94a3b8'} 12%, transparent)`,
                        }}
                      >
                        {category?.icon}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-sm font-semibold">{row.merchant}</span>
                        <span className="text-muted-foreground block truncate text-[11px]">
                          {category?.name}
                          {row.paymentMethod ? ` · ${row.paymentMethod}` : ''}{' '}
                          {row.tags.map((tag) => formatTag(tag)).join(' ')}
                        </span>
                      </span>
                      <span className="tabular-amount text-right text-sm font-bold">
                        -{row.amount.toLocaleString('ko-KR')}
                        <small
                          className="block text-[10px] font-semibold"
                          style={{ color: expenseKindColor(row.kind) }}
                        >
                          {expenseKindShortLabel(row.kind)}
                          {row.installmentSequence
                            ? ` ${row.installmentSequence}/${installmentMonths.get(row.installmentPlanId!) ?? '?'}`
                            : ''}
                        </small>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
            <Button
              variant="outline"
              size="sm"
              className="mt-2.5 w-full"
              onClick={() => openExpenseForm(selectedDate)}
            >
              + 이 날짜에 지출 추가
            </Button>
          </Section>

          <Section
            title="이번 달 고정 지출 일정"
            hint={`완료 ${formatKrw(paid.reduce((sum, row) => sum + row.amount, 0))}${planned.length ? ` · 예정 ${formatKrw(planned.reduce((sum, row) => sum + row.amount, 0))}` : ''}`}
          >
            {commitments.length === 0 && (
              <p className="text-muted-foreground text-sm">이 달의 고정비·할부가 없어요.</p>
            )}
            <ul className="grid gap-0.5">
              {commitments.map((row) => {
                const done = row.spentDate <= today
                return (
                  <li key={row.id} className="flex items-center gap-2.5 py-1.5 text-[13px]">
                    <span className="text-muted-foreground w-10 text-xs">{`${Number(row.spentDate.slice(5, 7))}/${Number(row.spentDate.slice(8))}`}</span>
                    <i
                      className="inline-block size-2 rounded-full"
                      style={{ backgroundColor: expenseKindColor(row.kind) }}
                    />
                    <span
                      className={cn('min-w-0 flex-1 truncate', done && 'text-muted-foreground')}
                    >
                      {row.merchant}
                      {row.installmentSequence
                        ? ` (${row.installmentSequence}/${installmentMonths.get(row.installmentPlanId!) ?? '?'})`
                        : ''}
                    </span>
                    <span className="tabular-amount">{row.amount.toLocaleString('ko-KR')}</span>
                    <span
                      className={cn(
                        'rounded px-1.5 py-0.5 text-[11px]',
                        done ? 'bg-slate-100 text-slate-500' : 'bg-amber-100 text-amber-700',
                      )}
                    >
                      {done ? '완료' : '예정'}
                    </span>
                  </li>
                )
              })}
            </ul>
          </Section>
        </div>
      </div>

      <TransactionEditDialog transaction={editing} onClose={() => setEditing(null)} />
    </div>
  )
}

function HighlightChip(props: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      aria-pressed={props.active}
      className={cn(
        'rounded-full border bg-white px-3 py-1 text-xs',
        props.active && 'border-slate-900 bg-slate-900 text-white',
      )}
    >
      {props.children}
    </button>
  )
}
