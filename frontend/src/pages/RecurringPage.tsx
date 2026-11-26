import { QueryState } from '@/components/common/QueryState'
import { Section } from '@/components/common/Section'
import { TagChip } from '@/components/common/TagChip'
import { Button } from '@/components/ui/button'
import { useCategoryMap } from '@/features/categories/queries'
import {
  useCancelInstallmentPlan,
  useFixedExpenses,
  useInstallmentPlans,
  useTerminateFixedExpense,
} from '@/features/recurring/queries'
import { formatKrw, formatYearMonth } from '@/lib/format'
import { usePeriodStore } from '@/store/periodStore'
import { useUiStore } from '@/store/uiStore'

/**
 * 고정비·할부 관리. 등록은 지출 추가 모달에서 종류를 골라 한다.
 * 납부 여부·납부일·진행 상황은 선택한 달을 기준으로 서버가 계산한 값이다.
 */
export function RecurringPage() {
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const openExpenseForm = useUiStore((state) => state.openExpenseForm)
  const categories = useCategoryMap()
  const fixed = useFixedExpenses(yearMonth)
  const plans = useInstallmentPlans(yearMonth)
  const terminate = useTerminateFixedExpense()
  const cancel = useCancelInstallmentPlan()

  const activeFixed = (fixed.data ?? []).filter((item) => item.activeInMonth)

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section
        title="고정비"
        hint={`이번 달 ${activeFixed.length}건 · ${formatKrw(activeFixed.reduce((sum, item) => sum + item.amount, 0))}`}
        action={
          <Button size="sm" variant="outline" onClick={() => openExpenseForm()}>
            + 고정비 등록
          </Button>
        }
      >
        <QueryState
          isPending={fixed.isPending}
          error={fixed.error}
          isEmpty={fixed.data?.length === 0}
          emptyMessage="등록된 고정비가 없어요."
        >
          <ul>
            {(fixed.data ?? []).map((item) => {
              const category = categories.get(item.categoryId)
              return (
                <li key={item.id} className="flex items-center gap-3 border-b py-3 last:border-0">
                  <span className="text-lg">{category?.icon}</span>
                  <div className="min-w-0 flex-1">
                    <p
                      className={
                        item.activeInMonth
                          ? 'font-semibold'
                          : 'text-muted-foreground font-semibold line-through'
                      }
                    >
                      {item.merchant}
                    </p>
                    <p className="text-muted-foreground text-xs">
                      매월 {item.dayOfMonth}일
                      {item.activeInMonth &&
                      item.dueDateInMonth.slice(8) !== String(item.dayOfMonth).padStart(2, '0')
                        ? ` (이번 달 ${Number(item.dueDateInMonth.slice(8))}일)`
                        : ''}{' '}
                      · {formatYearMonth(item.startMonth)} ~{' '}
                      {item.endMonth ? formatYearMonth(item.endMonth) : '계속'}
                      {item.paymentMethod ? ` · ${item.paymentMethod}` : ''}
                    </p>
                    <div className="mt-1">
                      {item.tags.map((tag) => (
                        <TagChip key={tag} tag={tag} />
                      ))}
                    </div>
                  </div>
                  <span className="tabular-amount font-semibold">{formatKrw(item.amount)}</span>
                  {!item.endMonth && (
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={terminate.isPending}
                      onClick={() => {
                        const lastMonth = yearMonth
                        if (
                          window.confirm(
                            `${item.merchant} 고정비를 ${formatYearMonth(lastMonth)}까지로 끝낼까요? 그 뒤의 기록은 지워집니다.`,
                          )
                        ) {
                          terminate.mutate({ id: item.id, lastMonth })
                        }
                      }}
                    >
                      이 달로 종료
                    </Button>
                  )}
                </li>
              )
            })}
          </ul>
        </QueryState>
      </Section>

      <Section
        title="할부"
        hint={`이번 달 ${formatKrw((plans.data ?? []).reduce((sum, plan) => sum + (plan.progress?.amountThisMonth ?? 0), 0))}`}
        action={
          <Button size="sm" variant="outline" onClick={() => openExpenseForm()}>
            + 할부 등록
          </Button>
        }
      >
        <QueryState
          isPending={plans.isPending}
          error={plans.error}
          isEmpty={plans.data?.length === 0}
          emptyMessage="등록된 할부가 없어요."
        >
          <ul>
            {(plans.data ?? []).map((plan) => {
              const category = categories.get(plan.categoryId)
              const progress = plan.progress
              return (
                <li key={plan.id} className="border-b py-3 last:border-0">
                  <div className="flex items-center gap-3">
                    <span className="text-lg">{category?.icon}</span>
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold">{plan.merchant}</p>
                      <p className="text-muted-foreground text-xs">
                        {formatKrw(plan.totalAmount)} · {plan.installmentMonths}개월 · 월{' '}
                        {formatKrw(plan.monthlyAmount)}
                        {plan.firstInstallmentAmount !== plan.monthlyAmount
                          ? ` (1회차 ${formatKrw(plan.firstInstallmentAmount)})`
                          : ''}
                      </p>
                      <p className="text-muted-foreground text-xs">
                        {formatYearMonth(plan.firstBillingMonth)} ~{' '}
                        {formatYearMonth(plan.lastBillingMonth)} · 매월 {plan.billingDay}일
                      </p>
                    </div>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={cancel.isPending}
                      onClick={() => {
                        if (
                          window.confirm(
                            `${plan.merchant} 할부를 해지할까요? 모든 회차 기록이 함께 지워지고 되돌릴 수 없습니다.`,
                          )
                        ) {
                          cancel.mutate(plan.id)
                        }
                      }}
                    >
                      해지
                    </Button>
                  </div>
                  {progress ? (
                    <div className="mt-2">
                      <div className="text-muted-foreground mb-1 flex justify-between text-xs">
                        <span>
                          {progress.currentSequence}/{progress.installmentMonths}회차 · 이번 달{' '}
                          {formatKrw(progress.amountThisMonth)}
                        </span>
                        <span>남은 {formatKrw(progress.remainingAmount)}</span>
                      </div>
                      <div className="h-1.5 overflow-hidden rounded bg-violet-100">
                        <div
                          className="h-full bg-violet-500"
                          style={{
                            width: `${(progress.currentSequence / progress.installmentMonths) * 100}%`,
                          }}
                        />
                      </div>
                    </div>
                  ) : (
                    <p className="text-muted-foreground mt-1 text-xs">
                      {formatYearMonth(yearMonth)}은 할부 기간이 아니에요.
                    </p>
                  )}
                </li>
              )
            })}
          </ul>
        </QueryState>
      </Section>
    </div>
  )
}
