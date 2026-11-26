import { Lightbulb } from 'lucide-react'
import { formatKrwCompact, formatPercentage } from '@/lib/format'
import type { Category, SpendingInsight } from '@/types/domain'

/**
 * 인사이트 한 줄. 비교 기간(최근 N개월)과 판정은 서버가 정한 값을 그대로 쓴다.
 */
export function InsightBanner({
  insight,
  categories,
}: {
  insight: SpendingInsight
  categories: Map<number, Category>
}) {
  const top = insight.topIncreasedCategory
    ? categories.get(insight.topIncreasedCategory.categoryId)
    : undefined
  const rate = insight.changeRateFromBaseline

  return (
    <div className="mb-4 flex items-start gap-2.5 rounded-xl border border-blue-100 bg-gradient-to-r from-blue-50 to-violet-50 px-4 py-3 text-[15px] leading-relaxed">
      <Lightbulb className="mt-1 size-4 shrink-0 text-blue-600" />
      <p>
        {rate === undefined ? (
          <>비교할 이전 달 기록이 아직 부족해요. </>
        ) : (
          <>
            최근 {insight.baselineMonths}개월 평균보다{' '}
            <b className="text-blue-600">
              {Math.abs(rate).toFixed(0)}% {rate >= 0 ? '더' : '덜'}
            </b>{' '}
            썼어요.{' '}
          </>
        )}
        {top && insight.topIncreasedCategory && (
          <>
            가장 크게 늘어난 건{' '}
            <b className="text-blue-600">
              {top.icon} {top.name} (+{formatKrwCompact(insight.topIncreasedCategory.increase)})
            </b>
            이고,{' '}
          </>
        )}
        {insight.commitmentRatio !== undefined && (
          <>
            고정비·할부가 지출의{' '}
            <b className="text-blue-600">{formatPercentage(insight.commitmentRatio)}</b>를 차지해요.
          </>
        )}
      </p>
    </div>
  )
}
