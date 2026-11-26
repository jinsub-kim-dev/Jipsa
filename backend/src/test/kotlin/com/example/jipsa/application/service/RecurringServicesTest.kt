package com.example.jipsa.application.service

import com.example.jipsa.application.port.RegisterFixedExpenseCommand
import com.example.jipsa.application.port.RegisterInstallmentPlanCommand
import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.domain.service.FixedExpenseConverter
import com.example.jipsa.domain.service.FixedExpenseRecorder
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@DisplayName("고정비·할부 서비스")
class RecurringServicesTest {

    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val categoryRepository = mockk<CategoryRepository>()
    private val november = YearMonth.of(2026, 11)

    @Nested
    @DisplayName("FixedExpenseService")
    inner class FixedExpenses {

        private val fixedExpenseRepository = mockk<FixedExpenseRepository>()
        private val clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneId.of("Asia/Seoul"))
        private val service = FixedExpenseService(
            fixedExpenseRepository, transactionRepository, categoryRepository, FixedExpenseRecorder(), FixedExpenseConverter(), clock,
        )
        private val recorded = slot<List<Transaction>>()

        @Test
        fun `등록하면 이번 달 분을 바로 기록한다`() {
            // given
            val saved = fixedExpense(merchant = "넷플릭스", id = 5)
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { fixedExpenseRepository.save(any()) } returns saved
            every { fixedExpenseRepository.findAll() } returns listOf(saved)
            every { transactionRepository.findRecordedFixedExpenseIds(november) } returns emptySet()
            every { transactionRepository.saveAll(capture(recorded)) } answers { recorded.captured }

            // when
            service.register(
                RegisterFixedExpenseCommand(CategoryId(1), "넷플릭스", won(17_000), 5, YearMonth.of(2026, 1), Tags.EMPTY, null),
            )

            // then
            assertThat(recorded.captured.single().fixedExpenseId).isEqualTo(FixedExpenseId(5))
        }

        @Test
        fun `없는 카테고리면 404 로 거부한다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns null

            assertThatThrownBy {
                service.register(
                    RegisterFixedExpenseCommand(CategoryId(1), "월세", won(1), 1, november, Tags.EMPTY, null),
                )
            }.isInstanceOf(ResourceNotFoundException::class.java)
        }

        @Test
        fun `기록할 것이 없으면 저장하지 않고 0건을 반환한다`() {
            every { fixedExpenseRepository.findAll() } returns listOf(fixedExpense(id = 1))
            every { transactionRepository.findRecordedFixedExpenseIds(november) } returns setOf(FixedExpenseId(1))

            assertThat(service.recordFor(november)).isZero()
            verify(exactly = 0) { transactionRepository.saveAll(any()) }
        }

        @Test
        fun `종료하면 마지막 달 이후의 거래를 지운다`() {
            // given
            val saved = slot<FixedExpense>()
            every { fixedExpenseRepository.findById(FixedExpenseId(1)) } returns fixedExpense(id = 1)
            every { fixedExpenseRepository.save(capture(saved)) } answers { saved.captured }

            // when
            val terminated = service.terminate(FixedExpenseId(1), YearMonth.of(2026, 10))

            // then
            assertThat(terminated.endMonth).isEqualTo(YearMonth.of(2026, 10))
            verify { transactionRepository.deleteAllByFixedExpenseIdAndSpentDateAfter(FixedExpenseId(1), LocalDate.of(2026, 10, 31)) }
        }

        @Test
        fun `없는 고정비는 종료할 수 없다`() {
            every { fixedExpenseRepository.findById(FixedExpenseId(9)) } returns null

            assertThatThrownBy { service.terminate(FixedExpenseId(9), november) }
                .isInstanceOf(ResourceNotFoundException::class.java)
        }

        @Test
        fun `목록을 그대로 돌려준다`() {
            every { fixedExpenseRepository.findAll() } returns listOf(fixedExpense())

            assertThat(service.getAll()).hasSize(1)
        }

        @Test
        fun `고정비로 전환하면 고정비를 저장하고 같은 사용처의 거래를 바꾼 뒤 이번 달 분을 기록한다`() {
            // given
            val source = generalTransaction(merchant = "OO보험", amount = 15_000, spentDate = LocalDate.of(2026, 10, 25), id = 2)
            val earlier = generalTransaction(merchant = "OO보험", amount = 15_000, spentDate = LocalDate.of(2026, 9, 26), id = 1)
            every { transactionRepository.findById(TransactionId(2)) } returns source
            every { transactionRepository.findAllByKind(ExpenseKind.GENERAL) } returns listOf(earlier, source)
            val savedFixed = slot<FixedExpense>()
            every { fixedExpenseRepository.save(capture(savedFixed)) } answers { savedFixed.captured.assignId(FixedExpenseId(9)) }
            // 전환 전에는 고정비가 없고, 이번 달 기록 때는 방금 저장한 고정비가 있다
            every { fixedExpenseRepository.findAll() } answers {
                if (savedFixed.isCaptured) listOf(savedFixed.captured.assignId(FixedExpenseId(9))) else emptyList()
            }
            val savedTransactions = mutableListOf<Transaction>()
            every { transactionRepository.save(capture(savedTransactions)) } answers { firstArg() }
            every { transactionRepository.findRecordedFixedExpenseIds(november) } returns emptySet()
            every { transactionRepository.saveAll(capture(recorded)) } answers { recorded.captured }

            // when
            val result = service.convertFromTransaction(TransactionId(2))

            // then
            assertThat(savedFixed.captured.startMonth).isEqualTo(YearMonth.of(2026, 9))
            assertThat(result.convertedTransactions.map { it.id }).containsExactly(TransactionId(1), TransactionId(2))
            assertThat(savedTransactions.map { it.fixedExpenseId }).containsOnly(FixedExpenseId(9))
            assertThat(recorded.captured.single().spentDate).isEqualTo(LocalDate.of(2026, 11, 25))
        }

        @Test
        fun `없는 거래는 고정비로 전환할 수 없다`() {
            every { transactionRepository.findById(TransactionId(9)) } returns null

            assertThatThrownBy { service.convertFromTransaction(TransactionId(9)) }
                .isInstanceOf(ResourceNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("InstallmentPlanService")
    inner class InstallmentPlans {

        private val installmentPlanRepository = mockk<InstallmentPlanRepository>(relaxUnitFun = true)
        private val service = InstallmentPlanService(installmentPlanRepository, transactionRepository, categoryRepository)
        private val command = RegisterInstallmentPlanCommand(
            CategoryId(1), "겨울 패딩", won(288_000), 3, november, 15, Tags.of("의류"), "현대카드",
        )

        @Test
        fun `등록하면 모든 회차 거래를 만든다`() {
            // given
            val parts = slot<List<Transaction>>()
            val planSlot = slot<InstallmentPlan>()
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { installmentPlanRepository.save(capture(planSlot)) } answers { planSlot.captured.assignId(InstallmentPlanId(3)) }
            every { transactionRepository.saveAll(capture(parts)) } answers { parts.captured }

            // when
            service.register(command)

            // then
            assertThat(parts.captured).hasSize(3)
            assertThat(Money.sum(parts.captured.map(Transaction::amount))).isEqualTo(won(288_000))
            assertThat(parts.captured.map(Transaction::installmentPlanId).toSet()).containsExactly(InstallmentPlanId(3))
        }

        @Test
        fun `저장소가 식별자를 주지 않으면 회차를 만들지 않는다`() {
            val planSlot = slot<InstallmentPlan>()
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { installmentPlanRepository.save(capture(planSlot)) } answers { planSlot.captured }

            assertThatThrownBy { service.register(command) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `없는 카테고리면 404 로 거부한다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns null

            assertThatThrownBy { service.register(command) }.isInstanceOf(ResourceNotFoundException::class.java)
        }

        @Test
        fun `해지하면 회차 거래와 계획을 함께 지운다`() {
            every { installmentPlanRepository.findById(InstallmentPlanId(1)) } returns installmentPlan()

            service.cancel(InstallmentPlanId(1))

            verify { transactionRepository.deleteAllByInstallmentPlanId(InstallmentPlanId(1)) }
            verify { installmentPlanRepository.deleteById(InstallmentPlanId(1)) }
        }

        @Test
        fun `없는 할부는 해지할 수 없다`() {
            every { installmentPlanRepository.findById(InstallmentPlanId(9)) } returns null

            assertThatThrownBy { service.cancel(InstallmentPlanId(9)) }.isInstanceOf(ResourceNotFoundException::class.java)
        }

        @Test
        fun `목록을 그대로 돌려준다`() {
            every { installmentPlanRepository.findAll() } returns listOf(installmentPlan())

            assertThat(service.getAll()).hasSize(1)
        }
    }
}
