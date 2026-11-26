import { ChangeText } from '@/components/common/ChangeText'
import { formatKrwCompact, formatMonthShort } from '@/lib/format'
import { cellOpacity, isDarkCell, normalizeRow } from '@/lib/heatmap'
import { cn } from '@/lib/utils'
import type { Category, CategoryTrend } from '@/types/domain'

/**
 * 카테고리 × 월 히트맵. 행 순서와 증감률은 서버가 정한다. 색 강도만 화면이 행 기준으로 정한다.
 */
export function CategoryHeatmap(props: {
  trend: CategoryTrend
  categories: Map<number, Category>
  selectedCategoryId?: number
  onSelectCategory: (categoryId: number) => void
}) {
  const { trend } = props
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-separate border-spacing-[3px] text-xs">
        <thead>
          <tr>
            <th />
            {trend.months.map((month) => (
              <th
                key={month}
                className={cn(
                  'px-1 py-1 font-medium',
                  month === trend.targetMonth ? 'font-bold text-blue-600' : 'text-muted-foreground',
                )}
              >
                {formatMonthShort(month)}
              </th>
            ))}
            <th className="text-muted-foreground px-1 py-1 text-right font-medium">전월 대비</th>
          </tr>
        </thead>
        <tbody>
          {trend.categories.map((row) => {
            const category = props.categories.get(row.categoryId)
            const intensities = normalizeRow(row.monthlyAmounts)
            const selected = row.categoryId === props.selectedCategoryId
            return (
              <tr
                key={row.categoryId}
                className="group cursor-pointer"
                onClick={() => props.onSelectCategory(row.categoryId)}
              >
                <th
                  scope="row"
                  className={cn(
                    'pr-3 text-left font-medium whitespace-nowrap group-hover:text-blue-600',
                    selected && 'font-bold text-blue-600',
                  )}
                >
                  {category ? `${category.icon} ${category.name}` : `#${row.categoryId}`}
                </th>
                {row.monthlyAmounts.map((amount, index) => {
                  const month = trend.months[index]
                  return (
                    <td
                      key={month}
                      title={`${formatMonthShort(month)} ${amount.toLocaleString('ko-KR')}원`}
                      className={cn(
                        'tabular-amount min-w-14 rounded-md px-1 py-2 text-center',
                        month === trend.targetMonth &&
                          'outline-2 -outline-offset-1 outline-blue-600',
                        isDarkCell(intensities[index]) && 'text-white',
                      )}
                      style={{
                        backgroundColor: `rgba(var(--heat-rgb), ${cellOpacity(intensities[index])})`,
                      }}
                    >
                      {amount > 0 ? formatKrwCompact(amount) : '·'}
                    </td>
                  )
                })}
                <td className="min-w-16 px-1 text-right whitespace-nowrap">
                  {row.targetAmount === 0 && row.changeRate === undefined ? (
                    <span className="text-muted-foreground">–</span>
                  ) : (
                    <ChangeText rate={row.changeRate} />
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
