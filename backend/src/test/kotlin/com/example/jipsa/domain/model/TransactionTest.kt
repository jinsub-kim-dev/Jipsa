package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.generalTransaction
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("Transaction - 거래")
class TransactionTest {

    private val date = LocalDate.of(2026, 11, 10)

    private fun reconstitute(
        kind: ExpenseKind,
        fixedExpenseId: FixedExpenseId? = null,
        installmentPlanId: InstallmentPlanId? = null,
        installmentSequence: Int? = null,
    ) = Transaction.reconstitute(
        id = TransactionId(1),
        kind = kind,
        categoryId = CategoryId(1),
        amount = won(1_000),
        merchant = "사용처",
        spentDate = date,
        tags = Tags.EMPTY,
        memo = null,
        paymentMethod = null,
        fixedExpenseId = fixedExpenseId,
        installmentPlanId = installmentPlanId,
        installmentSequence = installmentSequence,
    )

    @Nested
    @DisplayName("일반 지출 등록")
    inner class CreateGeneral {

        @Test
        fun `일반 지출은 출처 없이 만들어진다`() {
            val transaction = generalTransaction(id = null)

            assertThat(transaction.kind).isEqualTo(ExpenseKind.GENERAL)
            assertThat(transaction.fixedExpenseId).isNull()
            assertThat(transaction.installmentPlanId).isNull()
            assertThat(transaction.isInstallmentPart).isFalse()
            assertThat(transaction.isFixedOccurrence).isFalse()
        }

        @Test
        fun `일반 지출만 하나씩 삭제할 수 있다`() {
            assertThat(generalTransaction().isDeletable).isTrue()
            assertThat(fixedExpense().occurrenceIn(YearMonth.of(2026, 11)).isDeletable).isFalse()
            assertThat(installmentPlan().scheduleParts().first().isDeletable).isFalse()
        }

        @Test
        fun `0원 거래는 거부한다`() {
            assertThatThrownBy { generalTransaction(amount = 0) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `사용처는 필수이고 100자까지다`() {
            assertThatThrownBy { generalTransaction(merchant = "  ") }
                .isInstanceOf(InvariantViolationException::class.java)
            assertThat(generalTransaction(merchant = "가".repeat(100)).merchant).hasSize(100)
            assertThatThrownBy { generalTransaction(merchant = "가".repeat(101)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `메모와 결제 수단의 빈 문자열은 값 없음으로 저장한다`() {
            val transaction = generalTransaction(memo = "  ", paymentMethod = "")

            assertThat(transaction.memo).isNull()
            assertThat(transaction.paymentMethod).isNull()
        }

        @Test
        fun `메모는 200자, 결제 수단은 30자까지다`() {
            assertThat(generalTransaction(memo = "가".repeat(200)).memo).hasSize(200)
            assertThatThrownBy { generalTransaction(memo = "가".repeat(201)) }
                .isInstanceOf(InvariantViolationException::class.java)
            assertThatThrownBy { generalTransaction(paymentMethod = "가".repeat(31)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("지출 종류와 출처의 일치")
    inner class Source {

        @Test
        fun `일반 지출에 고정비 출처가 있으면 거부한다`() {
            assertThatThrownBy { reconstitute(ExpenseKind.GENERAL, fixedExpenseId = FixedExpenseId(1)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `일반 지출에 할부 출처가 있으면 거부한다`() {
            assertThatThrownBy { reconstitute(ExpenseKind.GENERAL, installmentSequence = 1) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `고정비 거래에 고정비 출처가 없으면 거부한다`() {
            assertThatThrownBy { reconstitute(ExpenseKind.FIXED) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `고정비 거래에 할부 출처가 함께 있으면 거부한다`() {
            assertThatThrownBy {
                reconstitute(ExpenseKind.FIXED, FixedExpenseId(1), installmentPlanId = InstallmentPlanId(1))
            }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `할부 거래에 회차가 없으면 거부한다`() {
            assertThatThrownBy { reconstitute(ExpenseKind.INSTALLMENT, installmentPlanId = InstallmentPlanId(1)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `할부 거래에 계획이 없으면 거부한다`() {
            assertThatThrownBy { reconstitute(ExpenseKind.INSTALLMENT, installmentSequence = 1) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `할부 거래에 고정비 출처가 있으면 거부한다`() {
            assertThatThrownBy {
                reconstitute(ExpenseKind.INSTALLMENT, FixedExpenseId(1), InstallmentPlanId(1), 1)
            }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `할부 회차는 1 이상이다`() {
            assertThatThrownBy { reconstitute(ExpenseKind.INSTALLMENT, null, InstallmentPlanId(1), 0) }
                .isInstanceOf(InvariantViolationException::class.java)
            assertThat(reconstitute(ExpenseKind.INSTALLMENT, null, InstallmentPlanId(1), 1).isInstallmentPart).isTrue()
        }
    }

    @Nested
    @DisplayName("수정")
    inner class Revise {

        @Test
        fun `일반 지출은 모든 값을 고칠 수 있다`() {
            // when
            val revised = generalTransaction()
                .correctAmount(won(20_000))
                .reschedule(date.plusDays(1))
                .renameMerchant(" 본죽 ")
                .recategorize(CategoryId(2))
                .retag(Tags.of("저녁"))
                .rewriteMemo(" 메모 ")
                .changePaymentMethod("현금")

            // then
            assertThat(revised.amount).isEqualTo(won(20_000))
            assertThat(revised.spentDate).isEqualTo(date.plusDays(1))
            assertThat(revised.merchant).isEqualTo("본죽")
            assertThat(revised.categoryId).isEqualTo(CategoryId(2))
            assertThat(revised.tags.names).containsExactly("저녁")
            assertThat(revised.memo).isEqualTo("메모")
            assertThat(revised.paymentMethod).isEqualTo("현금")
            assertThat(revised.id).isEqualTo(TransactionId(1))
        }

        @Test
        fun `메모를 지울 수 있다`() {
            assertThat(generalTransaction(memo = "메모").rewriteMemo(null).memo).isNull()
        }

        @Test
        fun `고정비 거래는 금액을 고칠 수 있다`() {
            // 관리비처럼 달마다 금액이 조금씩 다른 고정비가 있다
            val occurrence = fixedExpense(amount = 130_000).occurrenceIn(YearMonth.of(2026, 11))

            assertThat(occurrence.correctAmount(won(142_000)).amount).isEqualTo(won(142_000))
        }

        @Test
        fun `할부 회차의 금액은 고칠 수 없다`() {
            val part = installmentPlan().scheduleParts().first()

            assertThatThrownBy { part.correctAmount(won(1)) }
                .isInstanceOf(DomainStateException::class.java)
                .hasMessageContaining("금액")
        }

        @Test
        fun `할부 회차의 소비일은 고칠 수 없다`() {
            val part = installmentPlan().scheduleParts().first()

            assertThatThrownBy { part.reschedule(date) }
                .isInstanceOf(DomainStateException::class.java)
                .hasMessageContaining("소비일")
        }

        @Test
        fun `할부 회차의 카테고리와 태그와 메모는 고칠 수 있다`() {
            val part = installmentPlan().scheduleParts().first()
                .recategorize(CategoryId(3))
                .retag(Tags.of("업무"))
                .rewriteMemo("회사 지원 예정")

            assertThat(part.categoryId).isEqualTo(CategoryId(3))
            assertThat(part.tags.names).containsExactly("업무")
            assertThat(part.memo).isEqualTo("회사 지원 예정")
        }
    }

    @Nested
    @DisplayName("고정비로 전환 (PRD 3.2.1)")
    inner class ConvertToFixed {

        @Test
        fun `일반 지출은 값을 그대로 두고 고정비 거래가 된다`() {
            // given
            val general = generalTransaction(amount = 15_000, merchant = "OO보험", memo = "자동이체", id = 4)

            // when
            val converted = general.convertToFixedOccurrence(FixedExpenseId(9))

            // then
            assertThat(converted.kind).isEqualTo(ExpenseKind.FIXED)
            assertThat(converted.fixedExpenseId).isEqualTo(FixedExpenseId(9))
            assertThat(converted.id).isEqualTo(TransactionId(4))
            assertThat(listOf(converted.amount, converted.spentDate, converted.merchant, converted.memo, converted.paymentMethod))
                .containsExactly(general.amount, general.spentDate, general.merchant, general.memo, general.paymentMethod)
            assertThat(converted.tags).isEqualTo(general.tags)
            assertThat(converted.isDeletable).isFalse()
        }

        @Test
        fun `할부 회차는 고정비로 전환할 수 없다`() {
            val part = installmentPlan().scheduleParts().first()

            assertThatThrownBy { part.convertToFixedOccurrence(FixedExpenseId(9)) }
                .isInstanceOf(DomainStateException::class.java)
                .hasMessageContaining("일반 지출만")
        }

        @Test
        fun `이미 고정비 거래면 다시 전환할 수 없다`() {
            val occurrence = fixedExpense().occurrenceIn(YearMonth.of(2026, 11))

            assertThatThrownBy { occurrence.convertToFixedOccurrence(FixedExpenseId(9)) }
                .isInstanceOf(DomainStateException::class.java)
        }
    }

    @Test
    fun `동일성은 식별자로 판단한다`() {
        assertThat(generalTransaction(amount = 1_000, id = 1)).isEqualTo(generalTransaction(amount = 2_000, id = 1))
        assertThat(generalTransaction(id = 1)).isNotEqualTo(generalTransaction(id = 2))
        assertThat(generalTransaction(id = null)).isNotEqualTo(generalTransaction(id = null))
        assertThat(generalTransaction()).isNotEqualTo("거래")
        assertThat(generalTransaction(id = null).hashCode()).isZero()
        assertThat(generalTransaction(id = 1).hashCode()).isEqualTo(TransactionId(1).hashCode())
        val same = generalTransaction()
        assertThat(same).isEqualTo(same)
        assertThat(same.toString()).contains("김밥천국")
    }
}
