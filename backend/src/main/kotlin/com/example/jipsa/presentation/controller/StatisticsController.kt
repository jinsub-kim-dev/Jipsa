package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.StatisticsUseCase
import com.example.jipsa.domain.model.MonthRange
import com.example.jipsa.presentation.dto.CategoryTrendResponse
import com.example.jipsa.presentation.dto.CommitmentForecastResponse
import com.example.jipsa.presentation.dto.InsightResponse
import com.example.jipsa.presentation.dto.KindTrendResponse
import com.example.jipsa.presentation.dto.MonthlySummaryResponse
import com.example.jipsa.presentation.dto.TagTotalResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

/** 분석 API (PRD 4절). 모든 분석은 소비일 기준이다. */
@RestController
@RequestMapping("/api/statistics")
class StatisticsController(
    private val statisticsUseCase: StatisticsUseCase,
) {

    @GetMapping("/monthly-summary")
    fun monthlySummary(@RequestParam month: YearMonth): MonthlySummaryResponse =
        MonthlySummaryResponse.from(statisticsUseCase.monthlySummary(month))

    @GetMapping("/kind-trend")
    fun kindTrend(@RequestParam from: YearMonth, @RequestParam to: YearMonth): List<KindTrendResponse> =
        statisticsUseCase.kindTrend(from, to).map(KindTrendResponse::from)

    /** @param targetMonth 강조·정렬 기준월. 생략하면 [to] 다. */
    @GetMapping("/category-trend")
    fun categoryTrend(
        @RequestParam from: YearMonth,
        @RequestParam to: YearMonth,
        @RequestParam(required = false) targetMonth: YearMonth?,
    ): CategoryTrendResponse {
        val target = targetMonth ?: to
        return CategoryTrendResponse.of(
            months = MonthRange(from, to).months,
            targetMonth = target,
            trends = statisticsUseCase.categoryTrend(from, to, target),
        )
    }

    @GetMapping("/tags")
    fun tagRanking(
        @RequestParam month: YearMonth,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<TagTotalResponse> = statisticsUseCase.tagRanking(month, limit).map(TagTotalResponse::from)

    @GetMapping("/insight")
    fun insight(@RequestParam month: YearMonth): InsightResponse = InsightResponse.from(statisticsUseCase.insight(month))

    /** [month] 다음 달부터 [months] 개월의 약정 지출(고정비 + 할부). */
    @GetMapping("/commitment-forecast")
    fun commitmentForecast(
        @RequestParam month: YearMonth,
        @RequestParam(defaultValue = "6") months: Int,
    ): List<CommitmentForecastResponse> =
        statisticsUseCase.commitmentForecast(month, months).map(CommitmentForecastResponse::from)
}
