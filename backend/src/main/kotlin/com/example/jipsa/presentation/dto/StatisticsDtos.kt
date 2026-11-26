package com.example.jipsa.presentation.dto

import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.SpendingComparison
import com.example.jipsa.domain.query.TagTotal
import com.example.jipsa.domain.service.CategoryTrend
import com.example.jipsa.domain.service.MonthlyCommitment
import com.example.jipsa.domain.service.SpendingInsight
import java.time.YearMonth

/*
 * 분석 응답. 비율·증감률은 백분율(소수 첫째 자리)이다.
 * 증감률이 없으면(null) 이전 값이 0원이라 정의할 수 없다는 뜻이다. 화면은 "신규" 로 표기한다.
 * (application.yml 의 non_null 설정으로 null 필드는 응답에서 빠진다)
 */

data class MonthlySummaryResponse(
    val month: YearMonth,
    val total: Long,
    val previousTotal: Long,
    val totalChangeRate: Double?,
    /** 일반 지출 · 고정비 · 할부 순서. */
    val kinds: List<KindSummary>,
    val commitmentRatio: Double?,
) {
    data class KindSummary(
        val kind: ExpenseKind,
        val amount: Long,
        val previousAmount: Long,
        val ratio: Double?,
        val changeRate: Double?,
    )

    companion object {
        fun from(comparison: SpendingComparison): MonthlySummaryResponse = MonthlySummaryResponse(
            month = comparison.month,
            total = comparison.current.total.amount,
            previousTotal = comparison.previous.total.amount,
            totalChangeRate = comparison.totalChangeRate?.percentage,
            kinds = ExpenseKind.entries.map { kind ->
                KindSummary(
                    kind = kind,
                    amount = comparison.current.amountOf(kind).amount,
                    previousAmount = comparison.previous.amountOf(kind).amount,
                    ratio = comparison.current.ratioOf(kind)?.percentage,
                    changeRate = comparison.changeRateOf(kind)?.percentage,
                )
            },
            commitmentRatio = comparison.current.commitmentRatio?.percentage,
        )
    }
}

data class KindTrendResponse(
    val month: YearMonth,
    val general: Long,
    val fixed: Long,
    val installment: Long,
    val total: Long,
) {
    companion object {
        fun from(monthly: MonthlyKindAmounts): KindTrendResponse = KindTrendResponse(
            month = monthly.month,
            general = monthly.amounts.general.amount,
            fixed = monthly.amounts.fixed.amount,
            installment = monthly.amounts.installment.amount,
            total = monthly.amounts.total.amount,
        )
    }
}

data class CategoryTrendResponse(
    val months: List<YearMonth>,
    val targetMonth: YearMonth,
    /** 기준월 지출 내림차순. 정렬은 서버가 확정한다. */
    val categories: List<CategoryRow>,
) {
    data class CategoryRow(
        val categoryId: Long,
        /** [months] 와 같은 순서. */
        val monthlyAmounts: List<Long>,
        val targetAmount: Long,
        val changeRate: Double?,
    )

    companion object {
        fun of(months: List<YearMonth>, targetMonth: YearMonth, trends: List<CategoryTrend>) = CategoryTrendResponse(
            months = months,
            targetMonth = targetMonth,
            categories = trends.map { trend ->
                CategoryRow(
                    categoryId = trend.categoryId.value,
                    monthlyAmounts = trend.monthlyAmounts.map { it.amount },
                    targetAmount = trend.targetAmount.amount,
                    changeRate = trend.changeRate?.percentage,
                )
            },
        )
    }
}

data class TagTotalResponse(
    val tag: String,
    val amount: Long,
    val transactionCount: Int,
) {
    companion object {
        fun from(total: TagTotal): TagTotalResponse = TagTotalResponse(total.tag.name, total.amount.amount, total.transactionCount)
    }
}

data class InsightResponse(
    val month: YearMonth,
    /** 비교 기간(개월). 화면 문구 "최근 N개월 평균" 에 쓴다. */
    val baselineMonths: Int,
    val total: Long,
    val baselineAverage: Long?,
    val changeRateFromBaseline: Double?,
    val topIncreasedCategory: CategoryIncreaseResponse?,
    val commitmentRatio: Double?,
) {
    data class CategoryIncreaseResponse(
        val categoryId: Long,
        val increase: Long,
    )

    companion object {
        fun from(insight: SpendingInsight): InsightResponse = InsightResponse(
            month = insight.month,
            baselineMonths = insight.baselineMonths,
            total = insight.total.amount,
            baselineAverage = insight.baselineAverage?.amount,
            changeRateFromBaseline = insight.changeRateFromBaseline?.percentage,
            topIncreasedCategory = insight.topIncreasedCategory?.let {
                CategoryIncreaseResponse(it.categoryId.value, it.increase.amount)
            },
            commitmentRatio = insight.commitmentRatio?.percentage,
        )
    }
}

data class CommitmentForecastResponse(
    val month: YearMonth,
    val fixed: Long,
    val installment: Long,
    val total: Long,
    val installmentMerchants: List<String>,
) {
    companion object {
        fun from(commitment: MonthlyCommitment): CommitmentForecastResponse = CommitmentForecastResponse(
            month = commitment.month,
            fixed = commitment.fixed.amount,
            installment = commitment.installment.amount,
            total = commitment.total.amount,
            installmentMerchants = commitment.installmentMerchants,
        )
    }
}
