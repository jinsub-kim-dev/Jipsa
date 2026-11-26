package com.example.jipsa.application.service

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.domain.service.CardStatementImporter
import com.example.jipsa.domain.service.CategoryRule
import com.example.jipsa.domain.service.MerchantCategoryClassifier
import com.example.jipsa.fixture.cardEntry
import com.example.jipsa.fixture.cardStatement
import com.example.jipsa.fixture.category
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.won
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("카드 이용내역 가져오기 서비스")
class CardStatementImportServiceTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val installmentPlanRepository = mockk<InstallmentPlanRepository>()
    private val fixedExpenseRepository = mockk<FixedExpenseRepository>()
    private val service = CardStatementImportService(
        categoryRepository,
        transactionRepository,
        installmentPlanRepository,
        fixedExpenseRepository,
        CardStatementImporter(MerchantCategoryClassifier(listOf(CategoryRule("식비", listOf("김밥"))))),
    )

    private val uncategorized = category(name = "미분류", icon = "❔", color = "#9CA3AF", id = 13)
    private val savedTransactions = mutableListOf<List<Transaction>>()

    @BeforeEach
    fun setUp() {
        every { transactionRepository.search(any()) } returns emptyList()
        every { transactionRepository.saveAll(capture(savedTransactions)) } answers { firstArg() }
        every { installmentPlanRepository.findAll() } returns emptyList()
        every { fixedExpenseRepository.findAll() } returns emptyList()
    }

    @Test
    fun `미분류 카테고리가 없으면 만든다`() {
        // given
        val created = slot<Category>()
        every { categoryRepository.findAll() } returns listOf(category(name = "식비", id = 1))
        every { categoryRepository.save(capture(created)) } answers { created.captured.assignId(CategoryId(13)) }

        // when
        val result = service.importStatement(cardStatement(cardEntry(merchant = "가나다상사")))

        // then
        assertThat(created.captured.name).isEqualTo("미분류")
        assertThat(savedTransactions.single().single().categoryId).isEqualTo(CategoryId(13))
        assertThat(result.uncategorized).hasSize(1)
        assertThat(result.uncategorizedCategoryId).isEqualTo(CategoryId(13))
    }

    @Test
    fun `미분류 카테고리가 있으면 다시 만들지 않는다`() {
        // given
        every { categoryRepository.findAll() } returns listOf(category(name = "식비", id = 1), uncategorized)

        // when
        service.importStatement(cardStatement(cardEntry(merchant = "OO김밥")))

        // then
        verify(exactly = 0) { categoryRepository.save(any()) }
        assertThat(savedTransactions.single().single().categoryId).isEqualTo(CategoryId(1))
    }

    @Test
    fun `일시불 기간이 걸친 달 전체의 거래를 읽어 비교한다`() {
        // given
        every { categoryRepository.findAll() } returns listOf(uncategorized)

        // when
        service.importStatement(
            cardStatement(
                cardEntry(approvalNumber = "1", spentDate = LocalDate.of(2026, 8, 26)),
                cardEntry(approvalNumber = "2", spentDate = LocalDate.of(2026, 11, 25)),
            ),
        )

        // then
        // 고정비 자동 기록은 납부일(예: 11/30)에 있어 이용내역 기간 밖일 수 있다
        verify { transactionRepository.search(TransactionSearchCriteria(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 30))) }
    }

    @Test
    fun `일시불이 없으면 거래를 읽지 않는다`() {
        // given
        every { categoryRepository.findAll() } returns listOf(uncategorized)
        every { installmentPlanRepository.save(any()) } answers { firstArg<InstallmentPlan>().assignId(InstallmentPlanId(7)) }

        // when
        service.importStatement(cardStatement(cardEntry(amount = 30_000, installmentMonths = 3)))

        // then
        verify(exactly = 0) { transactionRepository.search(any()) }
    }

    @Test
    fun `할부는 계획을 저장한 뒤 모든 회차 거래를 만든다`() {
        // given
        every { categoryRepository.findAll() } returns listOf(uncategorized)
        every { installmentPlanRepository.save(any()) } answers { firstArg<InstallmentPlan>().assignId(InstallmentPlanId(7)) }

        // when
        val result = service.importStatement(
            cardStatement(cardEntry(merchant = "가나의원", amount = 240_000, installmentMonths = 2, spentDate = LocalDate.of(2026, 9, 28))),
        )

        // then
        val parts = savedTransactions.flatten()
        assertThat(parts.map { it.installmentPlanId }).containsOnly(InstallmentPlanId(7))
        assertThat(parts.map { it.spentDate }).containsExactly(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 28))
        assertThat(result.importedInstallmentPlanCount).isEqualTo(1)
    }

    @Test
    fun `결과에 추가·중복·처리 안 됨을 담는다`() {
        // given
        every { categoryRepository.findAll() } returns listOf(uncategorized)
        every { transactionRepository.search(any()) } returns listOf(
            Transaction.createGeneral(CategoryId(13), won(8_000), "OO김밥", LocalDate.of(2026, 11, 10), paymentMethod = "삼성카드"),
        )

        // when
        val result = service.importStatement(
            cardStatement(
                cardEntry(approvalNumber = "1"),
                cardEntry(approvalNumber = "2", merchant = "OO프레시"),
                cardEntry(approvalNumber = "3", amount = 12_000),
                cardEntry(approvalNumber = "3", amount = -12_000),
            ),
        )

        // then
        assertThat(result.importedTransactionCount).isEqualTo(1)
        assertThat(result.duplicates).hasSize(1)
        assertThat(result.unprocessed.single().reason).contains("취소")
    }

    @Test
    fun `고정비와 같은 사용처는 고정비 거래로 저장하고 자동 기록은 실제값으로 고쳐 저장한다`() {
        // given
        every { categoryRepository.findAll() } returns listOf(uncategorized)
        val insurance = fixedExpense(merchant = "OO보험", amount = 15_000, dayOfMonth = 25, startMonth = YearMonth.of(2026, 9), id = 3)
        val scheduledOctober = insurance.occurrenceIn(YearMonth.of(2026, 10)).assignId(TransactionId(40))
        every { fixedExpenseRepository.findAll() } returns listOf(insurance)
        every { transactionRepository.search(any()) } returns listOf(scheduledOctober)
        val corrected = slot<Transaction>()
        every { transactionRepository.save(capture(corrected)) } answers { corrected.captured }

        // when
        val result = service.importStatement(
            cardStatement(
                cardEntry(approvalNumber = "1", merchant = "OO보험", amount = 15_000, spentDate = LocalDate.of(2026, 9, 26)),
                cardEntry(approvalNumber = "2", merchant = "OO보험", amount = 15_000, spentDate = LocalDate.of(2026, 10, 27)),
            ),
        )

        // then
        assertThat(savedTransactions.single().map { it.kind to it.spentDate })
            .containsExactly(ExpenseKind.FIXED to LocalDate.of(2026, 9, 26))
        assertThat(corrected.captured.id).isEqualTo(TransactionId(40))
        assertThat(corrected.captured.spentDate).isEqualTo(LocalDate.of(2026, 10, 27))
        assertThat(result.fixedExpenseMatches).hasSize(2)
        assertThat(result.importedTransactionCount).isZero()
        assertThat(savedTransactions.single().map { it.fixedExpenseId }).containsOnly(FixedExpenseId(3))
    }
}
