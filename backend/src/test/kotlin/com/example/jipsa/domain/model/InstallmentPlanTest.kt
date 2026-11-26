package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("InstallmentPlan - 할부 계획")
class InstallmentPlanTest {

    @Nested
    @DisplayName("불변식")
    inner class Invariants {

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 61])
        fun `개월 수가 2~60 밖이면 거부한다`(months: Int) {
            assertThatThrownBy { installmentPlan(installmentMonths = months) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @ParameterizedTest
        @ValueSource(ints = [2, 60])
        fun `개월 수 경계값 2와 60은 허용한다`(months: Int) {
            assertThat(installmentPlan(installmentMonths = months).installmentMonths).isEqualTo(months)
        }

        @Test
        fun `총액은 0원보다 커야 한다`() {
            assertThatThrownBy { installmentPlan(totalAmount = 0) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `월 납부액이 1원 미만이면 거부한다`() {
            assertThatThrownBy { installmentPlan(totalAmount = 2, installmentMonths = 3) }
                .isInstanceOf(InvariantViolationException::class.java)
            assertThat(installmentPlan(totalAmount = 3, installmentMonths = 3).monthlyAmount).isEqualTo(won(1))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 32])
        fun `납부일이 1~31 밖이면 거부한다`(day: Int) {
            assertThatThrownBy { installmentPlan(billingDay = day) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `사용처는 필수다`() {
            assertThatThrownBy { installmentPlan(merchant = "") }.isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("금액 분할")
    inner class Split {

        @Test
        fun `나머지는 1회차에 더한다`() {
            // given: 1,000,000원 / 3개월
            val plan = installmentPlan(totalAmount = 1_000_000, installmentMonths = 3)

            // then
            assertThat(plan.installmentAmounts).containsExactly(won(333_334), won(333_333), won(333_333))
            assertThat(plan.monthlyAmount).isEqualTo(won(333_333))
            assertThat(plan.firstInstallmentAmount).isEqualTo(won(333_334))
        }

        @ParameterizedTest(name = "{0}원 / {1}개월")
        @CsvSource("1000000, 3", "999999, 7", "3096000, 12", "100, 60", "558000, 3", "7, 2")
        fun `회차 금액의 합은 항상 총액과 같다`(total: Long, months: Int) {
            val plan = installmentPlan(totalAmount = total, installmentMonths = months)

            assertThat(Money.sum(plan.installmentAmounts)).isEqualTo(won(total))
            assertThat(Money.sum(plan.scheduleParts().map(Transaction::amount))).isEqualTo(won(total))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 13])
        fun `범위 밖 회차는 거부한다`(sequence: Int) {
            assertThatThrownBy { installmentPlan().amountOf(sequence) }
                .isInstanceOf(InvariantViolationException::class.java)
            assertThatThrownBy { installmentPlan().spentDateOf(sequence) }
                .isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("회차 일정")
    inner class Schedule {

        @Test
        fun `n회차는 첫 납부월부터 n-1개월 뒤의 납부일이다`() {
            val plan = installmentPlan(firstBillingMonth = YearMonth.of(2026, 11), billingDay = 15, installmentMonths = 3)

            assertThat(plan.scheduleParts().map(Transaction::spentDate)).containsExactly(
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2027, 1, 15),
            )
            assertThat(plan.lastBillingMonth).isEqualTo(YearMonth.of(2027, 1))
        }

        @Test
        fun `없는 납부일은 말일로 보정한다`() {
            // given: 1월 31일부터 3개월. 평년 2월에는 31일이 없다
            val plan = installmentPlan(firstBillingMonth = YearMonth.of(2027, 1), billingDay = 31, installmentMonths = 3)

            assertThat(plan.scheduleParts().map(Transaction::spentDate)).containsExactly(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
            )
        }

        @Test
        fun `회차 거래는 할부 출처와 회차 번호를 가진다`() {
            val parts = installmentPlan(installmentMonths = 3, totalAmount = 300).scheduleParts()

            assertThat(parts).allSatisfy {
                assertThat(it.kind).isEqualTo(ExpenseKind.INSTALLMENT)
                assertThat(it.installmentPlanId).isEqualTo(InstallmentPlanId(1))
                assertThat(it.merchant).isEqualTo("맥북 프로 14")
                assertThat(it.tags.names).containsExactly("전자기기")
                assertThat(it.paymentMethod).isEqualTo("현대카드")
            }
            assertThat(parts.map(Transaction::installmentSequence)).containsExactly(1, 2, 3)
        }

        @Test
        fun `영속화되지 않은 계획으로는 회차를 만들 수 없다`() {
            assertThatThrownBy { installmentPlan(id = null).scheduleParts() }
                .isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("특정 달의 회차와 진행 상황")
    inner class Progress {

        // 2026-03 부터 12개월, 월 258,000원
        private val plan = installmentPlan()

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource("2026-02, ", "2026-03, 1", "2026-11, 9", "2027-02, 12", "2027-03, ")
        fun `그 달의 회차 번호를 구한다`(month: String, expected: Int?) {
            assertThat(plan.sequenceIn(YearMonth.parse(month))).isEqualTo(expected)
        }

        @Test
        fun `기간 밖의 달에는 0원이 나간다`() {
            assertThat(plan.amountIn(YearMonth.of(2027, 3))).isEqualTo(Money.ZERO)
            assertThat(plan.amountIn(YearMonth.of(2026, 11))).isEqualTo(won(258_000))
        }

        @Test
        fun `진행 상황은 낸 금액과 남은 금액을 담는다`() {
            // when
            val progress = plan.progressAt(YearMonth.of(2026, 11))!!

            // then
            assertThat(progress.currentSequence).isEqualTo(9)
            assertThat(progress.paidAmount).isEqualTo(won(258_000 * 9))
            assertThat(progress.remainingAmount).isEqualTo(won(258_000 * 3))
            assertThat(progress.remainingSequences).isEqualTo(3)
            assertThat(progress.isLastSequence).isFalse()
        }

        @Test
        fun `남은 금액에는 1회차 나머지 가산이 반영된다`() {
            val progress = installmentPlan(totalAmount = 1_000_000, installmentMonths = 3).progressAt(YearMonth.of(2026, 3))!!

            assertThat(progress.amountThisMonth).isEqualTo(won(333_334))
            assertThat(progress.paidAmount).isEqualTo(won(333_334))
            assertThat(progress.remainingAmount).isEqualTo(won(666_666))
        }

        @Test
        fun `마지막 회차와 기간 밖을 구분한다`() {
            assertThat(plan.progressAt(YearMonth.of(2027, 2))!!.isLastSequence).isTrue()
            assertThat(plan.progressAt(YearMonth.of(2027, 2))!!.remainingAmount).isEqualTo(Money.ZERO)
            assertThat(plan.progressAt(YearMonth.of(2027, 3))).isNull()
        }
    }

    @Test
    fun `결제 수단의 빈 문자열은 값 없음이다`() {
        val plan = InstallmentPlan.create(CategoryId(1), "냉장고", won(1_000), 2, YearMonth.of(2026, 1), 1, paymentMethod = "")

        assertThat(plan.paymentMethod).isNull()
    }

    @Test
    fun `동일성은 식별자로 판단한다`() {
        assertThat(installmentPlan(totalAmount = 100, id = 1)).isEqualTo(installmentPlan(totalAmount = 200, id = 1))
        assertThat(installmentPlan(id = 1)).isNotEqualTo(installmentPlan(id = 2))
        assertThat(installmentPlan(id = null)).isNotEqualTo(installmentPlan(id = null))
        assertThat(installmentPlan()).isNotEqualTo("할부")
        assertThat(installmentPlan(id = null).hashCode()).isZero()
        assertThat(installmentPlan(id = 5).hashCode()).isEqualTo(InstallmentPlanId(5).hashCode())
        val same = installmentPlan()
        assertThat(same).isEqualTo(same)
        assertThat(same.toString()).contains("맥북")
    }
}
