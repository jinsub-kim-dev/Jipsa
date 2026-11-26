package com.example.jipsa.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("Ratio / ChangeRate - 비율과 증감률")
class RateTest {

    @Nested
    @DisplayName("Ratio - 점유율")
    inner class RatioTest {

        @ParameterizedTest(name = "{0} / {1} = {2}%")
        @CsvSource(
            "1, 3, 33.3",
            "2, 3, 66.7",
            "1, 8, 12.5",
            "0, 100, 0.0",
            "100, 100, 100.0",
            "1001, 2000, 50.1",
            "1000999, 2000000, 50.0",
        )
        fun `소수 첫째 자리까지 반올림한다`(part: Long, whole: Long, expected: Double) {
            assertThat(Ratio.of(Money.of(part), Money.of(whole))!!.percentage).isEqualTo(expected)
        }

        @Test
        fun `전체가 0원이면 비율을 정의하지 않는다`() {
            assertThat(Ratio.of(Money.ZERO, Money.ZERO)).isNull()
        }

        @Test
        fun `문자열로 표기한다`() {
            assertThat(Ratio.of(Money.of(1), Money.of(3)).toString()).isEqualTo("33.3%")
        }
    }

    @Nested
    @DisplayName("ChangeRate - 증감률")
    inner class ChangeRateTest {

        @ParameterizedTest(name = "{1} -> {0} = {2}%")
        @CsvSource(
            "150, 100, 50.0",
            "70, 100, -30.0",
            "100, 100, 0.0",
            "7, 8, -12.5",
            "1, 3, -66.7",
            "4, 3, 33.3",
        )
        fun `이전 값 대비 증감률을 반올림해 계산한다`(current: Long, previous: Long, expected: Double) {
            assertThat(ChangeRate.between(Money.of(current), Money.of(previous))!!.percentage).isEqualTo(expected)
        }

        @Test
        fun `이전 값이 0원이면 증감률을 정의하지 않는다`() {
            // 0% 로 내려주면 "변동 없음" 으로 정반대로 읽힌다
            assertThat(ChangeRate.between(Money.of(10_000), Money.ZERO)).isNull()
        }

        @Test
        fun `증가 여부와 부호를 드러낸 표기를 제공한다`() {
            val increase = ChangeRate.between(Money.of(150), Money.of(100))!!
            val decrease = ChangeRate.between(Money.of(50), Money.of(100))!!

            assertThat(increase.isIncrease).isTrue()
            assertThat(decrease.isIncrease).isFalse()
            assertThat(increase.toString()).isEqualTo("+50.0%")
            assertThat(decrease.toString()).isEqualTo("-50.0%")
        }
    }
}
