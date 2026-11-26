package com.example.jipsa.domain.service

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ChangeRate
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.Ratio
import com.example.jipsa.domain.query.MonthlyCategoryExpense
import java.time.YearMonth

/**
 * 대시보드 상단의 인사이트 한 줄을 만드는 도메인 서비스 (PRD 4절 "인사이트").
 *
 * 판정 기준(직전 몇 개월과 비교하는지)은 이 서비스가 갖고 응답에 함께 담는다.
 * 기준이 화면과 서버 두 곳에 있으면 반드시 어긋난다.
 */
class SpendingInsightAnalyzer {

    /**
     * @param monthlyAmounts [targetMonth] 와 직전 [BASELINE_MONTHS] 개월의 종류별 합계. 없는 달은 무시한다
     * @param categoryExpenses [targetMonth] 와 그 전월의 카테고리별 지출
     */
    fun analyze(
        targetMonth: YearMonth,
        monthlyAmounts: List<MonthlyKindAmounts>,
        categoryExpenses: List<MonthlyCategoryExpense>,
    ): SpendingInsight {
        val byMonth = monthlyAmounts.associate { it.month to it.amounts }
        val current = byMonth[targetMonth]?.total ?: Money.ZERO

        val baselineAverage = baselineAverage(targetMonth, byMonth.mapValues { it.value.total })

        return SpendingInsight(
            month = targetMonth,
            baselineMonths = BASELINE_MONTHS,
            total = current,
            baselineAverage = baselineAverage,
            changeRateFromBaseline = baselineAverage?.let { ChangeRate.between(current, it) },
            topIncreasedCategory = topIncreasedCategory(targetMonth, categoryExpenses),
            commitmentRatio = byMonth[targetMonth]?.commitmentRatio,
        )
    }

    /**
     * 직전 [BASELINE_MONTHS] 개월 중 **지출이 있는 달만의** 평균. 그런 달이 없으면 `null` 이다.
     *
     * 0원인 달을 평균에 넣지 않는 이유: 가계부를 쓰기 시작한 직후에는 이전 달이 비어 있다.
     * 빈 달을 0원으로 평균하면 "평소보다 3배 썼다" 는 거짓 인사이트가 나온다.
     */
    private fun baselineAverage(targetMonth: YearMonth, totals: Map<YearMonth, Money>): Money? {
        val baseline = (1..BASELINE_MONTHS)
            .mapNotNull { totals[targetMonth.minusMonths(it.toLong())] }
            .filter(Money::isPositive)
        if (baseline.isEmpty()) return null
        return Money.sum(baseline).divideWithRemainder(baseline.size).first
    }

    /** 전월보다 가장 많이 늘어난 카테고리. 늘어난 카테고리가 없으면 `null` 이다. */
    private fun topIncreasedCategory(
        targetMonth: YearMonth,
        expenses: List<MonthlyCategoryExpense>,
    ): CategoryIncrease? {
        val previousMonth = targetMonth.minusMonths(1)
        val current = expenses.filter { it.month == targetMonth }.associate { it.categoryId to it.amount }
        val previous = expenses.filter { it.month == previousMonth }.associate { it.categoryId to it.amount }

        return current
            .mapNotNull { (categoryId, amount) ->
                val before = previous[categoryId] ?: Money.ZERO
                if (amount > before) CategoryIncrease(categoryId, amount - before) else null
            }
            .maxWithOrNull(compareBy<CategoryIncrease> { it.increase }.thenByDescending { it.categoryId.value })
    }

    companion object {
        /** 비교 기준 기간. 응답에 담겨 화면 문구("최근 3개월 평균")에 쓰인다. */
        const val BASELINE_MONTHS: Int = 3
    }
}

data class SpendingInsight(
    val month: YearMonth,
    val baselineMonths: Int,
    val total: Money,
    /** 직전 기간 평균. 비교할 달이 없으면 `null` 이다. */
    val baselineAverage: Money?,
    val changeRateFromBaseline: ChangeRate?,
    val topIncreasedCategory: CategoryIncrease?,
    /** 약정 지출 비중. 그 달 지출이 없으면 `null` 이다. */
    val commitmentRatio: Ratio?,
)

data class CategoryIncrease(
    val categoryId: CategoryId,
    val increase: Money,
)
