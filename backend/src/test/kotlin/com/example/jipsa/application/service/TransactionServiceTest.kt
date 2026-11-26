package com.example.jipsa.application.service

import com.example.jipsa.application.port.RegisterGeneralTransactionCommand
import com.example.jipsa.application.port.UpdateTransactionCommand
import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.fixture.category
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.generalTransaction
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("TransactionService")
class TransactionServiceTest {

    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val categoryRepository = mockk<CategoryRepository>()
    private val service = TransactionService(transactionRepository, categoryRepository)

    private val savedSlot = slot<Transaction>()

    private fun givenSaveReturnsInput() {
        every { transactionRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
    }

    @Nested
    @DisplayName("일반 지출 등록")
    inner class Register {

        private val command = RegisterGeneralTransactionCommand(
            categoryId = CategoryId(1), amount = won(9_000), merchant = "김밥천국",
            spentDate = LocalDate.of(2026, 11, 26), tags = Tags.of("점심"), memo = null, paymentMethod = "신한카드",
        )

        @Test
        fun `일반 지출로 저장한다`() {
            // given
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            givenSaveReturnsInput()

            // when
            val saved = service.registerGeneral(command)

            // then
            assertThat(saved.kind).isEqualTo(ExpenseKind.GENERAL)
            assertThat(saved.tags.names).containsExactly("점심")
        }

        @Test
        fun `없는 카테고리면 404 로 거부한다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns null

            assertThatThrownBy { service.registerGeneral(command) }.isInstanceOf(ResourceNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("수정")
    inner class Update {

        @Test
        fun `바뀐 필드만 반영한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1)) } returns generalTransaction(memo = "원래 메모")
            givenSaveReturnsInput()

            // when
            val updated = service.update(UpdateTransactionCommand(TransactionId(1), amount = won(12_000), tags = Tags.of("저녁")))

            // then
            assertThat(updated.amount).isEqualTo(won(12_000))
            assertThat(updated.tags.names).containsExactly("저녁")
            assertThat(updated.memo).isEqualTo("원래 메모")
            assertThat(updated.merchant).isEqualTo("김밥천국")
        }

        @Test
        fun `모든 필드를 바꾼다`() {
            // given
            every { transactionRepository.findById(TransactionId(1)) } returns generalTransaction()
            every { categoryRepository.findById(CategoryId(2)) } returns category(id = 2)
            givenSaveReturnsInput()

            // when
            val updated = service.update(
                UpdateTransactionCommand(
                    id = TransactionId(1), categoryId = CategoryId(2), amount = won(1), merchant = "본죽",
                    spentDate = LocalDate.of(2026, 11, 1), tags = Tags.EMPTY, memo = "메모", paymentMethod = "현금",
                ),
            )

            // then
            assertThat(updated.categoryId).isEqualTo(CategoryId(2))
            assertThat(updated.merchant).isEqualTo("본죽")
            assertThat(updated.spentDate).isEqualTo(LocalDate.of(2026, 11, 1))
            assertThat(updated.memo).isEqualTo("메모")
            assertThat(updated.paymentMethod).isEqualTo("현금")
        }

        @Test
        fun `메모 지우기를 요청하면 메모를 지운다`() {
            every { transactionRepository.findById(TransactionId(1)) } returns generalTransaction(memo = "메모")
            givenSaveReturnsInput()

            assertThat(service.update(UpdateTransactionCommand(TransactionId(1), clearMemo = true)).memo).isNull()
        }

        @Test
        fun `바꾸려는 카테고리가 없으면 404 로 거부한다`() {
            every { categoryRepository.findById(CategoryId(9)) } returns null

            assertThatThrownBy { service.update(UpdateTransactionCommand(TransactionId(1), categoryId = CategoryId(9))) }
                .isInstanceOf(ResourceNotFoundException::class.java)
        }

        @Test
        fun `할부 회차의 금액 수정은 도메인이 거부한다`() {
            every { transactionRepository.findById(TransactionId(1)) } returns
                installmentPlan().scheduleParts().first().assignId(TransactionId(1))

            assertThatThrownBy { service.update(UpdateTransactionCommand(TransactionId(1), amount = won(1))) }
                .isInstanceOf(DomainStateException::class.java)
        }

        @Test
        fun `없는 거래면 404 로 거부한다`() {
            every { transactionRepository.findById(TransactionId(1)) } returns null

            assertThatThrownBy { service.getById(TransactionId(1)) }.isInstanceOf(ResourceNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("삭제")
    inner class Delete {

        @Test
        fun `일반 지출을 삭제한다`() {
            every { transactionRepository.findById(TransactionId(1)) } returns generalTransaction()

            service.delete(TransactionId(1))

            verify { transactionRepository.deleteById(TransactionId(1)) }
        }

        @Test
        fun `할부 회차는 해지하라고 안내하며 거부한다`() {
            every { transactionRepository.findById(TransactionId(1)) } returns
                installmentPlan().scheduleParts().first().assignId(TransactionId(1))

            assertThatThrownBy { service.delete(TransactionId(1)) }
                .isInstanceOf(DomainStateException::class.java)
                .hasMessageContaining("해지")
        }

        @Test
        fun `고정비 거래는 종료하라고 안내하며 거부한다`() {
            every { transactionRepository.findById(TransactionId(1)) } returns
                fixedExpense().occurrenceIn(YearMonth.of(2026, 11)).assignId(TransactionId(1))

            assertThatThrownBy { service.delete(TransactionId(1)) }
                .isInstanceOf(DomainStateException::class.java)
                .hasMessageContaining("종료")
        }
    }
}
