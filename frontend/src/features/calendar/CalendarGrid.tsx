import { buildMonthGrid } from '@/lib/calendar'
import { normalizeRow } from '@/lib/heatmap'
import { cn } from '@/lib/utils'
import type { IsoDate, Transaction, YearMonthString } from '@/types/domain'
import { matchesHighlight, type CalendarHighlight } from './highlight'

const KIND_ORDER: Transaction['kind'][] = ['GENERAL', 'FIXED', 'INSTALLMENT']

/**
 * 월 달력. 칸 배경은 그날 일반 지출의 강도(표시 전용), 칸 아래 막대는 종류별 비중이다.
 * 고정비·할부가 나간 날은 이름표로 표시한다.
 */
export function CalendarGrid(props: {
  yearMonth: YearMonthString
  byDate: Map<IsoDate, Transaction[]>
  installmentMonthsByPlan: Map<number, number>
  selectedDate: IsoDate
  today: IsoDate
  highlight: CalendarHighlight
  onSelectDate: (date: IsoDate) => void
}) {
  const grid = buildMonthGrid(props.yearMonth)
  const dated = grid.filter((cell) => cell.date !== null)
  const generalTotals = dated.map((cell) =>
    (props.byDate.get(cell.date!) ?? [])
      .filter((t) => t.kind === 'GENERAL')
      .reduce((sum, t) => sum + t.amount, 0),
  )
  const shownTotals = dated.map((cell) =>
    (props.byDate.get(cell.date!) ?? [])
      .filter((t) => matchesHighlight(t, props.highlight))
      .reduce((sum, t) => sum + t.amount, 0),
  )
  const levels = normalizeRow(props.highlight ? shownTotals : generalTotals)
  const intensity = new Map(dated.map((cell, index) => [cell.date!, levels[index]]))

  return (
    <div className="overflow-hidden rounded-xl border bg-white">
      <div className="grid grid-cols-7 border-b text-center text-xs">
        {'일월화수목금토'.split('').map((day, index) => (
          <div
            key={day}
            className={cn(
              'py-2.5',
              index === 0
                ? 'text-red-600'
                : index === 6
                  ? 'text-blue-600'
                  : 'text-muted-foreground',
            )}
          >
            {day}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {grid.map((cell, index) => {
          if (cell.date === null)
            return (
              <div
                key={`empty-${index}`}
                className="min-h-24 border-r border-b border-gray-100 bg-slate-50 last:border-r-0"
              />
            )
          const all = props.byDate.get(cell.date) ?? []
          const shown = all.filter((t) => matchesHighlight(t, props.highlight))
          const total = shown.reduce((sum, t) => sum + t.amount, 0)
          const level = intensity.get(cell.date) ?? 0
          const future = cell.date > props.today
          return (
            <button
              key={cell.date}
              type="button"
              onClick={() => props.onSelectDate(cell.date!)}
              className={cn(
                'relative flex min-h-24 min-w-0 flex-col border-r border-b border-gray-100 p-1.5 text-left hover:outline-2 hover:-outline-offset-2 hover:outline-slate-400 [&:nth-child(7n)]:border-r-0',
                cell.date === props.selectedDate &&
                  'z-10 outline-2 -outline-offset-2 outline-slate-900',
                future && 'text-slate-300',
                props.highlight && all.length > 0 && total === 0 && 'opacity-30',
              )}
              style={{
                backgroundColor:
                  level > 0 ? `rgba(var(--heat-rgb), ${0.05 + level * 0.4})` : undefined,
              }}
            >
              <span
                className={cn(
                  'text-xs font-semibold',
                  cell.date === props.today &&
                    'inline-flex size-5 items-center justify-center rounded-full bg-slate-900 text-white',
                )}
              >
                {cell.day}
              </span>
              <span className="mt-1 hidden flex-wrap gap-0.5 sm:flex">
                {all
                  .filter((t) => t.kind !== 'GENERAL')
                  .map((t) => (
                    <span
                      key={t.id}
                      className={cn(
                        'max-w-full truncate rounded px-1 text-[10px] font-semibold',
                        t.kind === 'FIXED'
                          ? 'bg-amber-100 text-amber-700'
                          : 'bg-violet-100 text-violet-700',
                      )}
                    >
                      {t.kind === 'FIXED'
                        ? t.merchant
                        : `${t.merchant.split(' ')[0]} ${t.installmentSequence}/${props.installmentMonthsByPlan.get(t.installmentPlanId!) ?? '?'}`}
                    </span>
                  ))}
              </span>
              {total > 0 && (
                <>
                  <span className="tabular-amount mt-auto text-right text-[11px] font-bold sm:text-[13px]">
                    {total.toLocaleString('ko-KR')}
                  </span>
                  <span className="mt-1 flex h-[3px] overflow-hidden rounded">
                    {KIND_ORDER.map((kind) => {
                      const part = shown
                        .filter((t) => t.kind === kind)
                        .reduce((sum, t) => sum + t.amount, 0)
                      return part > 0 ? (
                        <i
                          key={kind}
                          style={{
                            width: `${(part / total) * 100}%`,
                            backgroundColor: `var(--color-kind-${kind.toLowerCase()})`,
                          }}
                        />
                      ) : null
                    })}
                  </span>
                </>
              )}
            </button>
          )
        })}
      </div>
      <div className="text-muted-foreground flex flex-wrap items-center justify-end gap-1.5 border-t px-3.5 py-2.5 text-[11px]">
        {props.highlight ? '선택한 조건의 지출 강도' : '일반 지출 강도'}
        {[0.06, 0.18, 0.32, 0.45].map((opacity) => (
          <i
            key={opacity}
            className="inline-block h-2.5 w-4 rounded-sm"
            style={{ backgroundColor: `rgba(var(--heat-rgb), ${opacity})` }}
          />
        ))}
        많음 · <span className="rounded bg-amber-100 px-1 font-semibold text-amber-700">고정</span>
        <span className="rounded bg-violet-100 px-1 font-semibold text-violet-700">할부</span>{' '}
        기록일
      </div>
    </div>
  )
}
