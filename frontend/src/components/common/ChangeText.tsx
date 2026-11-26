import { formatSignedPercentage } from '@/lib/format'
import { cn } from '@/lib/utils'

/**
 * 증감률 표시. **지출이 늘면 빨강, 줄면 초록**이다. 부호가 아니라 의미로 색을 정한다.
 * 증감률이 없으면(이전 값 0원) "신규" 로 표기한다.
 */
export function ChangeText({ rate, className }: { rate: number | undefined; className?: string }) {
  const tone =
    rate === undefined
      ? 'text-muted-foreground'
      : rate > 0
        ? 'text-increase'
        : rate < 0
          ? 'text-decrease'
          : ''
  const arrow = rate === undefined || rate === 0 ? '' : rate > 0 ? '▲ ' : '▼ '
  return (
    <span className={cn('tabular-amount font-semibold', tone, className)}>
      {arrow}
      {formatSignedPercentage(rate === undefined ? undefined : Math.abs(rate)).replace('+', '')}
    </span>
  )
}
