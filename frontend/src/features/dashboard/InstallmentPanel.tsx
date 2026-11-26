import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { expenseKindColor, formatKrw, formatKrwCompact, formatMonthShort } from '@/lib/format'
import type { Category, InstallmentPlan, MonthlyCommitment } from '@/types/domain'

/**
 * 진행 중인 할부 + 앞으로 6개월 약정 지출 전망.
 * 회차·남은 금액·전망은 모두 서버가 계산한 값이다(1회차 나머지 가산 규칙을 화면에 두지 않는다).
 */
export function InstallmentPanel(props: {
  plans: InstallmentPlan[]
  forecast: MonthlyCommitment[]
  categories: Map<number, Category>
}) {
  const active = props.plans.filter((plan) => plan.progress)
  const ending = active.filter((plan) => plan.progress && plan.progress.remainingSequences <= 3)

  return (
    <div>
      {active.length === 0 && (
        <p className="text-muted-foreground py-4 text-sm">이 달에 진행 중인 할부가 없어요.</p>
      )}
      {active.map((plan) => {
        const progress = plan.progress!
        const category = props.categories.get(plan.categoryId)
        return (
          <div key={plan.id} className="border-b py-2.5 last:border-0">
            <div className="flex justify-between text-sm">
              <b className="font-semibold">
                {category?.icon} {plan.merchant}
              </b>
              <span className="tabular-amount">{formatKrw(progress.amountThisMonth)}</span>
            </div>
            <div className="text-muted-foreground mt-1 mb-2 flex justify-between gap-2 text-xs">
              <span>
                {progress.currentSequence}/{progress.installmentMonths}회차
                {plan.paymentMethod ? ` · ${plan.paymentMethod}` : ''}
              </span>
              <span>
                남은 {formatKrw(progress.remainingAmount)} ·{' '}
                {formatMonthShort(plan.lastBillingMonth)} 종료
              </span>
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
        )
      })}

      <div className="mt-4 border-t border-dashed pt-4">
        <h3 className="text-[13px] font-semibold">앞으로 6개월 약정 지출 전망</h3>
        <p className="text-muted-foreground text-xs">이미 약속된 고정비 + 남은 할부 회차</p>
        <div className="mt-2 h-36">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={props.forecast} margin={{ left: -12 }}>
              <XAxis
                dataKey="month"
                tickFormatter={formatMonthShort}
                tickLine={false}
                axisLine={false}
                fontSize={11}
              />
              <YAxis
                tickFormatter={formatKrwCompact}
                tickLine={false}
                axisLine={false}
                fontSize={10}
                width={52}
                tickCount={4}
              />
              <Tooltip
                formatter={(value, name) => [formatKrw(Number(value)), String(name)]}
                labelFormatter={(label) => formatMonthShort(String(label))}
              />
              <Bar
                dataKey="fixed"
                name="고정비"
                stackId="c"
                fill={expenseKindColor('FIXED')}
                maxBarSize={28}
                isAnimationActive={false}
              />
              <Bar
                dataKey="installment"
                name="할부"
                stackId="c"
                fill={expenseKindColor('INSTALLMENT')}
                maxBarSize={28}
                isAnimationActive={false}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
        {ending.length > 0 && (
          <ul className="text-muted-foreground mt-2 grid gap-0.5 text-xs leading-relaxed">
            {ending.map((plan) => (
              <li key={plan.id}>
                <b className="text-foreground font-semibold">{plan.merchant}</b>{' '}
                {plan.progress!.lastSequence
                  ? `이번 달 마지막 회차 → 다음 달부터 월 -${formatKrwCompact(plan.monthlyAmount)}`
                  : `${plan.progress!.remainingSequences}회 남음 → ${formatMonthShort(plan.lastBillingMonth)} 종료`}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
