package com.example.jipsa.domain.service

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.KindAmounts
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.query.MonthlyCategoryExpense
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("도메인 서비스")
class DomainServicesTest {

    private val november = YearMonth.of(2026, 11)

    @Nested
    @DisplayName("FixedExpenseRecorder - 고정비 기록")
    inner class Recorder {

        private val recorder = FixedExpenseRecorder()

        @Test
        fun `그 달에 나가는 고정비만 기록한다`() {
            // given
            val rent = fixedExpense(merchant = "월세", id = 1)
            val ended = fixedExpense(merchant = "헬스장", endMonth = YearMonth.of(2026, 9), id = 2)
            val notStarted = fixedExpense(merchant = "보험", startMonth = YearMonth.of(2027, 1), id = 3)

            // when
            val records = recorder.recordsFor(november, listOf(rent, ended, notStarted), emptySet())

            // then
            assertThat(records.map { it.merchant }).containsExactly("월세")
        }

        @Test
        fun `이미 기록된 고정비는 다시 기록하지 않는다`() {
            // given
            val rent = fixedExpense(merchant = "월세", id = 1)
            val netflix = fixedExpense(merchant = "넷플릭스", id = 2)

            // when
            val records = recorder.recordsFor(november, listOf(rent, netflix), setOf(FixedExpenseId(1)))

            // then
            assertThat(records.map { it.fixedExpenseId }).containsExactly(FixedExpenseId(2))
        }

        @Test
        fun `모두 기록되어 있으면 아무것도 만들지 않는다`() {
            val rent = fixedExpense(id = 1)

            assertThat(recorder.recordsFor(november, listOf(rent), setOf(FixedExpenseId(1)))).isEmpty()
        }
    }

    @Nested
    @DisplayName("CommitmentForecaster - 약정 지출 전망")
    inner class Forecaster {

        private val forecaster = CommitmentForecaster()

        @Test
        fun `기준월 다음 달부터 고정비와 할부를 합산한다`() {
            // given
            val rent = fixedExpense(amount = 650_000)
            val gym = fixedExpense(amount = 80_000, endMonth = YearMonth.of(2026, 12), id = 2)
            val flight = installmentPlan(
                merchant = "항공권", totalAmount = 558_000, installmentMonths = 3,
                firstBillingMonth = YearMonth.of(2026, 10), id = 1,
            )
            val laptop = installmentPlan(merchant = "맥북", id = 2)

            // when
            val forecast = forecaster.forecast(november, 4, listOf(rent, gym), listOf(flight, laptop))

            // then
            assertThat(forecast.map { it.month }).containsExactly(
                YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2), YearMonth.of(2027, 3),
            )
            assertThat(forecast.map { it.fixed }).containsExactly(won(730_000), won(650_000), won(650_000), won(650_000))
            // 항공권은 10~12월, 맥북은 2027-02 까지
            assertThat(forecast.map { it.installment }).containsExactly(won(444_000), won(258_000), won(258_000), Money.ZERO)
            assertThat(forecast[0].installmentMerchants).containsExactly("항공권", "맥북")
            assertThat(forecast[3].installmentMerchants).isEmpty()
            assertThat(forecast[0].total).isEqualTo(won(1_174_000))
        }

        @Test
        fun `약정이 없으면 0원으로 채운다`() {
            val forecast = forecaster.forecast(november, 2, emptyList(), emptyList())

            assertThat(forecast).hasSize(2).allSatisfy { assertThat(it.total).isEqualTo(Money.ZERO) }
        }
    }

    @Nested
    @DisplayName("CategoryTrendAnalyzer - 카테고리 × 월")
    inner class TrendAnalyzer {

        private val analyzer = CategoryTrendAnalyzer()
        private val months = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), november)
        private val food = CategoryId(1)
        private val shopping = CategoryId(2)
        private val travel = CategoryId(3)

        @Test
        fun `빈 칸을 0원으로 채우고 기준월 증감률을 붙인다`() {
            // given
            val rows = listOf(
                MonthlyCategoryExpense(YearMonth.of(2026, 9), food, won(500_000)),
                MonthlyCategoryExpense(YearMonth.of(2026, 10), food, won(400_000)),
                MonthlyCategoryExpense(november, food, won(500_000)),
                MonthlyCategoryExpense(november, shopping, won(300_000)),
            )

            // when
            val trends = analyzer.analyze(months, november, rows)

            // then
            assertThat(trends.map { it.categoryId }).containsExactly(food, shopping)
            assertThat(trends[0].monthlyAmounts).containsExactly(won(500_000), won(400_000), won(500_000))
            assertThat(trends[0].changeRate!!.percentage).isEqualTo(25.0)
            assertThat(trends[1].monthlyAmounts).containsExactly(Money.ZERO, Money.ZERO, won(300_000))
            // 전월이 0원이면 신규
            assertThat(trends[1].changeRate).isNull()
        }

        @Test
        fun `기준월 지출 내림차순, 같으면 카테고리 ID 순으로 정렬한다`() {
            // given: 기준월에 지출이 없는 카테고리도 기간 안에 있으면 남는다
            val rows = listOf(
                MonthlyCategoryExpense(november, shopping, won(100)),
                MonthlyCategoryExpense(november, food, won(100)),
                MonthlyCategoryExpense(YearMonth.of(2026, 9), travel, won(900)),
            )

            // when
            val trends = analyzer.analyze(months, november, rows)

            // then
            assertThat(trends.map { it.categoryId }).containsExactly(food, shopping, travel)
            assertThat(trends[2].targetAmount).isEqualTo(Money.ZERO)
        }
    }

    @Nested
    @DisplayName("SpendingInsightAnalyzer - 인사이트")
    inner class InsightAnalyzer {

        private val analyzer = SpendingInsightAnalyzer()

        private fun month(month: YearMonth, general: Long, fixed: Long = 0, installment: Long = 0) =
            MonthlyKindAmounts(month, KindAmounts(won(general), won(fixed), won(installment)))

        @Test
        fun `직전 3개월 평균과 비교한다`() {
            // given: 8~10월 평균 200만, 11월 260만
            val amounts = listOf(
                month(YearMonth.of(2026, 7), 9_000_000),
                month(YearMonth.of(2026, 8), 1_000_000),
                month(YearMonth.of(2026, 9), 2_000_000),
                month(YearMonth.of(2026, 10), 3_000_000),
                month(november, 1_300_000, fixed = 1_000_000, installment = 300_000),
            )

            // when
            val insight = analyzer.analyze(november, amounts, emptyList())

            // then: 7월은 비교 기간 밖이라 무시한다
            assertThat(insight.baselineMonths).isEqualTo(3)
            assertThat(insight.baselineAverage).isEqualTo(won(2_000_000))
            assertThat(insight.changeRateFromBaseline!!.percentage).isEqualTo(30.0)
            assertThat(insight.commitmentRatio!!.percentage).isEqualTo(50.0)
            assertThat(insight.total).isEqualTo(won(2_600_000))
        }

        @Test
        fun `지출이 없는 달은 평균에서 뺀다`() {
            // given: 가계부를 9월부터 썼다. 8월은 비어 있다
            val amounts = listOf(
                month(YearMonth.of(2026, 8), 0),
                month(YearMonth.of(2026, 9), 1_000_000),
                month(YearMonth.of(2026, 10), 2_000_000),
                month(november, 1_500_000),
            )

            // when
            val insight = analyzer.analyze(november, amounts, emptyList())

            // then
            assertThat(insight.baselineAverage).isEqualTo(won(1_500_000))
            assertThat(insight.changeRateFromBaseline!!.percentage).isEqualTo(0.0)
        }

        @Test
        fun `비교할 달이 없으면 평균과 증감률을 정의하지 않는다`() {
            val insight = analyzer.analyze(november, listOf(month(november, 1_000)), emptyList())

            assertThat(insight.baselineAverage).isNull()
            assertThat(insight.changeRateFromBaseline).isNull()
        }

        @Test
        fun `그 달 기록이 없으면 총액은 0원이고 비중은 정의하지 않는다`() {
            val insight = analyzer.analyze(november, emptyList(), emptyList())

            assertThat(insight.total).isEqualTo(Money.ZERO)
            assertThat(insight.commitmentRatio).isNull()
        }

        @Test
        fun `전월보다 가장 많이 늘어난 카테고리를 찾는다`() {
            // given
            val october = november.minusMonths(1)
            val expenses = listOf(
                MonthlyCategoryExpense(october, CategoryId(1), won(400_000)),
                MonthlyCategoryExpense(november, CategoryId(1), won(450_000)),
                MonthlyCategoryExpense(october, CategoryId(2), won(100_000)),
                MonthlyCategoryExpense(november, CategoryId(2), won(500_000)),
                MonthlyCategoryExpense(november, CategoryId(3), won(200_000)),
                MonthlyCategoryExpense(october, CategoryId(4), won(900_000)),
                MonthlyCategoryExpense(YearMonth.of(2026, 9), CategoryId(5), won(9_000_000)),
            )

            // when
            val top = analyzer.analyze(november, emptyList(), expenses).topIncreasedCategory

            // then: 쇼핑(2) +40만 > 신규(3) +20만 > 식비(1) +5만. 4는 줄었고 5는 기간 밖이다
            assertThat(top!!.categoryId).isEqualTo(CategoryId(2))
            assertThat(top.increase).isEqualTo(won(400_000))
        }

        @Test
        fun `늘어난 금액이 같으면 ID 가 작은 카테고리를 고른다`() {
            val expenses = listOf(
                MonthlyCategoryExpense(november, CategoryId(9), won(100)),
                MonthlyCategoryExpense(november, CategoryId(3), won(100)),
            )

            assertThat(analyzer.analyze(november, emptyList(), expenses).topIncreasedCategory!!.categoryId)
                .isEqualTo(CategoryId(3))
        }

        @Test
        fun `늘어난 카테고리가 없으면 비운다`() {
            val expenses = listOf(
                MonthlyCategoryExpense(november.minusMonths(1), CategoryId(1), won(100)),
                MonthlyCategoryExpense(november, CategoryId(1), won(100)),
            )

            assertThat(analyzer.analyze(november, emptyList(), expenses).topIncreasedCategory).isNull()
        }
    }

    @Test
    fun `고정비 기록 결과는 납부일 기준 소비일을 갖는다`() {
        val records = FixedExpenseRecorder().recordsFor(
            YearMonth.of(2026, 2),
            listOf(fixedExpense(dayOfMonth = 31)),
            emptySet(),
        )

        assertThat(records.single().spentDate).isEqualTo(LocalDate.of(2026, 2, 28))
    }
}
