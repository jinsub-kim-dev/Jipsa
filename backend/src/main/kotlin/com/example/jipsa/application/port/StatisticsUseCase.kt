package com.example.jipsa.application.port

import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.SpendingComparison
import com.example.jipsa.domain.query.TagTotal
import com.example.jipsa.domain.service.CategoryTrend
import com.example.jipsa.domain.service.MonthlyCommitment
import com.example.jipsa.domain.service.SpendingInsight
import java.time.YearMonth

/** 분석 인바운드 포트 (PRD 4절). 모든 분석은 소비일 기준이다. */
interface StatisticsUseCase {

    fun monthlySummary(month: YearMonth): SpendingComparison

    fun kindTrend(from: YearMonth, to: YearMonth): List<MonthlyKindAmounts>

    fun categoryTrend(from: YearMonth, to: YearMonth, targetMonth: YearMonth): List<CategoryTrend>

    fun tagRanking(month: YearMonth, limit: Int): List<TagTotal>

    fun insight(month: YearMonth): SpendingInsight

    fun commitmentForecast(baseMonth: YearMonth, months: Int): List<MonthlyCommitment>
}
