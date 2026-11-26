package com.example.jipsa.domain.service

import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.generalTransaction
import com.example.jipsa.fixture.installmentPlan
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("FixedExpenseConverter - 고정비로 전환 (PRD 3.2.1)")
class FixedExpenseConverterTest {

    private val converter = FixedExpenseConverter()

    private fun paid(id: Long, date: LocalDate, amount: Long = 15_000, merchant: String = "OO보험") =
        generalTransaction(id = id, merchant = merchant, amount = amount, spentDate = date)

    private val source = paid(10, LocalDate.of(2026, 10, 27))

    @Test
    fun `같은 사용처의 다른 달 일반 지출을 함께 바꾸고 시작월은 가장 이른 달이다`() {
        // given
        val september = paid(1, LocalDate.of(2026, 9, 26))
        val november = paid(2, LocalDate.of(2026, 11, 25), merchant = "ＯＯ 보험")

        // when
        val plan = converter.plan(source, listOf(november, source, september), emptyList())

        // then
        assertThat(plan.transactions.map { it.id }).containsExactly(TransactionId(1), TransactionId(10), TransactionId(2))
        assertThat(plan.fixedExpense.startMonth).isEqualTo(YearMonth.of(2026, 9))
        assertThat(plan.fixedExpense.dayOfMonth).isEqualTo(27)
        assertThat(plan.ambiguousMonths).isEmpty()
    }

    @Test
    fun `다른 사용처와 고른 거래 달의 다른 거래는 바꾸지 않는다`() {
        // given
        val sameMonth = paid(1, LocalDate.of(2026, 10, 3))
        val otherMerchant = paid(2, LocalDate.of(2026, 9, 26), merchant = "OO화재")

        // when
        val plan = converter.plan(source, listOf(sameMonth, otherMerchant, source), emptyList())

        // then
        assertThat(plan.transactions).containsExactly(source)
        assertThat(plan.fixedExpense.startMonth).isEqualTo(YearMonth.of(2026, 10))
    }

    @Test
    fun `한 달에 여럿이면 금액이 같은 것 하나를 고른다`() {
        // given
        val same = paid(1, LocalDate.of(2026, 9, 26))
        val other = paid(2, LocalDate.of(2026, 9, 12), amount = 9_000)

        // when
        val plan = converter.plan(source, listOf(same, other), emptyList())

        // then
        assertThat(plan.transactions.map { it.id }).containsExactly(TransactionId(1), TransactionId(10))
    }

    @Test
    fun `한 달에 여럿이고 금액으로도 정할 수 없으면 그 달은 바꾸지 않고 알려준다`() {
        // given
        val august = listOf(paid(1, LocalDate.of(2026, 8, 3), amount = 9_000), paid(2, LocalDate.of(2026, 8, 20), amount = 7_000))
        val september = listOf(paid(3, LocalDate.of(2026, 9, 3)), paid(4, LocalDate.of(2026, 9, 20)))

        // when
        val plan = converter.plan(source, august + september, emptyList())

        // then
        assertThat(plan.transactions).containsExactly(source)
        assertThat(plan.ambiguousMonths).containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9))
    }

    @Test
    fun `일반 지출이 아닌 거래는 함께 바꾸지 않는다`() {
        val part = installmentPlan(merchant = "OO보험").scheduleParts().first()

        val plan = converter.plan(source, listOf(part, source), emptyList())

        assertThat(plan.transactions).containsExactly(source)
    }

    @Test
    fun `같은 사용처의 고정비가 이미 있으면 전환하지 않는다`() {
        assertThatThrownBy { converter.plan(source, listOf(source), listOf(fixedExpense(merchant = "oo보험"))) }
            .isInstanceOf(DomainStateException::class.java)
            .hasMessageContaining("이미")
    }

    @Test
    fun `다른 사용처의 고정비는 상관없다`() {
        val plan = converter.plan(source, listOf(source), listOf(fixedExpense(merchant = "월세")))

        assertThat(plan.transactions).containsExactly(source)
    }

    @Test
    fun `일반 지출이 아닌 거래는 고를 수 없다`() {
        val occurrence = fixedExpense(merchant = "OO보험").occurrenceIn(YearMonth.of(2026, 11))

        assertThatThrownBy { converter.plan(occurrence, emptyList(), emptyList()) }
            .isInstanceOf(DomainStateException::class.java)
    }

    @Test
    fun `저장된 고정비의 거래로 바꾼다`() {
        val plan = converter.plan(source, listOf(source, paid(1, LocalDate.of(2026, 9, 26))), emptyList())

        val converted = plan.convertedTo(FixedExpenseId(7))

        assertThat(converted.map { it.kind }).containsOnly(ExpenseKind.FIXED)
        assertThat(converted.map { it.fixedExpenseId }).containsOnly(FixedExpenseId(7))
    }
}
