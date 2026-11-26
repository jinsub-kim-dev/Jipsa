import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatYearMonth } from '@/lib/format'
import { currentYearMonth } from '@/lib/period'
import { usePeriodStore } from '@/store/periodStore'

/** 모든 메뉴가 공유하는 조회 연월 선택. */
export function MonthNavigator() {
  const { yearMonth, goToPreviousMonth, goToNextMonth, goToCurrentMonth } = usePeriodStore()
  const isCurrent = yearMonth === currentYearMonth()

  return (
    <div className="flex items-center gap-2">
      <Button variant="outline" size="icon" onClick={goToPreviousMonth} aria-label="이전 달">
        <ChevronLeft />
      </Button>
      <h1 className="min-w-36 text-center text-xl font-bold">{formatYearMonth(yearMonth)}</h1>
      <Button variant="outline" size="icon" onClick={goToNextMonth} aria-label="다음 달">
        <ChevronRight />
      </Button>
      {!isCurrent && (
        <Button variant="ghost" size="sm" onClick={goToCurrentMonth}>
          이번 달
        </Button>
      )}
    </div>
  )
}
