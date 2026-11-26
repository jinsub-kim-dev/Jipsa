import { ChangeText } from '@/components/common/ChangeText'
import { Card } from '@/components/ui/card'
import { expenseKindColor, expenseKindLabel, formatKrw, formatPercentage } from '@/lib/format'
import type { MonthlySummary } from '@/types/domain'

export function KpiCards({ summary }: { summary: MonthlySummary }) {
  const hasPrevious = summary.previousTotal > 0
  return (
    <section className="mb-4 grid grid-cols-2 gap-4 xl:grid-cols-4">
      <Kpi
        label="이번 달 총 지출"
        amount={summary.total}
        rate={summary.totalChangeRate}
        hasPrevious={hasPrevious}
      />
      {summary.kinds.map((kind) => (
        <Kpi
          key={kind.kind}
          label={`${expenseKindLabel(kind.kind)} · ${formatPercentage(kind.ratio)}`}
          amount={kind.amount}
          rate={kind.changeRate}
          hasPrevious={hasPrevious}
          color={expenseKindColor(kind.kind)}
          ratio={kind.ratio}
        />
      ))}
    </section>
  )
}

function Kpi(props: {
  label: string
  amount: number
  rate?: number
  hasPrevious: boolean
  color?: string
  ratio?: number
}) {
  return (
    <Card className="p-5">
      <p className="text-muted-foreground flex items-center gap-1.5 text-[13px]">
        {props.color && (
          <span className="size-2 rounded-full" style={{ backgroundColor: props.color }} />
        )}
        {props.label}
      </p>
      <p className="tabular-amount mt-2 mb-1.5 text-2xl font-extrabold tracking-tight">
        {formatKrw(props.amount)}
      </p>
      <p className="text-muted-foreground text-xs">
        {props.hasPrevious ? (
          <>
            지난달 대비 <ChangeText rate={props.rate} />
          </>
        ) : (
          '지난달 기록 없음'
        )}
      </p>
      {props.color && (
        <div className="mt-3 h-1 overflow-hidden rounded bg-slate-100">
          <div
            className="h-full"
            style={{ width: `${Math.min(100, props.ratio ?? 0)}%`, backgroundColor: props.color }}
          />
        </div>
      )}
    </Card>
  )
}
