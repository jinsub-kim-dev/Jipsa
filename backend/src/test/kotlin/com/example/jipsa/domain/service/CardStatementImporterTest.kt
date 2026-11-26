package com.example.jipsa.domain.service

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.model.CardStatementEntry
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.fixture.cardEntry
import com.example.jipsa.fixture.cardStatement
import com.example.jipsa.fixture.category
import com.example.jipsa.fixture.fixedExpense
import com.example.jipsa.fixture.generalTransaction
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("카드 이용내역 가져오기 - 도메인 서비스")
class CardStatementImporterTest {

    @Nested
    @DisplayName("CategoryRule - 분류 규칙")
    inner class Rules {

        @Test
        fun `공백과 대소문자를 무시하고 키워드를 찾는다`() {
            val rule = CategoryRule("식비", listOf("GS25"))

            assertThat(rule.matches("지에스 gs 25 OO점")).isTrue()
        }

        @Test
        fun `키워드가 없으면 맞지 않는다`() {
            val rule = CategoryRule("식비", listOf("김밥"))

            assertThat(rule.matches("버거킹")).isFalse()
        }

        @Test
        fun `카테고리 이름이 비어 있으면 거부한다`() {
            assertThatThrownBy { CategoryRule(" ", listOf("김밥")) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `키워드가 하나도 없으면 거부한다`() {
            assertThatThrownBy { CategoryRule("식비", emptyList()) }.isInstanceOf(InvariantViolationException::class.java)
        }

        @Test
        fun `빈 키워드는 모든 사용처에 맞으므로 거부한다`() {
            assertThatThrownBy { CategoryRule("식비", listOf("김밥", " ")) }.isInstanceOf(InvariantViolationException::class.java)
        }
    }

    @Nested
    @DisplayName("MerchantCategoryClassifier - 기본 규칙")
    inner class Classifier {

        private val classifier = MerchantCategoryClassifier()

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "아파트관리비_10월OO아파트, 주거·통신",
            "휴대폰알림서비스 10월이용료, 주거·통신",
            "가나이비인후과의원, 보험·건강",
            "OO약국, 보험·건강",
            "카카오T택시_가승인, 교통",
            "OO에너지(주)OO주유소, 교통",
            "(주)OO파크_OO주차장, 교통",
            "메가엠지씨커피OO점, 카페·간식",
            "OO아이스크림할인점 OO점, 카페·간식",
            "돈가스 전문점, 식비",
            "씨유(CU) OO점, 식비",
            "CU(씨유)OO점, 식비",
            "트레이더스 OO점, 식비",
            "주식회사 OO 휴게소, 식비",
            "주식회사 아성다이소, 쇼핑",
            "쿠팡와우 월회비, 구독",
            "쿠팡, 쇼핑",
            "에스케이브로드밴드(주)-000000, 주거·통신",
            "이용알림플러스-10월분, 주거·통신",
            "가나손해보험（주）, 보험·건강",
            "교통-지하철 8건, 교통",
            "쿠팡이츠, 식비",
            "OO버거OO역점, 식비",
            "맥도날드ＯＯＧＳＤＴ, 식비",
            "ＡＫ　ＰＬＡＺＡ（ＯＯ, 쇼핑",
            "지에스２５　ＯＯ점, 식비",
        )
        fun `사용처 키워드로 기본 카테고리를 정한다`(merchant: String, expected: String) {
            assertThat(classifier.categoryNameOf(merchant)).isEqualTo(expected)
        }

        @Test
        fun `맞는 규칙이 없으면 정하지 않는다`() {
            assertThat(classifier.categoryNameOf("가나다상사  주식회사")).isNull()
        }

        @Test
        fun `규칙은 순서대로 보고 처음 맞는 것을 쓴다`() {
            val classifier = MerchantCategoryClassifier(
                listOf(CategoryRule("교통", listOf("카카오T")), CategoryRule("구독", listOf("카카오"))),
            )

            assertThat(classifier.categoryNameOf("카카오T택시")).isEqualTo("교통")
        }
    }

    @Nested
    @DisplayName("CardStatementImporter - 가져오기 계획")
    inner class Planning {

        private val food = CategoryId(1)
        private val uncategorized = CategoryId(13)
        private val categories = listOf(category(name = "식비", id = 1), category(name = "보험·건강", id = 9))
        private val importer = CardStatementImporter(
            MerchantCategoryClassifier(
                listOf(
                    CategoryRule("식비", listOf("김밥")),
                    CategoryRule("보험·건강", listOf("의원")),
                    // 지워졌거나 이름이 바뀐 카테고리를 가리키는 규칙
                    CategoryRule("없는카테고리", listOf("다이소")),
                ),
            ),
        )

        private fun plan(vararg entries: CardStatementEntry): CardImportPlan =
            importer.plan(cardStatement(*entries), categories, uncategorized, emptyList(), emptyList())

        @Test
        fun `일시불은 결제 수단이 카드인 일반 지출이 된다`() {
            // when
            val result = plan(cardEntry(merchant = "OO김밥", amount = 8_000, spentDate = LocalDate.of(2026, 11, 13)))

            // then
            val transaction = result.transactions.single()
            assertThat(transaction.kind).isEqualTo(ExpenseKind.GENERAL)
            assertThat(transaction.categoryId).isEqualTo(food)
            assertThat(transaction.amount).isEqualTo(won(8_000))
            assertThat(transaction.spentDate).isEqualTo(LocalDate.of(2026, 11, 13))
            assertThat(transaction.paymentMethod).isEqualTo("삼성카드")
        }

        @Test
        fun `할부는 소비일 기준의 할부 계획이 된다`() {
            // when
            val result = plan(
                cardEntry(merchant = "가나의원", amount = 240_000, installmentMonths = 2, spentDate = LocalDate.of(2026, 9, 28)),
            )

            // then
            val installmentPlan = result.installmentPlans.single()
            assertThat(installmentPlan.categoryId).isEqualTo(CategoryId(9))
            assertThat(installmentPlan.totalAmount).isEqualTo(won(240_000))
            assertThat(installmentPlan.firstBillingMonth).isEqualTo(YearMonth.of(2026, 9))
            assertThat(installmentPlan.billingDay).isEqualTo(28)
            assertThat(installmentPlan.paymentMethod).isEqualTo("삼성카드")
            assertThat(result.transactions).isEmpty()
        }

        @Test
        fun `맞는 규칙이 없으면 미분류다`() {
            val result = plan(cardEntry(merchant = "가나다상사"))

            assertThat(result.transactions.single().categoryId).isEqualTo(uncategorized)
        }

        @Test
        fun `규칙의 카테고리가 없으면 미분류다`() {
            val result = plan(cardEntry(merchant = "주식회사 아성다이소"))

            assertThat(result.transactions.single().categoryId).isEqualTo(uncategorized)
        }

        @Test
        fun `영속화되지 않은 카테고리는 규칙 대상으로 쓰지 않는다`() {
            val result = importer.plan(
                cardStatement(cardEntry(merchant = "OO김밥")),
                listOf(category(name = "식비", id = null)),
                uncategorized,
                emptyList(),
                emptyList(),
            )

            assertThat(result.transactions.single().categoryId).isEqualTo(uncategorized)
        }

        @Test
        fun `미분류로 들어간 구매를 일시불과 할부에서 함께 모은다`() {
            // when
            val result = plan(
                cardEntry(approvalNumber = "1", merchant = "OO김밥"),
                cardEntry(approvalNumber = "2", merchant = "가나다상사"),
                cardEntry(approvalNumber = "3", merchant = "라마바상점", amount = 60_000, installmentMonths = 3),
            )

            // then
            assertThat(result.uncategorized.map { it.merchant }).containsExactly("가나다상사", "라마바상점")
        }

        @Test
        fun `취소된 승인은 사유와 함께 처리 안 됨에 담는다`() {
            // when
            val result = plan(
                cardEntry(approvalNumber = "1", merchant = "카카오T택시", amount = 12_000),
                cardEntry(approvalNumber = "1", merchant = "카카오T택시", amount = -12_000),
            )

            // then
            val unprocessed = result.unprocessed.single()
            assertThat(unprocessed.merchant).isEqualTo("카카오T택시")
            assertThat(unprocessed.amount).isEqualTo(12_000)
            assertThat(unprocessed.reason).contains("취소")
            assertThat(result.transactions).isEmpty()
        }

        @Test
        fun `할부 계획 규칙에 어긋나는 구매는 그 구매만 빼고 사유를 남긴다`() {
            // when: 61개월 할부는 등록할 수 없다
            val result = plan(
                cardEntry(approvalNumber = "1", merchant = "가전", amount = 6_100_000, installmentMonths = 61),
                cardEntry(approvalNumber = "2", merchant = "OO김밥"),
            )

            // then
            assertThat(result.unprocessed.single().reason).contains("60개월 이하")
            assertThat(result.installmentPlans).isEmpty()
            assertThat(result.transactions.map { it.merchant }).containsExactly("OO김밥")
        }

        @Test
        fun `처리하지 못한 미분류 구매는 미분류로 세지 않는다`() {
            val result = plan(cardEntry(merchant = "가나다상사", amount = 2, installmentMonths = 3))

            assertThat(result.uncategorized).isEmpty()
            assertThat(result.unprocessed).hasSize(1)
        }
    }

    @Nested
    @DisplayName("CardStatementImporter - 중복 판단")
    inner class Duplicates {

        private val uncategorized = CategoryId(13)
        private val importer = CardStatementImporter(MerchantCategoryClassifier(emptyList()))
        private val day = LocalDate.of(2026, 11, 12)

        @Test
        fun `소비일·사용처·금액이 같은 거래가 있으면 건너뛴다`() {
            // given
            val existing = generalTransaction(merchant = "OO프레시", amount = 30_000, spentDate = day, paymentMethod = "삼성카드")

            // when
            val result = importer.plan(
                cardStatement(cardEntry(merchant = "OO프레시", amount = 30_000, spentDate = day)),
                emptyList(), uncategorized, listOf(existing), emptyList(),
            )

            // then
            assertThat(result.transactions).isEmpty()
            assertThat(result.duplicates).hasSize(1)
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
            "소비일이 다르면, 2026-11-13, OO프레시, 30000",
            "사용처가 다르면, 2026-11-12, OO프레시2, 30000",
            "금액이 다르면, 2026-11-12, OO프레시, 30001",
        )
        fun `내용이 하나라도 다르면 새로 만든다`(case: String, spentDate: LocalDate, merchant: String, amount: Long) {
            // given
            val existing = generalTransaction(merchant = "OO프레시", amount = 30_000, spentDate = day, paymentMethod = "삼성카드")

            // when
            val result = importer.plan(
                cardStatement(cardEntry(merchant = merchant, amount = amount, spentDate = spentDate)),
                emptyList(), uncategorized, listOf(existing), emptyList(),
            )

            // then
            assertThat(result.transactions).hasSize(1)
        }

        @Test
        fun `결제 수단이 다르면 같은 내용이어도 새로 만든다`() {
            // given: 같은 날 같은 가게에서 같은 금액을 다른 카드로 냈다
            val existing = generalTransaction(merchant = "OO프레시", amount = 30_000, spentDate = day, paymentMethod = "우리카드")

            // when
            val result = importer.plan(
                cardStatement(cardEntry(merchant = "OO프레시", amount = 30_000, spentDate = day)),
                emptyList(), uncategorized, listOf(existing), emptyList(),
            )

            // then
            assertThat(result.transactions).hasSize(1)
        }

        @Test
        fun `결제 수단이 다른 할부는 새로 만든다`() {
            // given
            val existing = installmentPlan(
                merchant = "가나의원", totalAmount = 240_000, installmentMonths = 2, firstBillingMonth = YearMonth.of(2026, 9),
                paymentMethod = "우리카드",
            )

            // when
            val result = importer.plan(
                cardStatement(cardEntry(merchant = "가나의원", amount = 240_000, installmentMonths = 2, spentDate = LocalDate.of(2026, 9, 28))),
                emptyList(), uncategorized, emptyList(), listOf(existing),
            )

            // then
            assertThat(result.installmentPlans).hasSize(1)
        }

        @Test
        fun `같은 내용이 여러 번이면 이미 있는 개수만큼만 건너뛴다`() {
            // given: 같은 날 같은 가게에서 같은 금액을 세 번 샀고, 하나는 이미 있다
            val entries = (1..3).map { cardEntry(approvalNumber = "$it", merchant = "OO프레시", amount = 3_000, spentDate = day) }
            val existing = generalTransaction(merchant = "OO프레시", amount = 3_000, spentDate = day, paymentMethod = "삼성카드")

            // when
            val result = importer.plan(cardStatement(*entries.toTypedArray()), emptyList(), uncategorized, listOf(existing), emptyList())

            // then
            assertThat(result.transactions).hasSize(2)
            assertThat(result.duplicates).hasSize(1)
        }

        @Test
        fun `처음 가져올 때는 같은 내용도 모두 만든다`() {
            val entries = (1..2).map { cardEntry(approvalNumber = "$it", merchant = "OO프레시", amount = 3_000, spentDate = day) }

            val result = importer.plan(cardStatement(*entries.toTypedArray()), emptyList(), uncategorized, emptyList(), emptyList())

            assertThat(result.transactions).hasSize(2)
        }

        @Test
        fun `사용처·총액·개월 수·첫 납부월이 같은 할부 계획이 있으면 건너뛴다`() {
            // given
            val existing = installmentPlan(
                merchant = "가나의원", totalAmount = 240_000, installmentMonths = 2, firstBillingMonth = YearMonth.of(2026, 9),
                paymentMethod = "삼성카드",
            )

            // when
            val result = importer.plan(
                cardStatement(cardEntry(merchant = "가나의원", amount = 240_000, installmentMonths = 2, spentDate = LocalDate.of(2026, 9, 28))),
                emptyList(), uncategorized, emptyList(), listOf(existing),
            )

            // then
            assertThat(result.installmentPlans).isEmpty()
            assertThat(result.duplicates).hasSize(1)
        }

        @Test
        fun `첫 납부월이 다른 할부는 새로 만든다`() {
            // given
            val existing = installmentPlan(
                merchant = "가나의원", totalAmount = 240_000, installmentMonths = 2, firstBillingMonth = YearMonth.of(2026, 10),
                paymentMethod = "삼성카드",
            )

            // when
            val result = importer.plan(
                cardStatement(cardEntry(merchant = "가나의원", amount = 240_000, installmentMonths = 2, spentDate = LocalDate.of(2026, 9, 28))),
                emptyList(), uncategorized, emptyList(), listOf(existing),
            )

            // then
            assertThat(result.installmentPlans).hasSize(1)
        }
    }

    @Nested
    @DisplayName("CardStatementImporter - 고정비 (PRD 3.5)")
    inner class FixedExpenses {

        private val uncategorized = CategoryId(13)
        private val importer = CardStatementImporter(MerchantCategoryClassifier(listOf(CategoryRule("식비", listOf("보험")))))
        private val insurance = fixedExpense(
            merchant = "OO보험", amount = 15_000, dayOfMonth = 25, startMonth = YearMonth.of(2026, 9), categoryId = 9,
            paymentMethod = "삼성카드", id = 3,
        )

        private fun plan(existing: List<Transaction>, vararg entries: CardStatementEntry): CardImportPlan =
            importer.plan(cardStatement(*entries), emptyList(), uncategorized, existing, emptyList(), listOf(insurance))

        private fun insurancePaid(day: LocalDate, amount: Long = 15_000, approvalNumber: String = "1") =
            cardEntry(approvalNumber = approvalNumber, merchant = "ＯＯ 보험", amount = amount, spentDate = day)

        @Test
        fun `고정비와 사용처가 같으면 카드의 날짜·금액과 고정비의 카테고리로 고정비 거래를 만든다`() {
            // when
            val result = plan(emptyList(), insurancePaid(LocalDate.of(2026, 10, 27), amount = 15_920))

            // then
            val occurrence = result.fixedExpenseOccurrences.single()
            assertThat(occurrence.kind).isEqualTo(ExpenseKind.FIXED)
            assertThat(occurrence.fixedExpenseId).isEqualTo(FixedExpenseId(3))
            assertThat(occurrence.spentDate).isEqualTo(LocalDate.of(2026, 10, 27))
            assertThat(occurrence.amount).isEqualTo(won(15_920))
            assertThat(occurrence.categoryId).isEqualTo(CategoryId(9))
            assertThat(result.transactions).isEmpty()
            assertThat(result.uncategorized).isEmpty()
            assertThat(result.fixedExpenseMatches).hasSize(1)
        }

        @Test
        fun `그 달에 자동 기록만 있으면 그 거래를 실제값으로 고친다`() {
            // given
            val scheduled = insurance.occurrenceIn(YearMonth.of(2026, 10)).assignId(TransactionId(40))

            // when
            val result = plan(listOf(scheduled), insurancePaid(LocalDate.of(2026, 10, 27), amount = 15_920))

            // then
            val corrected = result.fixedExpenseCorrections.single()
            assertThat(corrected.id).isEqualTo(TransactionId(40))
            assertThat(corrected.spentDate).isEqualTo(LocalDate.of(2026, 10, 27))
            assertThat(corrected.amount).isEqualTo(won(15_920))
            assertThat(result.fixedExpenseOccurrences).isEmpty()
            assertThat(result.fixedExpenseMatches).hasSize(1)
        }

        @Test
        fun `그 달에 이미 실제값의 고정비 거래가 있으면 일반 지출로 넣는다`() {
            // given
            val actual = insurance.actualOccurrence(LocalDate.of(2026, 10, 27), won(15_920)).assignId(TransactionId(40))

            // when
            val result = plan(listOf(actual), insurancePaid(LocalDate.of(2026, 10, 20), amount = 3_000))

            // then
            assertThat(result.transactions.single().kind).isEqualTo(ExpenseKind.GENERAL)
            assertThat(result.fixedExpenseMatches).isEmpty()
            assertThat(result.fixedExpenseCorrections).isEmpty()
        }

        @Test
        fun `한 달에 같은 사용처가 둘이면 먼저 나온 것만 고정비 거래다`() {
            val result = plan(
                emptyList(),
                insurancePaid(LocalDate.of(2026, 10, 25), approvalNumber = "1"),
                insurancePaid(LocalDate.of(2026, 10, 26), approvalNumber = "2", amount = 9_000),
                insurancePaid(LocalDate.of(2026, 11, 25), approvalNumber = "3"),
            )

            assertThat(result.fixedExpenseOccurrences.map { it.spentDate })
                .containsExactly(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25))
            assertThat(result.transactions.map { it.spentDate }).containsExactly(LocalDate.of(2026, 10, 26))
        }

        @Test
        fun `고정비 기간 밖의 달이면 일반 지출이다`() {
            val result = plan(emptyList(), insurancePaid(LocalDate.of(2026, 8, 25)))

            assertThat(result.transactions.single().kind).isEqualTo(ExpenseKind.GENERAL)
            assertThat(result.fixedExpenseMatches).isEmpty()
        }

        @Test
        fun `할부는 고정비로 보지 않는다`() {
            val result = plan(
                emptyList(),
                cardEntry(merchant = "OO보험", amount = 30_000, installmentMonths = 3, spentDate = LocalDate.of(2026, 10, 25)),
            )

            assertThat(result.installmentPlans).hasSize(1)
            assertThat(result.fixedExpenseMatches).isEmpty()
        }

        @Test
        fun `자동 기록과 똑같은 구매는 중복이고 같은 달의 다른 구매가 그 기록을 고치지 않는다`() {
            // given
            val scheduled = insurance.occurrenceIn(YearMonth.of(2026, 10)).assignId(TransactionId(40))

            // when
            val result = plan(
                listOf(scheduled),
                cardEntry(approvalNumber = "1", merchant = "OO보험", amount = 15_000, spentDate = LocalDate.of(2026, 10, 25)),
                insurancePaid(LocalDate.of(2026, 10, 26), approvalNumber = "2", amount = 9_000),
            )

            // then
            assertThat(result.duplicates).hasSize(1)
            assertThat(result.fixedExpenseCorrections).isEmpty()
            assertThat(result.transactions.single().amount).isEqualTo(won(9_000))
        }

        @Test
        fun `고정비가 아닌 사용처의 중복은 고정비 판단에 영향이 없다`() {
            // given
            val existing = Transaction.createGeneral(CategoryId(1), won(8_000), "OO김밥", LocalDate.of(2026, 10, 10), paymentMethod = "삼성카드")

            // when
            val result = plan(
                listOf(existing),
                cardEntry(approvalNumber = "1", merchant = "OO김밥", spentDate = LocalDate.of(2026, 10, 10)),
                insurancePaid(LocalDate.of(2026, 10, 25), approvalNumber = "2"),
            )

            // then
            assertThat(result.duplicates).hasSize(1)
            assertThat(result.fixedExpenseOccurrences).hasSize(1)
        }
    }
}
