package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.CategoryUseCase
import com.example.jipsa.application.port.FixedExpenseUseCase
import com.example.jipsa.application.port.InstallmentPlanUseCase
import com.example.jipsa.application.port.StatisticsUseCase
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ChangeRate
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.KindAmounts
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.SpendingComparison
import com.example.jipsa.domain.model.Tag
import com.example.jipsa.domain.query.TagTotal
import com.example.jipsa.domain.service.CategoryIncrease
import com.example.jipsa.domain.service.CategoryTrend
import com.example.jipsa.domain.service.MonthlyCommitment
import com.example.jipsa.domain.service.SpendingInsight
import com.example.jipsa.fixture.category
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@WebMvcTest(
    CategoryController::class,
    FixedExpenseController::class,
    InstallmentPlanController::class,
    StatisticsController::class,
)
@Import(OtherControllersTest.MockConfiguration::class)
@DisplayName("카테고리·고정비·할부·분석 API")
class OtherControllersTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var categoryUseCase: CategoryUseCase

    @field:Autowired
    private lateinit var fixedExpenseUseCase: FixedExpenseUseCase

    @field:Autowired
    private lateinit var installmentPlanUseCase: InstallmentPlanUseCase

    @field:Autowired
    private lateinit var statisticsUseCase: StatisticsUseCase

    private val november = YearMonth.of(2026, 11)

    @BeforeEach
    fun resetMocks() = clearMocks(categoryUseCase, fixedExpenseUseCase, installmentPlanUseCase, statisticsUseCase)

    @Test
    fun `카테고리를 등록하고 조회하고 수정하고 삭제한다`() {
        every { categoryUseCase.register(any()) } returns category(id = 3)
        every { categoryUseCase.getAll() } returns listOf(category())
        every { categoryUseCase.update(any()) } returns category(name = "외식")
        every { categoryUseCase.delete(CategoryId(3)) } returns Unit

        mockMvc.perform(
            post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("""{"name":"식비","icon":"🍚","color":"#EF4444"}"""),
        ).andExpect(status().isCreated).andExpect(jsonPath("$.id").value(3))
        mockMvc.perform(get("/api/categories")).andExpect(jsonPath("$[0].color").value("#EF4444"))
        mockMvc.perform(patch("/api/categories/1").contentType(MediaType.APPLICATION_JSON).content("""{"name":"외식"}"""))
            .andExpect(jsonPath("$.name").value("외식"))
        mockMvc.perform(delete("/api/categories/3")).andExpect(status().isNoContent)
    }

    @Test
    fun `카테고리 필수 값이 없으면 400 이다`() {
        mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("""{"name":"식비"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `고정비 목록은 기준월의 납부 여부와 말일 보정된 납부일을 준다`() {
        every { fixedExpenseUseCase.getAll() } returns listOf(fixedExpense(dayOfMonth = 31, endMonth = YearMonth.of(2027, 1)))

        mockMvc.perform(get("/api/fixed-expenses?month=2027-02"))
            .andExpect(jsonPath("$[0].activeInMonth").value(false))
            .andExpect(jsonPath("$[0].dueDateInMonth").value("2027-02-28"))
            .andExpect(jsonPath("$[0].endMonth").value("2027-01"))
        // 월을 생략하면 이번 달(고정 시계: 2026-11)이다
        mockMvc.perform(get("/api/fixed-expenses")).andExpect(jsonPath("$[0].month").value("2026-11"))
    }

    @Test
    fun `고정비를 등록하고 종료하고 기록한다`() {
        every { fixedExpenseUseCase.register(any()) } returns fixedExpense(id = 4)
        every { fixedExpenseUseCase.terminate(FixedExpenseId(4), YearMonth.of(2026, 12)) } returns
            fixedExpense(id = 4, endMonth = YearMonth.of(2026, 12))
        every { fixedExpenseUseCase.recordFor(november) } returns 2

        mockMvc.perform(
            post("/api/fixed-expenses").contentType(MediaType.APPLICATION_JSON).content(
                """{"categoryId":7,"merchant":"월세","amount":650000,"dayOfMonth":1,"startMonth":"2026-11","tags":["집"]}""",
            ),
        ).andExpect(status().isCreated).andExpect(jsonPath("$.startMonth").value("2026-01"))
        mockMvc.perform(
            post("/api/fixed-expenses/4/terminate").contentType(MediaType.APPLICATION_JSON).content("""{"lastMonth":"2026-12"}"""),
        ).andExpect(jsonPath("$.endMonth").value("2026-12"))
        mockMvc.perform(post("/api/fixed-expenses/records?month=2026-11")).andExpect(jsonPath("$.recordedCount").value(2))
    }

    @Test
    fun `할부 목록은 기준월의 진행 상황을 준다`() {
        every { installmentPlanUseCase.getAll() } returns listOf(installmentPlan())

        mockMvc.perform(get("/api/installment-plans?month=2026-11"))
            .andExpect(jsonPath("$[0].monthlyAmount").value(258000))
            .andExpect(jsonPath("$[0].lastBillingMonth").value("2027-02"))
            .andExpect(jsonPath("$[0].progress.currentSequence").value(9))
            .andExpect(jsonPath("$[0].progress.remainingAmount").value(774000))
        // 기간 밖이면 진행 상황이 없다
        mockMvc.perform(get("/api/installment-plans?month=2027-06")).andExpect(jsonPath("$[0].progress").doesNotExist())
        mockMvc.perform(get("/api/installment-plans")).andExpect(jsonPath("$[0].month").value("2026-11"))
    }

    @Test
    fun `할부를 등록하고 해지한다`() {
        every { installmentPlanUseCase.register(any()) } returns installmentPlan(id = 2, totalAmount = 1_000_000, installmentMonths = 3)
        every { installmentPlanUseCase.cancel(InstallmentPlanId(2)) } returns Unit

        mockMvc.perform(
            post("/api/installment-plans").contentType(MediaType.APPLICATION_JSON).content(
                """{"categoryId":1,"merchant":"패딩","totalAmount":1000000,"installmentMonths":3,"firstBillingMonth":"2026-03","billingDay":15}""",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.firstInstallmentAmount").value(333334))
            .andExpect(jsonPath("$.progress.currentSequence").value(1))
        mockMvc.perform(delete("/api/installment-plans/2")).andExpect(status().isNoContent)
        verify { installmentPlanUseCase.cancel(InstallmentPlanId(2)) }
    }

    @Test
    fun `월 요약은 종류별 비중과 증감률을 담고 정의되지 않은 증감률은 뺀다`() {
        every { statisticsUseCase.monthlySummary(november) } returns SpendingComparison(
            november,
            KindAmounts(won(1_500_000), won(1_000_000), won(500_000)),
            KindAmounts(won(1_000_000), won(1_000_000), won(0)),
        )

        mockMvc.perform(get("/api/statistics/monthly-summary?month=2026-11"))
            .andExpect(jsonPath("$.total").value(3_000_000))
            .andExpect(jsonPath("$.totalChangeRate").value(50.0))
            .andExpect(jsonPath("$.kinds[0].kind").value("GENERAL"))
            .andExpect(jsonPath("$.kinds[2].ratio").value(16.7))
            .andExpect(jsonPath("$.kinds[2].changeRate").doesNotExist())
            .andExpect(jsonPath("$.commitmentRatio").value(50.0))
    }

    @Test
    fun `종류 추이와 카테고리 추이를 준다`() {
        every { statisticsUseCase.kindTrend(november.minusMonths(1), november) } returns listOf(
            MonthlyKindAmounts(november, KindAmounts(won(1), won(2), won(3))),
        )
        every { statisticsUseCase.categoryTrend(november.minusMonths(1), november, november) } returns listOf(
            CategoryTrend(CategoryId(1), listOf(won(400), won(500)), won(500), ChangeRate.between(won(500), won(400))),
        )

        mockMvc.perform(get("/api/statistics/kind-trend?from=2026-10&to=2026-11"))
            .andExpect(jsonPath("$[0].total").value(6))
        mockMvc.perform(get("/api/statistics/category-trend?from=2026-10&to=2026-11"))
            .andExpect(jsonPath("$.months[1]").value("2026-11"))
            .andExpect(jsonPath("$.targetMonth").value("2026-11"))
            .andExpect(jsonPath("$.categories[0].monthlyAmounts[1]").value(500))
            .andExpect(jsonPath("$.categories[0].changeRate").value(25.0))
    }

    @Test
    fun `태그 순위와 인사이트와 약정 전망을 준다`() {
        every { statisticsUseCase.tagRanking(november, 10) } returns listOf(TagTotal(Tag.of("점심"), won(100), 2))
        every { statisticsUseCase.insight(november) } returns SpendingInsight(
            november, 3, won(260), won(200), ChangeRate.between(won(260), won(200)),
            CategoryIncrease(CategoryId(4), won(40)), null,
        )
        every { statisticsUseCase.commitmentForecast(november, 6) } returns listOf(
            MonthlyCommitment(november.plusMonths(1), won(10), won(5), listOf("맥북")),
        )

        mockMvc.perform(get("/api/statistics/tags?month=2026-11"))
            .andExpect(jsonPath("$[0].tag").value("점심"))
            .andExpect(jsonPath("$[0].transactionCount").value(2))
        mockMvc.perform(get("/api/statistics/insight?month=2026-11"))
            .andExpect(jsonPath("$.baselineMonths").value(3))
            .andExpect(jsonPath("$.changeRateFromBaseline").value(30.0))
            .andExpect(jsonPath("$.topIncreasedCategory.categoryId").value(4))
            .andExpect(jsonPath("$.commitmentRatio").doesNotExist())
        mockMvc.perform(get("/api/statistics/commitment-forecast?month=2026-11"))
            .andExpect(jsonPath("$[0].month").value("2026-12"))
            .andExpect(jsonPath("$[0].total").value(15))
            .andExpect(jsonPath("$[0].installmentMerchants[0]").value("맥북"))
    }

    @TestConfiguration
    class MockConfiguration {
        @Bean
        fun categoryUseCase(): CategoryUseCase = mockk()

        @Bean
        fun fixedExpenseUseCase(): FixedExpenseUseCase = mockk()

        @Bean
        fun installmentPlanUseCase(): InstallmentPlanUseCase = mockk()

        @Bean
        fun statisticsUseCase(): StatisticsUseCase = mockk()

        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    }
}
