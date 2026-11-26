import type { ReactNode } from 'react'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/** 제목과 보조 설명을 가진 카드. 대시보드 블록의 공통 틀이다. */
export function Section(props: {
  title: ReactNode
  hint?: ReactNode
  action?: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <Card className={cn('min-w-0 p-5', props.className)}>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-[15px] font-semibold">
          {props.title}
          {props.hint && (
            <small className="text-muted-foreground ml-2 text-xs font-normal">{props.hint}</small>
          )}
        </h2>
        {props.action}
      </div>
      {props.children}
    </Card>
  )
}
