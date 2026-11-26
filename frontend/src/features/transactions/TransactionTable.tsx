import { CategoryLabel } from '@/components/common/CategoryLabel'
import { KindBadge } from '@/components/common/KindBadge'
import { TagChip } from '@/components/common/TagChip'
import { useCategoryMap } from '@/features/categories/queries'
import { formatMonthDay } from '@/lib/format'
import type { Transaction } from '@/types/domain'

interface TransactionTableProps {
  transactions: Transaction[]
  /** 할부 회차 표기(9/12)에 쓰는 계획별 개월 수. */
  installmentMonthsByPlan?: Map<number, number>
  onSelect?: (transaction: Transaction) => void
  onTagClick?: (tag: string) => void
  showPaymentMethod?: boolean
}

export function TransactionTable({
  transactions,
  installmentMonthsByPlan,
  onSelect,
  onTagClick,
  showPaymentMethod = false,
}: TransactionTableProps) {
  const categories = useCategoryMap()

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-muted-foreground border-b text-left text-xs">
            <th className="px-2 py-2.5 font-medium">날짜</th>
            <th className="px-2 py-2.5 font-medium">사용처</th>
            <th className="hidden px-2 py-2.5 font-medium sm:table-cell">카테고리</th>
            <th className="px-2 py-2.5 font-medium">종류</th>
            <th className="hidden px-2 py-2.5 font-medium md:table-cell">태그</th>
            {showPaymentMethod && (
              <th className="hidden px-2 py-2.5 font-medium lg:table-cell">결제 수단</th>
            )}
            <th className="px-2 py-2.5 text-right font-medium">금액</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => {
            const total = transaction.installmentPlanId
              ? installmentMonthsByPlan?.get(transaction.installmentPlanId)
              : undefined
            return (
              <tr
                key={transaction.id}
                className="hover:bg-muted/60 cursor-pointer border-b border-gray-100 last:border-0"
                onClick={() => onSelect?.(transaction)}
              >
                <td className="text-muted-foreground px-2 py-2.5 whitespace-nowrap">
                  {formatMonthDay(transaction.spentDate)}
                </td>
                <td className="px-2 py-2.5 font-semibold">
                  {transaction.merchant}
                  {transaction.installmentSequence && (
                    <span className="text-muted-foreground ml-1 font-normal">
                      ({transaction.installmentSequence}
                      {total ? `/${total}` : '회차'})
                    </span>
                  )}
                </td>
                <td className="hidden px-2 py-2.5 sm:table-cell">
                  <CategoryLabel category={categories.get(transaction.categoryId)} />
                </td>
                <td className="px-2 py-2.5">
                  <KindBadge kind={transaction.kind} />
                </td>
                <td className="hidden px-2 py-2.5 md:table-cell">
                  {transaction.tags.map((tag) => (
                    <TagChip
                      key={tag}
                      tag={tag}
                      className={onTagClick ? 'cursor-pointer hover:bg-indigo-100' : undefined}
                      onClick={(event) => {
                        if (!onTagClick) return
                        event.stopPropagation()
                        onTagClick(tag)
                      }}
                    />
                  ))}
                </td>
                {showPaymentMethod && (
                  <td className="text-muted-foreground hidden px-2 py-2.5 lg:table-cell">
                    {transaction.paymentMethod ?? '–'}
                  </td>
                )}
                <td className="tabular-amount px-2 py-2.5 text-right font-semibold whitespace-nowrap">
                  -{transaction.amount.toLocaleString('ko-KR')}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
