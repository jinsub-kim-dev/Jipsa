package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.checkInvariant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 분석에 쓰는 연월 구간. 시작월과 종료월을 포함한다.
 *
 * 너무 긴 구간은 한 화면에서 읽을 수 없고 집계 비용만 커지므로 [MAX_MONTHS] 개월로 제한한다.
 */
data class MonthRange(val from: YearMonth, val to: YearMonth) {

    init {
        checkInvariant(!to.isBefore(from)) { "종료월은 시작월보다 앞설 수 없습니다. 시작월: $from, 종료월: $to" }
        checkInvariant(size <= MAX_MONTHS) { "분석 구간은 최대 ${MAX_MONTHS}개월입니다. 요청: ${size}개월 ($from ~ $to)" }
    }

    val size: Int get() = (to.year - from.year) * 12 + (to.monthValue - from.monthValue) + 1

    val months: List<YearMonth> get() = (0 until size).map { from.plusMonths(it.toLong()) }

    val firstDay: LocalDate get() = from.atDay(1)

    val lastDay: LocalDate get() = to.atEndOfMonth()

    operator fun contains(month: YearMonth): Boolean = !month.isBefore(from) && !month.isAfter(to)

    companion object {
        const val MAX_MONTHS: Int = 36

        fun single(month: YearMonth): MonthRange = MonthRange(month, month)

        /** [month] 와 그 앞 [count] - 1 개월. */
        fun endingAt(month: YearMonth, count: Int): MonthRange = MonthRange(month.minusMonths((count - 1).toLong()), month)
    }
}
