package com.example.jipsa.application.service

import com.example.jipsa.application.port.RegisterCategoryCommand
import com.example.jipsa.application.port.UpdateCategoryCommand
import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.KindAmounts
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.query.MonthlyCategoryExpense
import com.example.jipsa.domain.query.StatisticsQuery
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.domain.service.CategoryTrendAnalyzer
import com.example.jipsa.domain.service.CommitmentForecaster
import com.example.jipsa.domain.service.SpendingInsightAnalyzer
import com.example.jipsa.fixture.category
import com.example.jipsa.fixture.fixedExpense
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
import java.time.YearMonth

@DisplayName("카테고리·분석 서비스")
class CategoryAndStatisticsServiceTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private val fixedExpenseRepository = mockk<FixedExpenseRepository>()
    private val installmentPlanRepository = mockk<InstallmentPlanRepository>()

    @Nested
    @DisplayName("CategoryService")
    inner class Categories {

        private val categoryRepository = mockk<CategoryRepository>(relaxUnitFun = true)
        private val service = CategoryService(
            categoryRepository, transactionRepository, fixedExpenseRepository, installmentPlanRepository,
        )
        private val saved = slot<Category>()

        @Test
        fun `새 이름이면 등록한다`() {
            every { categoryRepository.existsByName("식비") } returns false
            every { categoryRepository.save(capture(saved)) } answers { saved.captured.assignId(CategoryId(1)) }

            assertThat(service.register(RegisterCategoryCommand(" 식비 ", "🍚", "#ef4444")).color).isEqualTo("#EF4444")
        }

        @Test
        fun `이름이 겹치면 409 로 거부한다`() {
            every { categoryRepository.existsByName("식비") } returns true

            assertThatThrownBy { service.register(RegisterCategoryCommand("식비", "🍚", "#EF4444")) }
                .isInstanceOf(DomainStateException::class.java)
        }

        @Test
        fun `이름과 표시를 바꾼다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { categoryRepository.existsByName("외식") } returns false
            every { categoryRepository.save(capture(saved)) } answers { saved.captured }

            val updated = service.update(UpdateCategoryCommand(CategoryId(1), "외식", null, "#000000"))

            assertThat(updated.name).isEqualTo("외식")
            assertThat(updated.icon).isEqualTo("🍚")
            assertThat(updated.color).isEqualTo("#000000")
        }

        @Test
        fun `같은 이름으로 바꾸는 것은 중복이 아니다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns category(name = "식비")
            every { categoryRepository.save(capture(saved)) } answers { saved.captured }

            assertThat(service.update(UpdateCategoryCommand(CategoryId(1), "식비", "🍝", null)).icon).isEqualTo("🍝")
            verify(exactly = 0) { categoryRepository.existsByName(any()) }
        }

        @Test
        fun `아무것도 바꾸지 않으면 그대로 저장한다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { categoryRepository.save(capture(saved)) } answers { saved.captured }

            assertThat(service.update(UpdateCategoryCommand(CategoryId(1), null, null, null)).name).isEqualTo("식비")
        }

        @Test
        fun `쓰는 곳이 없으면 삭제한다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { transactionRepository.existsByCategoryId(CategoryId(1)) } returns false
            every { fixedExpenseRepository.existsByCategoryId(CategoryId(1)) } returns false
            every { installmentPlanRepository.existsByCategoryId(CategoryId(1)) } returns false

            service.delete(CategoryId(1))

            verify { categoryRepository.deleteById(CategoryId(1)) }
        }

        @Test
        fun `거래나 할부가 쓰고 있으면 409 로 거부한다`() {
            every { categoryRepository.findById(CategoryId(1)) } returns category()
            every { transactionRepository.existsByCategoryId(CategoryId(1)) } returns false
            every { fixedExpenseRepository.existsByCategoryId(CategoryId(1)) } returns false
            every { installmentPlanRepository.existsByCategoryId(CategoryId(1)) } returns true

            assertThatThrownBy { service.delete(CategoryId(1)) }.isInstanceOf(DomainStateException::class.java)
        }

        @Test
        fun `없는 카테고리는 404 다`() {
            every { categoryRepository.findById(CategoryId(9)) } returns null

            assertThatThrownBy { service.delete(CategoryId(9)) }.isInstanceOf(ResourceNotFoundException::class.java)
        }

        @Test
        fun `목록을 돌려준다`() {
            every { categoryRepository.findAll() } returns listOf(category())

            assertThat(service.getAll()).hasSize(1)
        }
    }

    @Nested
    @DisplayName("StatisticsService")
    inner class Statistics {

        private val statisticsQuery = mockk<StatisticsQuery>()
        private val service = StatisticsService(
            statisticsQuery, fixedExpenseRepository, installmentPlanRepository,
            CategoryTrendAnalyzer(), SpendingInsightAnalyzer(), CommitmentForecaster(),
        )
        private val november = YearMonth.of(2026, 11)
        private val october = YearMonth.of(2026, 10)

        @Test
        fun `월 요약은 그 달과 전월을 비교한다`() {
            every { statisticsQuery.monthlyKindAmounts(october, november) } returns listOf(
                MonthlyKindAmounts(october, KindAmounts(won(100), won(0), won(0))),
                MonthlyKindAmounts(november, KindAmounts(won(150), won(0), won(0))),
            )

            val summary = service.monthlySummary(november)

            assertThat(summary.totalChangeRate!!.percentage).isEqualTo(50.0)
        }

        @Test
        fun `조회 결과에 달이 빠져 있으면 0원으로 본다`() {
            every { statisticsQuery.monthlyKindAmounts(october, november) } returns emptyList()

            val summary = service.monthlySummary(november)

            assertThat(summary.current).isEqualTo(KindAmounts.ZERO)
            assertThat(summary.previous).isEqualTo(KindAmounts.ZERO)
        }

        @Test
        fun `추이는 구간을 검증한 뒤 조회한다`() {
            every { statisticsQuery.monthlyKindAmounts(october, november) } returns emptyList()

            assertThat(service.kindTrend(october, november)).isEmpty()
            assertThatThrownBy { service.kindTrend(november, october) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `카테고리 추이의 기준월은 구간 안이어야 한다`() {
            every { statisticsQuery.monthlyCategoryExpenses(october, november) } returns listOf(
                MonthlyCategoryExpense(november, CategoryId(1), won(10)),
            )

            assertThat(service.categoryTrend(october, november, november).single().targetAmount).isEqualTo(won(10))
            assertThatThrownBy { service.categoryTrend(october, november, YearMonth.of(2026, 12)) }
                .isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `태그 순위는 1~50개까지 조회한다`() {
            every { statisticsQuery.tagTotals(november.atDay(1), november.atEndOfMonth(), 10) } returns emptyList()

            assertThat(service.tagRanking(november, 10)).isEmpty()
            assertThatThrownBy { service.tagRanking(november, 0) }.isInstanceOf(InvariantViolationException::class.java)
            assertThatThrownBy { service.tagRanking(november, 51) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `인사이트는 직전 3개월과 그 달을 조회한다`() {
            every { statisticsQuery.monthlyKindAmounts(YearMonth.of(2026, 8), november) } returns emptyList()
            every { statisticsQuery.monthlyCategoryExpenses(october, november) } returns emptyList()

            assertThat(service.insight(november).baselineMonths).isEqualTo(3)
        }

        @Test
        fun `약정 지출 전망은 1~36개월까지 조회한다`() {
            every { fixedExpenseRepository.findAll() } returns listOf(fixedExpense(amount = 650_000))
            every { installmentPlanRepository.findAll() } returns emptyList()

            assertThat(service.commitmentForecast(november, 6)).hasSize(6).allSatisfy {
                assertThat(it.fixed).isEqualTo(won(650_000))
            }
            assertThatThrownBy { service.commitmentForecast(november, 0) }.isInstanceOf(InvariantViolationException::class.java)
            assertThatThrownBy { service.commitmentForecast(november, 37) }.isInstanceOf(InvariantViolationException::class.java)
        }
    }
}
