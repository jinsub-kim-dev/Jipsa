import { useState } from 'react'
import { Link } from 'react-router-dom'
import { QueryState } from '@/components/common/QueryState'
import { Section } from '@/components/common/Section'
import { useCategoryMap } from '@/features/categories/queries'
import { CategoryDonut } from '@/features/dashboard/CategoryDonut'
import { CategoryHeatmap } from '@/features/dashboard/CategoryHeatmap'
import { InsightBanner } from '@/features/dashboard/InsightBanner'
import { InstallmentPanel } from '@/features/dashboard/InstallmentPanel'
import { KindTrendChart } from '@/features/dashboard/KindTrendChart'
import { KpiCards } from '@/features/dashboard/KpiCards'
import {
  useCategoryTrend,
  useCommitmentForecast,
  useInsight,
  useKindTrend,
  useMonthlySummary,
  useTagRanking,
} from '@/features/dashboard/queries'
import { TagRanking } from '@/features/dashboard/TagRanking'
import { useInstallmentPlans } from '@/features/recurring/queries'
import { useTransactions } from '@/features/transactions/queries'
import { TransactionEditDialog } from '@/features/transactions/TransactionEditDialog'
import { transactionsLink } from '@/features/transactions/transactionFilter'
import { TransactionTable } from '@/features/transactions/TransactionTable'
import { formatKrw, formatTag } from '@/lib/format'
import { trendRangeOf, usePeriodStore } from '@/store/periodStore'
import type { Transaction } from '@/types/domain'

const RECENT_LIMIT = 10

/**
 * 대시보드. 인사이트 → KPI → 추이·비중 → 히트맵 → 태그·할부 → 최근 내역 순서다.
 *
 * 히트맵 행·도넛 조각·태그를 누르면 아래 최근 내역이 걸러진다. 이 필터는 이 화면 안에서만
 * 쓰이므로 지역 상태로 두고, "전체 보기" 를 누르면 같은 조건을 주소에 담아 거래 내역으로 넘긴다.
 */
export function DashboardPage() {
  const { yearMonth, setYearMonth } = usePeriodStore()
  const { from, to } = trendRangeOf(yearMonth)
  const categories = useCategoryMap()
  const [categoryFilter, setCategoryFilter] = useState<number>()
  const [tagFilter, setTagFilter] = useState<string>()
  const [editing, setEditing] = useState<Transaction | null>(null)

  const summary = useMonthlySummary(yearMonth)
  const insight = useInsight(yearMonth)
  const kindTrend = useKindTrend(from, to)
  const categoryTrend = useCategoryTrend(from, to, yearMonth)
  const tags = useTagRanking(yearMonth)
  const plans = useInstallmentPlans(yearMonth)
  const forecast = useCommitmentForecast(yearMonth)
  const recent = useTransactions({ month: yearMonth, categoryId: categoryFilter, tag: tagFilter })

  const toggleCategory = (id: number) =>
    setCategoryFilter((current) => (current === id ? undefined : id))
  const toggleTag = (tag: string) => setTagFilter((current) => (current === tag ? undefined : tag))
  const installmentMonths = new Map(
    (plans.data ?? []).map((plan) => [plan.id, plan.installmentMonths]),
  )
  const filtered = categoryFilter !== undefined || tagFilter !== undefined
  const recentRows = recent.data ?? []

  return (
    <div>
      {insight.data && <InsightBanner insight={insight.data} categories={categories} />}

      <QueryState isPending={summary.isPending} error={summary.error}>
        {summary.data && <KpiCards summary={summary.data} />}
      </QueryState>

      <section className="mb-4 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <Section title="월별 지출 추이" hint="종류별 누적 · 막대를 누르면 그 달로 이동">
          <QueryState isPending={kindTrend.isPending} error={kindTrend.error}>
            <KindTrendChart
              points={kindTrend.data ?? []}
              selectedMonth={yearMonth}
              onSelectMonth={setYearMonth}
            />
          </QueryState>
        </Section>
        <Section
          title="카테고리 비중"
          hint={
            categoryTrend.data
              ? `${categoryTrend.data.categories.filter((row) => row.targetAmount > 0).length}개 카테고리`
              : undefined
          }
        >
          <QueryState isPending={categoryTrend.isPending} error={categoryTrend.error}>
            {categoryTrend.data && (
              <CategoryDonut
                rows={categoryTrend.data.categories}
                categories={categories}
                onSelectCategory={toggleCategory}
              />
            )}
          </QueryState>
        </Section>
      </section>

      <Section
        className="mb-4"
        title="카테고리 × 월 히트맵"
        hint="행 기준 정규화 · 진할수록 그 카테고리를 많이 쓴 달 · 행을 누르면 최근 내역 필터"
      >
        <QueryState
          isPending={categoryTrend.isPending}
          error={categoryTrend.error}
          isEmpty={categoryTrend.data?.categories.length === 0}
          emptyMessage="이 기간에 기록된 지출이 없어요."
        >
          {categoryTrend.data && (
            <CategoryHeatmap
              trend={categoryTrend.data}
              categories={categories}
              selectedCategoryId={categoryFilter}
              onSelectCategory={toggleCategory}
            />
          )}
        </QueryState>
      </Section>

      <section className="mb-4 grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <Section title="태그 TOP 10" hint="클릭하면 최근 내역 필터 · 태그는 중복 집계">
          <QueryState
            isPending={tags.isPending}
            error={tags.error}
            isEmpty={tags.data?.length === 0}
            emptyMessage="태그가 붙은 지출이 없어요."
          >
            <TagRanking tags={tags.data ?? []} selectedTag={tagFilter} onSelectTag={toggleTag} />
          </QueryState>
        </Section>
        <Section
          title="진행 중인 할부"
          hint={
            plans.data
              ? `월 ${formatKrw(plans.data.reduce((sum, plan) => sum + (plan.progress?.amountThisMonth ?? 0), 0))}`
              : undefined
          }
        >
          <QueryState
            isPending={plans.isPending || forecast.isPending}
            error={plans.error ?? forecast.error}
          >
            <InstallmentPanel
              plans={plans.data ?? []}
              forecast={forecast.data ?? []}
              categories={categories}
            />
          </QueryState>
        </Section>
      </section>

      <Section
        title="최근 내역"
        hint={
          filtered
            ? `${recentRows.length}건 · ${formatKrw(recentRows.reduce((sum, row) => sum + row.amount, 0))}`
            : `최근 ${RECENT_LIMIT}건`
        }
        action={
          <div className="flex flex-wrap items-center gap-1.5">
            {categoryFilter !== undefined && (
              <FilterChip
                label={`${categories.get(categoryFilter)?.icon ?? ''} ${categories.get(categoryFilter)?.name ?? ''}`}
                onClear={() => setCategoryFilter(undefined)}
              />
            )}
            {tagFilter && (
              <FilterChip label={formatTag(tagFilter)} onClear={() => setTagFilter(undefined)} />
            )}
            <Link
              to={transactionsLink({ categoryId: categoryFilter, tag: tagFilter })}
              className="rounded-md border px-3 py-1.5 text-xs font-medium hover:bg-slate-50"
            >
              전체 보기 →
            </Link>
          </div>
        }
      >
        <QueryState
          isPending={recent.isPending}
          error={recent.error}
          isEmpty={recentRows.length === 0}
          emptyMessage="내역이 없어요."
        >
          <TransactionTable
            transactions={recentRows.slice(0, RECENT_LIMIT)}
            installmentMonthsByPlan={installmentMonths}
            onSelect={setEditing}
          />
        </QueryState>
      </Section>

      <TransactionEditDialog transaction={editing} onClose={() => setEditing(null)} />
    </div>
  )
}

function FilterChip({ label, onClear }: { label: string; onClear: () => void }) {
  return (
    <button
      type="button"
      onClick={onClear}
      className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-xs text-indigo-700"
    >
      {label} ✕
    </button>
  )
}
