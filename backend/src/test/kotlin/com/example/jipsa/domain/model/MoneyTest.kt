package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Money - 금액 값 객체")
class MoneyTest {

    @Test
    fun `0원은 허용한다`() {
        assertThat(Money.of(0).isZero).isTrue()
    }

    @Test
    fun `음수 금액은 거부한다`() {
        assertThatThrownBy { Money.of(-1) }.isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `더하고 뺀다`() {
        assertThat(Money.of(1_000) + Money.of(500)).isEqualTo(Money.of(1_500))
        assertThat(Money.of(1_000) - Money.of(500)).isEqualTo(Money.of(500))
    }

    @Test
    fun `빼서 음수가 되면 거부한다`() {
        assertThatThrownBy { Money.of(100) - Money.of(101) }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `곱한다`() {
        assertThat(Money.of(1_000) * 3).isEqualTo(Money.of(3_000))
    }

    @Test
    fun `음수를 곱하면 거부한다`() {
        assertThatThrownBy { Money.of(1_000) * -1 }.isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `나누어떨어지지 않는 금액은 몫과 나머지를 함께 반환한다`() {
        // given
        val (quotient, remainder) = Money.of(1_000_000).divideWithRemainder(3)

        // then
        assertThat(quotient).isEqualTo(Money.of(333_333))
        assertThat(remainder).isEqualTo(Money.of(1))
    }

    @Test
    fun `0 이하로 나누면 거부한다`() {
        assertThatThrownBy { Money.of(1_000).divideWithRemainder(0) }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `합계를 구하고 대소를 비교한다`() {
        assertThat(Money.sum(listOf(Money.of(1), Money.of(2)))).isEqualTo(Money.of(3))
        assertThat(Money.of(2)).isGreaterThan(Money.of(1))
        assertThat(Money.of(1).isPositive).isTrue()
        assertThat(Money.of(1_234).toString()).isEqualTo("1,234원")
    }
}
