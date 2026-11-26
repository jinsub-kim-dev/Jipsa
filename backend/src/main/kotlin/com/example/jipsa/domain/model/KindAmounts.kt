package com.example.jipsa.domain.model

/**
 * 지출 종류별 합계.
 *
 * 월 요약과 추이의 기본 단위다. 비중 계산은 [Ratio] 규칙을 따른다.
 */
data class KindAmounts(
    val general: Money,
    val fixed: Money,
    val installment: Money,
) {
    val total: Money get() = general + fixed + installment

    /** 약정 지출(고정비 + 할부). */
    val commitment: Money get() = fixed + installment

    fun amountOf(kind: ExpenseKind): Money = when (kind) {
        ExpenseKind.GENERAL -> general
        ExpenseKind.FIXED -> fixed
        ExpenseKind.INSTALLMENT -> installment
    }

    /** 총 지출에서 [kind] 가 차지하는 비중. 총 지출이 0원이면 `null` 이다. */
    fun ratioOf(kind: ExpenseKind): Ratio? = Ratio.of(amountOf(kind), total)

    /** 총 지출에서 약정 지출이 차지하는 비중. 총 지출이 0원이면 `null` 이다. */
    val commitmentRatio: Ratio? get() = Ratio.of(commitment, total)

    companion object {
        val ZERO: KindAmounts = KindAmounts(Money.ZERO, Money.ZERO, Money.ZERO)

        /** 종류별 금액 맵에서 만든다. 없는 종류는 0원이다. */
        fun of(amounts: Map<ExpenseKind, Money>): KindAmounts = KindAmounts(
            general = amounts[ExpenseKind.GENERAL] ?: Money.ZERO,
            fixed = amounts[ExpenseKind.FIXED] ?: Money.ZERO,
            installment = amounts[ExpenseKind.INSTALLMENT] ?: Money.ZERO,
        )
    }
}

/** 한 달의 지출 종류별 합계. */
data class MonthlyKindAmounts(
    val month: java.time.YearMonth,
    val amounts: KindAmounts,
)
