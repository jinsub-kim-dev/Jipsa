package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.generalTransaction
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

@DisplayName("FixedExpense - 고정비")
class FixedExpenseTest {

    @Nested
    @DisplayName("불변식")
    inner class Invariants {

        @Test
        fun `금액은 0원보다 커야 한다`() {
            assertThatThrownBy { fixedExpense(amount = 0) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 32])
        fun `납부일이 1~31 밖이면 거부한다`(day: Int) {
            assertThatThrownBy { fixedExpense(dayOfMonth = day) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 31])
        fun `납부일 경계값 1과 31은 허용한다`(day: Int) {
            assertThat(fixedExpense(dayOfMonth = day).dayOfMonth).isEqualTo(day)
        }

        @Test
        fun `사용처는 필수다`() {
            assertThatThrownBy { fixedExpense(merchant = " ") }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `종료월은 시작월보다 앞설 수 없다`() {
            assertThatThrownBy {
                fixedExpense(startMonth = YearMonth.of(2026, 6), endMonth = YearMonth.of(2026, 5))
            }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `시작월에 바로 끝낼 수 있다`() {
            val expense = fixedExpense(startMonth = YearMonth.of(2026, 6), endMonth = YearMonth.of(2026, 6))

            assertThat(expense.isActiveIn(YearMonth.of(2026, 6))).isTrue()
            assertThat(expense.isActiveIn(YearMonth.of(2026, 7))).isFalse()
        }
    }

    @Nested
    @DisplayName("기간")
    inner class Period {

        private val expense = fixedExpense(startMonth = YearMonth.of(2026, 3), endMonth = YearMonth.of(2026, 8))

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource("2026-02, false", "2026-03, true", "2026-08, true", "2026-09, false")
        fun `시작월과 종료월을 포함한다`(month: String, expected: Boolean) {
            assertThat(expense.isActiveIn(YearMonth.parse(month))).isEqualTo(expected)
        }

        @Test
        fun `종료월이 없으면 계속 나간다`() {
            assertThat(fixedExpense(startMonth = YearMonth.of(2026, 1)).isActiveIn(YearMonth.of(2030, 1))).isTrue()
        }
    }

    @Nested
    @DisplayName("납부일 말일 보정")
    inner class DueDate {

        @ParameterizedTest(name = "{0}월 31일 납부 -> {1}")
        @CsvSource(
            "2026-01, 2026-01-31",
            "2026-02, 2026-02-28",
            "2028-02, 2028-02-29",
            "2026-04, 2026-04-30",
        )
        fun `그 달에 없는 날이면 말일로 보정한다`(month: String, expected: String) {
            assertThat(fixedExpense(dayOfMonth = 31).dueDateIn(YearMonth.parse(month)))
                .isEqualTo(LocalDate.parse(expected))
        }

        @Test
        fun `윤년 2월 29일 납부는 평년에 28일이 된다`() {
            assertThat(fixedExpense(dayOfMonth = 29).dueDateIn(YearMonth.of(2027, 2)))
                .isEqualTo(LocalDate.of(2027, 2, 28))
        }
    }

    @Nested
    @DisplayName("한 달 분 거래 기록")
    inner class Occurrence {

        @Test
        fun `고정비 출처와 정의의 값을 담은 거래를 만든다`() {
            // when
            val occurrence = fixedExpense(merchant = "넷플릭스", amount = 17_000, dayOfMonth = 5)
                .occurrenceIn(YearMonth.of(2026, 11))

            // then
            assertThat(occurrence.kind).isEqualTo(ExpenseKind.FIXED)
            assertThat(occurrence.fixedExpenseId).isEqualTo(FixedExpenseId(1))
            assertThat(occurrence.amount).isEqualTo(won(17_000))
            assertThat(occurrence.merchant).isEqualTo("넷플릭스")
            assertThat(occurrence.spentDate).isEqualTo(LocalDate.of(2026, 11, 5))
            assertThat(occurrence.tags.names).containsExactly("집")
            assertThat(occurrence.paymentMethod).isEqualTo("신한카드")
        }

        @Test
        fun `영속화되지 않은 고정비로는 기록할 수 없다`() {
            assertThatThrownBy { fixedExpense(id = null).occurrenceIn(YearMonth.of(2026, 11)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `기간 밖의 달에는 기록할 수 없다`() {
            assertThatThrownBy { fixedExpense(startMonth = YearMonth.of(2026, 12)).occurrenceIn(YearMonth.of(2026, 11)) }
                .isInstanceOf(InvariantViolationException::class.java)
            assertThatThrownBy {
                fixedExpense(startMonth = YearMonth.of(2026, 1), endMonth = YearMonth.of(2026, 3))
                    .occurrenceIn(YearMonth.of(2026, 11))
            }.isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("사용처 비교")
    inner class Merchant {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(
            "OO보험, true",
            "oo 보험, true",
            "ＯＯ　보험, true",
            "OO보험(주), false",
            "OO화재, false",
        )
        fun `공백·대소문자·전각을 무시하고 사용처가 같은지 본다`(merchant: String, expected: Boolean) {
            assertThat(fixedExpense(merchant = "OO보험").isFor(merchant)).isEqualTo(expected)
        }
    }

    @Nested
    @DisplayName("실제값으로 기록 (PRD 3.5)")
    inner class ActualOccurrence {

        @Test
        fun `실제로 나간 날과 금액으로 고정비 거래를 만든다`() {
            // when
            val occurrence = fixedExpense(amount = 15_000, dayOfMonth = 25).actualOccurrence(LocalDate.of(2026, 10, 27), won(15_920))

            // then
            assertThat(occurrence.kind).isEqualTo(ExpenseKind.FIXED)
            assertThat(occurrence.fixedExpenseId).isEqualTo(FixedExpenseId(1))
            assertThat(occurrence.spentDate).isEqualTo(LocalDate.of(2026, 10, 27))
            assertThat(occurrence.amount).isEqualTo(won(15_920))
        }

        @Test
        fun `기간 밖의 날에는 만들 수 없다`() {
            assertThatThrownBy {
                fixedExpense(startMonth = YearMonth.of(2026, 11)).actualOccurrence(LocalDate.of(2026, 10, 31), won(1))
            }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `자동 기록 그대로인 거래만 자동 기록으로 본다`() {
            // given
            val expense = fixedExpense(amount = 15_000, dayOfMonth = 31)
            val scheduled = expense.occurrenceIn(YearMonth.of(2026, 11))

            // then
            assertThat(scheduled.spentDate).isEqualTo(LocalDate.of(2026, 11, 30))
            assertThat(expense.isScheduledOccurrence(scheduled)).isTrue()
            assertThat(expense.isScheduledOccurrence(scheduled.correctAmount(won(15_920)))).isFalse()
            assertThat(expense.isScheduledOccurrence(scheduled.reschedule(LocalDate.of(2026, 11, 28)))).isFalse()
            assertThat(fixedExpense(amount = 15_000, dayOfMonth = 31, id = 2).isScheduledOccurrence(scheduled)).isFalse()
        }
    }

    @Nested
    @DisplayName("일반 지출로 정의 (PRD 3.2.1)")
    inner class DefineFrom {

        private val source = generalTransaction(
            amount = 15_000, merchant = "OO보험", spentDate = LocalDate.of(2026, 10, 27), categoryId = 6, tags = Tags.of("보험"),
        )

        @Test
        fun `거래의 값으로 정의하고 소비일의 일이 납부일이다`() {
            // when
            val expense = FixedExpense.defineFrom(source, startMonth = YearMonth.of(2026, 9))

            // then
            assertThat(expense.id).isNull()
            assertThat(expense.merchant).isEqualTo("OO보험")
            assertThat(expense.amount).isEqualTo(won(15_000))
            assertThat(expense.categoryId).isEqualTo(CategoryId(6))
            assertThat(expense.tags.names).containsExactly("보험")
            assertThat(expense.paymentMethod).isEqualTo("신한카드")
            assertThat(expense.dayOfMonth).isEqualTo(27)
            assertThat(expense.startMonth).isEqualTo(YearMonth.of(2026, 9))
            assertThat(expense.endMonth).isNull()
        }

        @Test
        fun `시작월은 거래의 달일 수 있지만 그보다 늦을 수 없다`() {
            assertThat(FixedExpense.defineFrom(source, YearMonth.of(2026, 10)).startMonth).isEqualTo(YearMonth.of(2026, 10))
            assertThatThrownBy { FixedExpense.defineFrom(source, YearMonth.of(2026, 11)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `일반 지출이 아니면 정의할 수 없다`() {
            val occurrence = fixedExpense().occurrenceIn(YearMonth.of(2026, 11))

            assertThatThrownBy { FixedExpense.defineFrom(occurrence, YearMonth.of(2026, 11)) }
                .isInstanceOf(DomainStateException::class.java)
        }
    }

    @Test
    fun `종료월을 다시 정할 수 있다`() {
        val terminated = fixedExpense(endMonth = YearMonth.of(2026, 6)).terminate(YearMonth.of(2026, 9))

        assertThat(terminated.endMonth).isEqualTo(YearMonth.of(2026, 9))
    }

    @Test
    fun `결제 수단의 빈 문자열은 값 없음이다`() {
        val expense = FixedExpense.create(CategoryId(1), "월세", won(1), 1, YearMonth.of(2026, 1), paymentMethod = " ")

        assertThat(expense.paymentMethod).isNull()
        assertThat(expense.tags).isEqualTo(Tags.EMPTY)
    }

    @Test
    fun `동일성은 식별자로 판단한다`() {
        assertThat(fixedExpense(amount = 1, id = 1)).isEqualTo(fixedExpense(amount = 2, id = 1))
        assertThat(fixedExpense(id = 1)).isNotEqualTo(fixedExpense(id = 2))
        assertThat(fixedExpense(id = null)).isNotEqualTo(fixedExpense(id = null))
        assertThat(fixedExpense()).isNotEqualTo("월세")
        assertThat(fixedExpense(id = null).hashCode()).isZero()
        assertThat(fixedExpense(id = 3).hashCode()).isEqualTo(FixedExpenseId(3).hashCode())
        val same = fixedExpense()
        assertThat(same).isEqualTo(same)
        assertThat(same.toString()).contains("월세")
        assertThat(fixedExpense(endMonth = YearMonth.of(2026, 5)).toString()).contains("2026-05")
    }
}
