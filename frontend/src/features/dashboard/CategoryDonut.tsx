import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { formatKrw } from '@/lib/format'
import type { Category, CategoryTrendRow } from '@/types/domain'

/** 기준월의 카테고리 비중. 조각을 누르면 그 카테고리로 최근 내역을 거른다. */
export function CategoryDonut(props: {
  rows: CategoryTrendRow[]
  categories: Map<number, Category>
  onSelectCategory: (categoryId: number) => void
}) {
  const data = props.rows
    .filter((row) => row.targetAmount > 0)
    .map((row) => {
      const category = props.categories.get(row.categoryId)
      return {
        categoryId: row.categoryId,
        name: category ? `${category.icon} ${category.name}` : `#${row.categoryId}`,
        value: row.targetAmount,
        color: category?.color ?? '#94a3b8',
      }
    })

  return (
    <div className="h-64">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius="58%"
            outerRadius="88%"
            isAnimationActive={false}
            cursor="pointer"
            onClick={(entry) =>
              props.onSelectCategory((entry as unknown as { categoryId: number }).categoryId)
            }
          >
            {data.map((entry) => (
              <Cell key={entry.categoryId} fill={entry.color} stroke="#fff" />
            ))}
          </Pie>
          <Tooltip formatter={(value) => formatKrw(Number(value))} />
          <Legend
            layout="vertical"
            align="right"
            verticalAlign="middle"
            iconType="circle"
            iconSize={8}
            wrapperStyle={{ fontSize: 12 }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
