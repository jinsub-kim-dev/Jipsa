package com.example.jipsa.application.service

import com.example.jipsa.application.port.StatisticsUseCase
import com.example.jipsa.domain.exception.checkInvariant
import com.example.jipsa.domain.model.KindAmounts
import com.example.jipsa.domain.model.MonthRange
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.SpendingComparison
import com.example.jipsa.domain.query.StatisticsQuery
import com.example.jipsa.domain.query.TagTotal
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.service.CategoryTrend
import com.example.jipsa.domain.service.CategoryTrendAnalyzer
import com.example.jipsa.domain.service.CommitmentForecaster
import com.example.jipsa.domain.service.MonthlyCommitment
import com.example.jipsa.domain.service.SpendingInsight
import com.example.jipsa.domain.service.SpendingInsightAnalyzer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

/**
 * 분석 유스케이스 오케스트레이션.
 *
 * 집계는 조회 포트가 DB 에서 하고, 판정·정렬·증감률은 도메인 서비스가 한다.
 * 이 서비스는 필요한 구간을 정해 둘을 잇기만 한다.
 */
@Service
@Transactional(readOnly = true)
class StatisticsService(
    private val statisticsQuery: StatisticsQuery,
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val installmentPlanRepository: InstallmentPlanRepository,
    private val categoryTrendAnalyzer: CategoryTrendAnalyzer,
    private val spendingInsightAnalyzer: SpendingInsightAnalyzer,
    private val commitmentForecaster: CommitmentForecaster,
) : StatisticsUseCase {

    override fun monthlySummary(month: YearMonth): SpendingComparison {
        val amounts = amountsByMonth(MonthRange(month.minusMonths(1), month))
        return SpendingComparison(
            month = month,
            current = amounts[month] ?: KindAmounts.ZERO,
            previous = amounts[month.minusMonths(1)] ?: KindAmounts.ZERO,
        )
    }

    override fun kindTrend(from: YearMonth, to: YearMonth): List<MonthlyKindAmounts> {
        val range = MonthRange(from, to)
        return statisticsQuery.monthlyKindAmounts(range.from, range.to)
    }

    override fun categoryTrend(from: YearMonth, to: YearMonth, targetMonth: YearMonth): List<CategoryTrend> {
        val range = MonthRange(from, to)
        checkInvariant(targetMonth in range) { "기준월 $targetMonth 이(가) 구간($from ~ $to) 밖입니다." }
        return categoryTrendAnalyzer.analyze(
            months = range.months,
            targetMonth = targetMonth,
            expenses = statisticsQuery.monthlyCategoryExpenses(range.from, range.to),
        )
    }

    override fun tagRanking(month: YearMonth, limit: Int): List<TagTotal> {
        checkInvariant(limit in 1..MAX_TAG_LIMIT) { "태그 순위는 1~${MAX_TAG_LIMIT}개까지 조회할 수 있습니다. 요청: $limit" }
        return statisticsQuery.tagTotals(month.atDay(1), month.atEndOfMonth(), limit)
    }

    override fun insight(month: YearMonth): SpendingInsight {
        val range = MonthRange.endingAt(month, SpendingInsightAnalyzer.BASELINE_MONTHS + 1)
        return spendingInsightAnalyzer.analyze(
            targetMonth = month,
            monthlyAmounts = statisticsQuery.monthlyKindAmounts(range.from, range.to),
            categoryExpenses = statisticsQuery.monthlyCategoryExpenses(month.minusMonths(1), month),
        )
    }

    override fun commitmentForecast(baseMonth: YearMonth, months: Int): List<MonthlyCommitment> {
        checkInvariant(months in 1..MonthRange.MAX_MONTHS) {
            "전망은 1~${MonthRange.MAX_MONTHS}개월까지 조회할 수 있습니다. 요청: $months"
        }
        return commitmentForecaster.forecast(
            baseMonth = baseMonth,
            months = months,
            fixedExpenses = fixedExpenseRepository.findAll(),
            installmentPlans = installmentPlanRepository.findAll(),
        )
    }

    private fun amountsByMonth(range: MonthRange): Map<YearMonth, KindAmounts> =
        statisticsQuery.monthlyKindAmounts(range.from, range.to).associate { it.month to it.amounts }

    companion object {
        const val MAX_TAG_LIMIT: Int = 50
    }
}
