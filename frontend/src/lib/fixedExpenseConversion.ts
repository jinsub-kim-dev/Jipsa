import { formatYearMonth } from '@/lib/format'
import type { FixedExpenseConversionResult } from '@/types/api'

/**
 * 고정비로 전환한 결과를 문장으로 만든다 (PRD 3.2.1).
 *
 * 무엇을 함께 바꿀지는 서버가 정했다. 화면은 몇 건이 바뀌었고 어느 달을 그대로 뒀는지만 알려준다.
 */
export function conversionSummary(result: FixedExpenseConversionResult): string[] {
  const { fixedExpense, convertedTransactionCount, ambiguousMonths } = result
  const lines = [
    `${fixedExpense.merchant}을(를) 매월 ${fixedExpense.dayOfMonth}일 고정비로 등록했어요.`,
    convertedTransactionCount > 1
      ? `같은 사용처의 거래까지 ${convertedTransactionCount}건을 고정비 거래로 바꿨어요.`
      : '이 거래를 고정비 거래로 바꿨어요.',
  ]
  if (ambiguousMonths.length > 0) {
    const months = ambiguousMonths.map(formatYearMonth).join(', ')
    lines.push(`${months}은(는) 같은 사용처가 여러 건이라 그대로 뒀어요.`)
  }
  return lines
}

/**
 * 전환 전에 묻는 문장. 같은 사용처의 다른 거래도 함께 바뀐다는 것을 먼저 알린다.
 * 납부일은 서버가 정하므로(소비일의 일) 여기서 계산해 보여주지 않는다.
 */
export function conversionConfirmMessage(merchant: string): string {
  return (
    `${merchant}을(를) 고정비로 전환할까요? 이 거래의 날짜가 매월 납부일이 돼요.
` +
    '같은 사용처의 다른 달 일반 지출도 함께 고정비 거래가 돼요. 고정비 거래는 하나씩 지울 수 없어요.'
  )
}
