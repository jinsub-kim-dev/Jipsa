package com.example.jipsa.domain.service

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ChangeRate
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.query.MonthlyCategoryExpense
import java.time.YearMonth

/**
 * 카테고리 × 월 지출 표를 만드는 도메인 서비스 (PRD 4절 "카테고리 × 월").
 *
 * 조회 포트는 거래가 있는 (카테고리, 월) 조합만 내려준다. 빈 칸을 0원으로 채우고,
 * 기준월의 전월 대비 증감률을 붙이고, 정렬 순서를 확정하는 일을 여기서 한다.
 * 정렬이 화면에 흩어지면 화면마다 순서가 달라진다.
 */
class CategoryTrendAnalyzer {

    /**
     * @param months 표의 열. 오래된 달부터 순서대로
     * @param targetMonth 강조하고 정렬 기준으로 삼는 달. [months] 안에 있어야 한다
     * @return 기준월 지출 내림차순, 같으면 카테고리 ID 오름차순. 기간 내 지출이 없는 카테고리는 빠진다
     */
    fun analyze(
        months: List<YearMonth>,
        targetMonth: YearMonth,
        expenses: List<MonthlyCategoryExpense>,
    ): List<CategoryTrend> {
        val previousMonth = targetMonth.minusMonths(1)
        return expenses
            .groupBy(MonthlyCategoryExpense::categoryId)
            .map { (categoryId, rows) ->
                val byMonth = rows.associate { it.month to it.amount }
                val target = byMonth[targetMonth] ?: Money.ZERO
                CategoryTrend(
                    categoryId = categoryId,
                    monthlyAmounts = months.map { byMonth[it] ?: Money.ZERO },
                    targetAmount = target,
                    changeRate = ChangeRate.between(target, byMonth[previousMonth] ?: Money.ZERO),
                )
            }
            .sortedWith(compareByDescending<CategoryTrend> { it.targetAmount }.thenBy { it.categoryId.value })
    }
}

/** 카테고리 하나의 월별 지출. [monthlyAmounts] 는 요청한 달의 순서와 같다. */
data class CategoryTrend(
    val categoryId: CategoryId,
    val monthlyAmounts: List<Money>,
    val targetAmount: Money,
    /** 기준월의 전월 대비. 전월이 0원이면 `null`(신규)이다. */
    val changeRate: ChangeRate?,
)
