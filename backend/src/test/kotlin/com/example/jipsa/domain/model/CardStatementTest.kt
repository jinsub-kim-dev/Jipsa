package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.fixture.cardEntry
import com.example.jipsa.fixture.cardStatement
import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("카드 이용내역")
class CardStatementTest {

    @Nested
    @DisplayName("이용 건 불변식")
    inner class EntryInvariants {

        @Test
        fun `승인번호가 비어 있으면 거부한다`() {
            assertThatThrownBy { cardEntry(approvalNumber = " ") }
                .isInstanceOf(InvariantViolationException::class.java)
                .hasMessageContaining("승인번호")
        }

        @Test
        fun `사용처가 비어 있으면 거부한다`() {
            assertThatThrownBy { cardEntry(merchant = "") }
                .isInstanceOf(InvariantViolationException::class.java)
                .hasMessageContaining("사용처")
        }

        @Test
        fun `할부 개월 수가 음수면 거부한다`() {
            assertThatThrownBy { cardEntry(installmentMonths = -1) }
                .isInstanceOf(InvariantViolationException::class.java)
                .hasMessageContaining("할부 개월 수")
        }

        @ParameterizedTest(name = "{0}개월이면 일시불이다")
        @ValueSource(ints = [0, 1])
        fun `1개월 이하는 일시불이다`(months: Int) {
            assertThat(cardEntry(installmentMonths = months).isInstallment).isFalse()
        }

        @Test
        fun `2개월부터 할부다`() {
            assertThat(cardEntry(installmentMonths = 2).isInstallment).isTrue()
        }
    }

    @Nested
    @DisplayName("이용내역 불변식")
    inner class StatementInvariants {

        @Test
        fun `결제 수단이 비어 있으면 거부한다`() {
            assertThatThrownBy { CardStatement(" ", emptyList()) }
                .isInstanceOf(InvariantViolationException::class.java)
                .hasMessageContaining("결제 수단")
        }

        @Test
        fun `결제 수단이 30자를 넘으면 거부한다`() {
            assertThatThrownBy { CardStatement("가".repeat(31), emptyList()) }
                .isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("구매 뽑기 - 취소 반영")
    inner class Purchases {

        @Test
        fun `일시불 승인은 승인일자를 소비일로 하는 구매가 된다`() {
            // given
            val statement = cardStatement(cardEntry(spentDate = LocalDate.of(2026, 11, 25), merchant = "세븐일레븐", amount = 2_500))

            // when
            val purchases = statement.purchases()

            // then
            assertThat(purchases).containsExactly(CardPurchase.LumpSum(LocalDate.of(2026, 11, 25), "세븐일레븐", won(2_500)))
        }

        @Test
        fun `같은 승인번호의 전체 취소는 승인과 함께 빠진다`() {
            // given
            val statement = cardStatement(
                cardEntry(approvalNumber = "10000002", merchant = "카카오_택시_선승인", amount = 12_000),
                cardEntry(approvalNumber = "10000002", merchant = "카카오_택시_선승인", amount = -12_000),
                cardEntry(approvalNumber = "10000003", merchant = "카카오_택시_0", amount = 12_000),
            )

            // when
            val purchases = statement.purchases()

            // then
            assertThat(purchases.map { it.merchant }).containsExactly("카카오_택시_0")
        }

        @Test
        fun `부분 취소면 남은 금액만 구매가 된다`() {
            // given
            val statement = cardStatement(
                cardEntry(approvalNumber = "1", amount = 30_000),
                cardEntry(approvalNumber = "1", amount = -12_000),
            )

            // when
            val purchase = statement.purchases().single()

            // then
            assertThat(purchase.amount).isEqualTo(won(18_000))
        }

        @Test
        fun `취소 금액이 승인보다 크면 빠진다`() {
            val statement = cardStatement(cardEntry(approvalNumber = "1", amount = 10_000), cardEntry(approvalNumber = "1", amount = -10_001))

            assertThat(statement.purchases()).isEmpty()
        }

        @Test
        fun `승인 없이 취소만 있는 묶음은 빠진다`() {
            // 승인이 조회 기간 밖이고 취소만 기간 안에 들어온 경우
            val statement = cardStatement(cardEntry(approvalNumber = "1", amount = -5_000))

            assertThat(statement.purchases()).isEmpty()
        }

        @Test
        fun `승인번호가 같아도 사용처가 다르면 다른 구매다`() {
            // given
            val statement = cardStatement(
                cardEntry(approvalNumber = "1", merchant = "가게A", amount = 1_000),
                cardEntry(approvalNumber = "1", merchant = "가게B", amount = 2_000),
            )

            // when
            val purchases = statement.purchases()

            // then
            assertThat(purchases.map { it.merchant }).containsExactly("가게A", "가게B")
        }

        @Test
        fun `전체 취소된 묶음은 원래 승인 금액과 사유를 남긴다`() {
            // given
            val statement = cardStatement(
                cardEntry(approvalNumber = "1", spentDate = LocalDate.of(2026, 9, 28), merchant = "카카오T택시", amount = 12_000),
                cardEntry(approvalNumber = "1", spentDate = LocalDate.of(2026, 9, 28), merchant = "카카오T택시", amount = -12_000),
                cardEntry(approvalNumber = "3", amount = 7_000),
            )

            // when
            val cancellations = statement.cancellations()

            // then
            assertThat(cancellations).containsExactly(
                CardCancellation(LocalDate.of(2026, 9, 28), "카카오T택시", 12_000, CardCancellation.Kind.FULL),
            )
            assertThat(cancellations.single().reason).contains("취소")
        }

        @Test
        fun `취소만 있는 묶음은 원래 승인이 기간 밖이라는 사유를 남긴다`() {
            // given
            val statement = cardStatement(cardEntry(approvalNumber = "2", merchant = "가게", amount = -5_000))

            // when
            val cancellation = statement.cancellations().single()

            // then
            assertThat(cancellation.kind).isEqualTo(CardCancellation.Kind.ORPHAN)
            assertThat(cancellation.approvedAmount).isZero()
            assertThat(cancellation.reason).contains("조회 기간 밖")
        }

        @Test
        fun `부분 취소는 구매이고 취소가 아니다`() {
            val statement = cardStatement(cardEntry(approvalNumber = "1", amount = 30_000), cardEntry(approvalNumber = "1", amount = -12_000))

            assertThat(statement.cancellations()).isEmpty()
        }
    }

    @Nested
    @DisplayName("구매 뽑기 - 할부")
    inner class Installments {

        @Test
        fun `할부의 첫 납부월과 납부일은 소비일에서 정한다`() {
            // given: 9/28 승인. 카드 대금 결제일(10/13)은 쓰지 않는다
            val statement = cardStatement(
                cardEntry(spentDate = LocalDate.of(2026, 9, 28), merchant = "가나의원", amount = 240_000, installmentMonths = 2),
            )

            // when
            val purchase = statement.purchases().single()

            // then
            assertThat(purchase).isEqualTo(CardPurchase.Installment(LocalDate.of(2026, 9, 28), "가나의원", won(240_000), 2))
            val installment = purchase as CardPurchase.Installment
            assertThat(installment.firstBillingMonth).isEqualTo(YearMonth.of(2026, 9))
            assertThat(installment.billingDay).isEqualTo(28)
        }

        @Test
        fun `31일에 산 할부는 납부일이 31일이다`() {
            // 짧은 달의 말일 보정은 InstallmentPlan 이 맡는다
            val statement = cardStatement(cardEntry(spentDate = LocalDate.of(2026, 1, 31), amount = 90_000, installmentMonths = 3))

            val purchase = statement.purchases().single() as CardPurchase.Installment

            assertThat(purchase.billingDay).isEqualTo(31)
        }

        @Test
        fun `취소된 할부는 빠진다`() {
            val statement = cardStatement(
                cardEntry(approvalNumber = "1", amount = 100_000, installmentMonths = 3),
                cardEntry(approvalNumber = "1", amount = -100_000, installmentMonths = 3),
            )

            assertThat(statement.purchases()).isEmpty()
        }
    }

    @Nested
    @DisplayName("일시불 기간")
    inner class LumpSumPeriod {

        @Test
        fun `일시불 구매의 가장 이른 날부터 가장 늦은 날까지다`() {
            // given
            val statement = cardStatement(
                cardEntry(approvalNumber = "1", spentDate = LocalDate.of(2026, 10, 5)),
                cardEntry(approvalNumber = "2", spentDate = LocalDate.of(2026, 8, 26)),
                cardEntry(approvalNumber = "3", spentDate = LocalDate.of(2026, 11, 25)),
                // 할부는 기간에 넣지 않는다
                cardEntry(approvalNumber = "4", spentDate = LocalDate.of(2026, 8, 1), installmentMonths = 2, amount = 20_000),
            )

            // when
            val period = statement.lumpSumPeriod()

            // then
            assertThat(period).isEqualTo(LocalDate.of(2026, 8, 26)..LocalDate.of(2026, 11, 25))
        }

        @Test
        fun `일시불이 없으면 기간도 없다`() {
            val statement = cardStatement(cardEntry(installmentMonths = 2, amount = 20_000))

            assertThat(statement.lumpSumPeriod()).isNull()
        }
    }
}
