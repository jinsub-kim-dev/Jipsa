import { AlertTriangle, CheckCircle2, FileSpreadsheet, Loader2, XCircle } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { transactionsLink } from '@/features/transactions/transactionFilter'
import { countByReason, type ImportJob, needsAttention, unprocessedLabel } from '@/lib/cardImport'
import { formatKrw, formatMonthDay } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { CardImportItem, CardImportResult } from '@/types/api'

/** 올린 파일 하나의 진행 상태와 결과. */
export function CardImportResultCard({ job }: { job: ImportJob }) {
  return (
    <Card className="p-5" aria-label={`${job.fileName} 가져오기 결과`}>
      <div className="mb-3 flex items-center gap-2">
        <FileSpreadsheet className="text-muted-foreground size-4 shrink-0" />
        <b className="min-w-0 truncate text-sm">{job.fileName}</b>
        <StatusBadge job={job} />
      </div>
      {job.status === 'uploading' && (
        <p className="text-muted-foreground text-sm">파일을 읽고 내역을 반영하는 중이에요…</p>
      )}
      {job.status === 'failed' && <p className="text-destructive text-sm">{job.message}</p>}
      {job.status === 'done' && <ResultBody result={job.result} />}
    </Card>
  )
}

function StatusBadge({ job }: { job: ImportJob }) {
  if (job.status === 'uploading') {
    return (
      <span className="text-muted-foreground ml-auto flex items-center gap-1 text-xs">
        <Loader2 className="size-3.5 animate-spin" />
        처리 중
      </span>
    )
  }
  if (job.status === 'failed') {
    return (
      <span className="text-destructive ml-auto flex items-center gap-1 text-xs font-medium">
        <XCircle className="size-3.5" />
        가져오지 못함
      </span>
    )
  }
  const attention = needsAttention(job.result)
  return (
    <span
      className={cn(
        'ml-auto flex items-center gap-1 text-xs font-medium',
        attention ? 'text-amber-600' : 'text-emerald-600',
      )}
    >
      {attention ? <AlertTriangle className="size-3.5" /> : <CheckCircle2 className="size-3.5" />}
      {job.result.paymentMethod} · {attention ? '확인할 내역 있음' : '완료'}
    </span>
  )
}

function ResultBody({ result }: { result: CardImportResult }) {
  return (
    <div className="space-y-4">
      <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
        <Stat label="전체 줄" value={result.totalRowCount} />
        <Stat
          label="추가"
          value={result.importedCount}
          hint={
            result.importedInstallmentPlanCount > 0
              ? `할부 ${result.importedInstallmentPlanCount}건 포함`
              : undefined
          }
        />
        <Stat label="고정비" value={result.fixedExpenseCount} hint="등록된 고정비" />
        <Stat label="중복 무시" value={result.duplicateCount} hint="이미 있는 내역" />
        <Stat
          label="미분류"
          value={result.uncategorizedCount}
          tone={result.uncategorizedCount > 0 ? 'warn' : undefined}
        />
        <Stat
          label="처리 안 됨"
          value={result.unprocessedCount}
          tone={result.unprocessedCount > 0 ? 'error' : undefined}
        />
      </dl>

      {result.fixedExpenseCount > 0 && (
        <Collapsible
          title={`고정비 ${result.fixedExpenseCount}건`}
          description="등록된 고정비와 사용처가 같아 그 달의 고정비 거래로 넣었어요. 자동 기록이 있던 달은 카드의 날짜·금액으로 고쳤어요."
        >
          <PurchaseTable items={result.fixedExpenses} />
        </Collapsible>
      )}
      {result.uncategorizedCount > 0 && <UncategorizedList result={result} />}
      {result.unprocessedCount > 0 && <UnprocessedList result={result} />}
    </div>
  )
}

function Stat(props: { label: string; value: number; hint?: string; tone?: 'warn' | 'error' }) {
  return (
    <div className="bg-muted/40 rounded-md px-3 py-2">
      <dt className="text-muted-foreground text-xs">{props.label}</dt>
      <dd
        className={cn(
          'text-lg font-semibold tabular-nums',
          props.tone === 'warn' && 'text-amber-600',
          props.tone === 'error' && 'text-destructive',
        )}
      >
        {props.value.toLocaleString('ko-KR')}
        {props.hint && (
          <span className="text-muted-foreground ml-1 text-[11px] font-normal">{props.hint}</span>
        )}
      </dd>
    </div>
  )
}

function UncategorizedList({ result }: { result: CardImportResult }) {
  return (
    <Collapsible
      title={`미분류 ${result.uncategorizedCount}건`}
      description="분류 규칙에 맞지 않아 '미분류' 카테고리에 넣었어요. 거래 내역에서 카테고리를 바꿀 수 있어요."
      action={
        <Link
          to={transactionsLink({ categoryId: result.uncategorizedCategoryId })}
          className="text-primary text-xs font-medium hover:underline"
        >
          미분류 거래 보기 →
        </Link>
      }
    >
      <PurchaseTable items={result.uncategorized} />
    </Collapsible>
  )
}

function PurchaseTable({ items }: { items: CardImportItem[] }) {
  return (
    <table className="w-full text-sm">
      <tbody>
        {items.map((item, index) => (
          <tr key={`${item.spentDate}-${item.merchant}-${index}`} className="border-t">
            <td className="text-muted-foreground w-16 py-1.5 tabular-nums">
              {formatMonthDay(item.spentDate)}
            </td>
            <td className="py-1.5">
              {item.merchant}
              {item.installmentMonths !== undefined && (
                <span className="text-muted-foreground ml-1 text-xs">
                  {item.installmentMonths}개월 할부
                </span>
              )}
            </td>
            <td className="py-1.5 text-right tabular-nums">{formatKrw(item.amount)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function UnprocessedList({ result }: { result: CardImportResult }) {
  const reasons = countByReason(result.unprocessed)
  return (
    <Collapsible
      title={`처리 안 됨 ${result.unprocessedCount}건`}
      description="아래 내역은 가계부에 넣지 않았어요."
      defaultOpen
    >
      <ul className="mb-3 space-y-1">
        {reasons.map(({ reason, count }) => (
          <li key={reason} className="flex items-start gap-2 text-sm">
            <span className="bg-destructive/10 text-destructive shrink-0 rounded px-1.5 text-xs font-semibold tabular-nums">
              {count}건
            </span>
            <span>{reason}</span>
          </li>
        ))}
      </ul>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-muted-foreground text-left text-xs">
            <th className="py-1 font-normal">항목</th>
            <th className="py-1 font-normal">사유</th>
            <th className="py-1 text-right font-normal">금액</th>
          </tr>
        </thead>
        <tbody>
          {result.unprocessed.map((item, index) => (
            <tr key={index} className="border-t align-top">
              <td className="py-1.5 pr-3">
                <div className="font-medium">{unprocessedLabel(item)}</div>
                {item.content && (
                  <div className="text-muted-foreground mt-0.5 text-xs break-all">
                    {item.content}
                  </div>
                )}
              </td>
              <td className="py-1.5 pr-3">{item.reason}</td>
              <td className="py-1.5 text-right tabular-nums">
                {item.amount ? formatKrw(item.amount) : '–'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Collapsible>
  )
}

function Collapsible(props: {
  title: string
  description: string
  action?: ReactNode
  defaultOpen?: boolean
  children: ReactNode
}) {
  const [open, setOpen] = useState(props.defaultOpen ?? false)
  return (
    <section className="rounded-md border">
      <div className="flex flex-wrap items-center gap-2 px-3 py-2">
        <button
          type="button"
          className="text-sm font-semibold hover:underline"
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          {open ? '▾' : '▸'} {props.title}
        </button>
        {props.action && <span className="ml-auto">{props.action}</span>}
      </div>
      {open && (
        <div className="border-t px-3 py-2">
          <p className="text-muted-foreground mb-2 text-xs">{props.description}</p>
          {props.children}
        </div>
      )}
    </section>
  )
}
