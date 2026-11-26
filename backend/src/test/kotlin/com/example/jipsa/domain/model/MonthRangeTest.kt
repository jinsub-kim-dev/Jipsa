package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("MonthRange - 분석 구간")
class MonthRangeTest {

    @Test
    fun `해를 넘는 구간의 달을 순서대로 나열한다`() {
        val range = MonthRange(YearMonth.of(2026, 11), YearMonth.of(2027, 2))

        assertThat(range.size).isEqualTo(4)
        assertThat(range.months).containsExactly(
            YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2),
        )
        assertThat(range.firstDay).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(range.lastDay).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun `시작월과 종료월을 포함한다`() {
        val range = MonthRange(YearMonth.of(2026, 6), YearMonth.of(2026, 11))

        assertThat(YearMonth.of(2026, 6) in range).isTrue()
        assertThat(YearMonth.of(2026, 11) in range).isTrue()
        assertThat(YearMonth.of(2026, 5) in range).isFalse()
        assertThat(YearMonth.of(2026, 12) in range).isFalse()
    }

    @Test
    fun `종료월이 시작월보다 앞서면 거부한다`() {
        assertThatThrownBy { MonthRange(YearMonth.of(2026, 11), YearMonth.of(2026, 10)) }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `36개월까지 허용한다`() {
        assertThat(MonthRange.endingAt(YearMonth.of(2026, 12), 36).from).isEqualTo(YearMonth.of(2024, 1))
        assertThatThrownBy { MonthRange.endingAt(YearMonth.of(2026, 12), 37) }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `한 달짜리 구간을 만든다`() {
        assertThat(MonthRange.single(YearMonth.of(2026, 11)).months).containsExactly(YearMonth.of(2026, 11))
    }
}
