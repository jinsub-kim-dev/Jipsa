package com.example.jipsa.domain.model

import java.time.YearMonth

/**
 * 한 달의 지출을 전월과 비교한 결과 (PRD 4절 "월 요약").
 *
 * 증감률이 `null` 이면 전월이 0원이라 정의할 수 없다는 뜻이다. 0% 와 구분해야 한다.
 */
data class SpendingComparison(
    val month: YearMonth,
    val current: KindAmounts,
    val previous: KindAmounts,
) {
    val totalChangeRate: ChangeRate? get() = ChangeRate.between(current.total, previous.total)

    fun changeRateOf(kind: ExpenseKind): ChangeRate? =
        ChangeRate.between(current.amountOf(kind), previous.amountOf(kind))
}
