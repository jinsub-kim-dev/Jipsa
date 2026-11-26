package com.example.jipsa.domain.model

import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.YearMonth

@DisplayName("KindAmounts / SpendingComparison - 종류별 합계와 전월 비교")
class KindAmountsTest {

    private val amounts = KindAmounts(general = won(1_500_000), fixed = won(1_000_000), installment = won(500_000))

    @Test
    fun `합계와 약정 지출을 구한다`() {
        assertThat(amounts.total).isEqualTo(won(3_000_000))
        assertThat(amounts.commitment).isEqualTo(won(1_500_000))
        assertThat(amounts.commitmentRatio!!.percentage).isEqualTo(50.0)
    }

    @Test
    fun `종류별 금액과 비중을 구한다`() {
        assertThat(ExpenseKind.entries.map(amounts::amountOf))
            .containsExactly(won(1_500_000), won(1_000_000), won(500_000))
        assertThat(amounts.ratioOf(ExpenseKind.INSTALLMENT)!!.percentage).isEqualTo(16.7)
    }

    @Test
    fun `지출이 없으면 비중을 정의하지 않는다`() {
        assertThat(KindAmounts.ZERO.ratioOf(ExpenseKind.GENERAL)).isNull()
        assertThat(KindAmounts.ZERO.commitmentRatio).isNull()
    }

    @Test
    fun `맵에서 만들 때 없는 종류는 0원이다`() {
        val partial = KindAmounts.of(mapOf(ExpenseKind.FIXED to won(10)))

        assertThat(partial).isEqualTo(KindAmounts(Money.ZERO, won(10), Money.ZERO))
        assertThat(KindAmounts.of(ExpenseKind.entries.associateWith { won(1) }).total).isEqualTo(won(3))
    }

    @Test
    fun `약정 지출 여부는 일반 지출만 아니다`() {
        assertThat(ExpenseKind.entries.filter(ExpenseKind::isCommitment))
            .containsExactly(ExpenseKind.FIXED, ExpenseKind.INSTALLMENT)
    }

    @Test
    fun `전월 대비 증감률을 종류별로 구한다`() {
        // given
        val previous = KindAmounts(general = won(1_000_000), fixed = won(1_000_000), installment = Money.ZERO)
        val comparison = SpendingComparison(YearMonth.of(2026, 11), amounts, previous)

        // then
        assertThat(comparison.totalChangeRate!!.percentage).isEqualTo(50.0)
        assertThat(comparison.changeRateOf(ExpenseKind.GENERAL)!!.percentage).isEqualTo(50.0)
        assertThat(comparison.changeRateOf(ExpenseKind.FIXED)!!.percentage).isEqualTo(0.0)
        // 전월 할부가 0원이었으므로 증감률은 정의되지 않는다 (신규)
        assertThat(comparison.changeRateOf(ExpenseKind.INSTALLMENT)).isNull()
    }

    @Test
    fun `전월 지출이 없으면 총액 증감률도 정의하지 않는다`() {
        assertThat(SpendingComparison(YearMonth.of(2026, 6), amounts, KindAmounts.ZERO).totalChangeRate).isNull()
    }
}
