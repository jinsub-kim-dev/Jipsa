import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  expenseKindColor,
  expenseKindLabel,
  formatKrw,
  formatKrwCompact,
  formatMonthShort,
} from '@/lib/format'
import type { ExpenseKind, KindTrendPoint, YearMonthString } from '@/types/domain'

const SERIES: { key: 'general' | 'fixed' | 'installment'; kind: ExpenseKind }[] = [
  { key: 'general', kind: 'GENERAL' },
  { key: 'fixed', kind: 'FIXED' },
  { key: 'installment', kind: 'INSTALLMENT' },
]

/** 종류별 누적 막대. 막대를 누르면 그 달로 이동한다. 선택한 달 외에는 흐리게 그린다. */
export function KindTrendChart(props: {
  points: KindTrendPoint[]
  selectedMonth: YearMonthString
  onSelectMonth: (month: YearMonthString) => void
}) {
  return (
    <div className="h-64">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={props.points} margin={{ left: -8, right: 8 }}>
          <CartesianGrid vertical={false} stroke="#f1f5f9" />
          <XAxis
            dataKey="month"
            tickFormatter={formatMonthShort}
            tickLine={false}
            axisLine={false}
            fontSize={12}
          />
          <YAxis
            tickFormatter={formatKrwCompact}
            tickLine={false}
            axisLine={false}
            fontSize={11}
            width={56}
          />
          <Tooltip
            formatter={(value, name) => [formatKrw(Number(value)), String(name)]}
            labelFormatter={(label) => formatMonthShort(String(label))}
          />
          <Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12 }} />
          {SERIES.map(({ key, kind }) => (
            <Bar
              key={key}
              dataKey={key}
              name={expenseKindLabel(kind)}
              stackId="kind"
              fill={expenseKindColor(kind)}
              maxBarSize={46}
              isAnimationActive={false}
              cursor="pointer"
              onClick={(entry) => {
                const month = (entry as { payload?: KindTrendPoint }).payload?.month
                if (month) props.onSelectMonth(month)
              }}
            >
              {props.points.map((point) => (
                <Cell
                  key={point.month}
                  fillOpacity={point.month === props.selectedMonth ? 1 : 0.35}
                />
              ))}
            </Bar>
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
