import { formatKrw, formatTag } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { TagTotal } from '@/types/domain'

/** 태그 TOP N. 거래 하나가 여러 태그에 들어가므로 합이 총 지출보다 클 수 있다 (PRD 4절). */
export function TagRanking(props: {
  tags: TagTotal[]
  selectedTag?: string
  onSelectTag: (tag: string) => void
}) {
  const max = props.tags[0]?.amount ?? 1
  return (
    <ul className="grid gap-1">
      {props.tags.map((item) => (
        <li key={item.tag}>
          <button
            type="button"
            onClick={() => props.onSelectTag(item.tag)}
            className="group grid w-full grid-cols-[88px_1fr_88px] items-center gap-2.5 py-1 text-left text-[13px]"
          >
            <span
              className={cn(
                'truncate group-hover:text-blue-600',
                item.tag === props.selectedTag && 'font-bold text-blue-600',
              )}
            >
              {formatTag(item.tag)}
            </span>
            <span className="h-2 overflow-hidden rounded bg-indigo-50">
              <span
                className="block h-full rounded bg-indigo-500"
                style={{ width: `${(item.amount / max) * 100}%` }}
              />
            </span>
            <span className="text-muted-foreground tabular-amount text-right">
              {formatKrw(item.amount)}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
