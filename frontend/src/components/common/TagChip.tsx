import type { ComponentProps } from 'react'
import { formatTag } from '@/lib/format'
import { cn } from '@/lib/utils'

/** 태그 표시. `removable` 이면 뒤에 ✕ 를 붙인다(누르는 처리는 감싸는 쪽이 한다). */
export function TagChip({
  tag,
  removable = false,
  className,
  ...props
}: { tag: string; removable?: boolean } & ComponentProps<'span'>) {
  return (
    <span
      className={cn(
        'mr-1 rounded bg-indigo-50 px-1.5 py-0.5 text-xs whitespace-nowrap text-indigo-700',
        className,
      )}
      {...props}
    >
      {formatTag(tag)}
      {removable && ' ✕'}
    </span>
  )
}
