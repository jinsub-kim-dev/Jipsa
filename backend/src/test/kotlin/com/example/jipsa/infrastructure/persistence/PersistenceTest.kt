package com.example.jipsa.infrastructure.persistence

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tag
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.support.AbstractPersistenceTest
import com.example.jipsa.support.AbstractPersistenceTest.Companion.TEST_PREFIX
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("영속성 - 실제 MySQL")
class PersistenceTest : AbstractPersistenceTest() {

    private val november = YearMonth.of(2026, 11)

    private fun general(
        categoryId: CategoryId,
        merchant: String,
        amount: Long,
        date: LocalDate,
        vararg tags: String,
        memo: String? = null,
    ): Transaction = transactionRepository.save(
        Transaction.createGeneral(categoryId, Money.of(amount), merchant, date, Tags.of(*tags), memo, "신한카드"),
    )

    @Nested
    @DisplayName("카테고리")
    inner class Categories {

        @Test
        fun `저장하고 다시 읽으면 정보가 유실되지 않는다`() {
            val id = savedCategory("카페·간식")
            flushAndClear()

            val found = categoryRepository.findById(id)!!

            assertThat(found.name).isEqualTo("T-카페·간식")
            assertThat(found.icon).isEqualTo("🍚")
            assertThat(found.color).isEqualTo("#EF4444")
            assertThat(categoryRepository.existsByName("T-카페·간식")).isTrue()
        }

        @Test
        fun `수정은 같은 행을 갱신한다`() {
            val id = savedCategory()
            categoryRepository.save(categoryRepository.findById(id)!!.rename("외식"))
            flushAndClear()

            assertThat(categoryRepository.findAll().map(Category::name)).contains("외식").doesNotContain("T-식비")
        }

        @Test
        fun `기본 카테고리는 시드 없이 마이그레이션만으로 들어간다`() {
            // 배포 환경은 시드를 적용하지 않는다. 카테고리가 없으면 지출을 등록할 수 없다
            val defaults = categoryRepository.findAll().filterNot { it.name.startsWith(TEST_PREFIX) }

            assertThat(defaults).hasSize(12)
            assertThat(defaults.first().name).isEqualTo("식비")
            assertThat(defaults.map { it.id!!.value }).isEqualTo((1L..12L).toList())
        }
    }

    @Nested
    @DisplayName("거래")
    inner class Transactions {

        @Test
        fun `태그 순서와 메모까지 왕복한다`() {
            // given
            val food = savedCategory()
            val saved = general(food, "김밥천국", 9_000, november.atDay(26), "점심", "혼밥", memo = "메모")
            flushAndClear()

            // when
            val found = transactionRepository.findById(saved.id!!)!!

            // then
            assertThat(found.kind).isEqualTo(ExpenseKind.GENERAL)
            assertThat(found.tags.names).containsExactly("점심", "혼밥")
            assertThat(found.memo).isEqualTo("메모")
            assertThat(found.paymentMethod).isEqualTo("신한카드")
        }

        @Test
        fun `태그를 바꾸면 기존 태그를 대체한다`() {
            val food = savedCategory()
            val saved = general(food, "김밥천국", 9_000, november.atDay(26), "점심", "혼밥")
            flushAndClear()

            transactionRepository.save(transactionRepository.findById(saved.id!!)!!.retag(Tags.of("저녁")))
            flushAndClear()

            assertThat(transactionRepository.findById(saved.id!!)!!.tags.names).containsExactly("저녁")
        }

        @Test
        fun `검색은 기간과 조건을 함께 적용하고 최근 것부터 준다`() {
            // given
            val food = savedCategory("식비")
            val cafe = savedCategory("카페")
            general(food, "김밥천국", 9_000, november.atDay(3), "점심")
            general(cafe, "스타벅스", 5_000, november.atDay(20), "커피")
            general(food, "배달의민족", 25_000, november.atDay(20), "배달", "야식", memo = "치킨")
            general(food, "지난달 점심", 8_000, LocalDate.of(2026, 10, 31), "점심")
            flushAndClear()
            val month = TransactionSearchCriteria(november.atDay(1), november.atEndOfMonth())

            // when & then
            assertThat(transactionRepository.search(month).map(Transaction::merchant))
                .containsExactly("배달의민족", "스타벅스", "김밥천국")
            assertThat(transactionRepository.search(month.copy(categoryId = cafe)).map(Transaction::merchant))
                .containsExactly("스타벅스")
            assertThat(transactionRepository.search(month.copy(tag = Tag.of("점심"))).map(Transaction::merchant))
                .containsExactly("김밥천국")
            assertThat(transactionRepository.search(month.copy(keyword = "치킨")).map(Transaction::merchant))
                .containsExactly("배달의민족")
            assertThat(transactionRepository.search(month.copy(keyword = "야")).map(Transaction::merchant))
                .containsExactly("배달의민족")
            assertThat(transactionRepository.search(month.copy(keyword = "  ")).map(Transaction::merchant)).hasSize(3)
            assertThat(transactionRepository.search(month.copy(kind = ExpenseKind.FIXED))).isEmpty()
        }

        @Test
        fun `삭제하면 태그도 함께 사라진다`() {
            val food = savedCategory()
            val saved = general(food, "김밥천국", 9_000, november.atDay(1), "점심")
            flushAndClear()

            transactionRepository.deleteById(saved.id!!)
            flushAndClear()

            assertThat(transactionRepository.findById(saved.id!!)).isNull()
            assertThat(statisticsQuery.tagTotals(november.atDay(1), november.atEndOfMonth(), 10)).isEmpty()
        }

        @Test
        fun `카테고리 사용 여부를 확인한다`() {
            val food = savedCategory("식비")
            val unused = savedCategory("미사용")
            general(food, "김밥천국", 9_000, november.atDay(1))

            assertThat(transactionRepository.existsByCategoryId(food)).isTrue()
            assertThat(transactionRepository.existsByCategoryId(unused)).isFalse()
        }
    }

    @Nested
    @DisplayName("고정비")
    inner class FixedExpenses {

        @Test
        fun `연월과 종료월까지 왕복하고 기록 현황을 판단한다`() {
            // given
            val housing = savedCategory("주거")
            val rent = fixedExpenseRepository.save(
                FixedExpense.create(housing, "월세", Money.of(650_000), 31, YearMonth.of(2026, 6), Tags.of("집")),
            )
            val terminated = fixedExpenseRepository.save(rent.terminate(YearMonth.of(2027, 2)))
            transactionRepository.saveAll(listOf(terminated.occurrenceIn(november)))
            flushAndClear()

            // when
            val found = fixedExpenseRepository.findById(rent.id!!)!!

            // then
            assertThat(found.startMonth).isEqualTo(YearMonth.of(2026, 6))
            assertThat(found.endMonth).isEqualTo(YearMonth.of(2027, 2))
            assertThat(found.tags.names).containsExactly("집")
            assertThat(transactionRepository.findRecordedFixedExpenseIds(november)).containsExactly(rent.id)
            assertThat(transactionRepository.findRecordedFixedExpenseIds(november.plusMonths(1))).isEmpty()
            assertThat(fixedExpenseRepository.existsByCategoryId(housing)).isTrue()
        }

        @Test
        fun `종료하면 마지막 달 이후의 기록만 지운다`() {
            // given
            val housing = savedCategory("주거")
            val rent = fixedExpenseRepository.save(
                FixedExpense.create(housing, "월세", Money.of(650_000), 1, YearMonth.of(2026, 10)),
            )
            transactionRepository.saveAll(listOf(november.minusMonths(1), november).map(rent::occurrenceIn))
            flushAndClear()

            // when
            transactionRepository.deleteAllByFixedExpenseIdAndSpentDateAfter(rent.id!!, LocalDate.of(2026, 10, 31))
            flushAndClear()

            // then
            assertThat(transactionRepository.findRecordedFixedExpenseIds(november.minusMonths(1))).containsExactly(rent.id)
            assertThat(transactionRepository.findRecordedFixedExpenseIds(november)).isEmpty()
        }
    }

    @Nested
    @DisplayName("할부")
    inner class InstallmentPlans {

        @Test
        fun `계획과 회차가 왕복하고 해지하면 회차가 모두 사라진다`() {
            // given
            val shopping = savedCategory("쇼핑")
            val plan = installmentPlanRepository.save(
                InstallmentPlan.create(shopping, "겨울 패딩", Money.of(1_000_000), 3, november, 31, Tags.of("의류")),
            )
            val parts = transactionRepository.saveAll(plan.scheduleParts())
            flushAndClear()

            // then: 왕복
            val found = installmentPlanRepository.findById(plan.id!!)!!
            assertThat(found.firstBillingMonth).isEqualTo(november)
            assertThat(found.installmentAmounts).containsExactly(Money.of(333_334), Money.of(333_333), Money.of(333_333))
            assertThat(transactionRepository.findById(parts[1].id!!)!!.spentDate).isEqualTo(LocalDate.of(2026, 12, 31))
            assertThat(installmentPlanRepository.findAll()).hasSize(1)
            assertThat(installmentPlanRepository.existsByCategoryId(shopping)).isTrue()

            // when: 해지
            transactionRepository.deleteAllByInstallmentPlanId(plan.id!!)
            installmentPlanRepository.deleteById(plan.id!!)
            flushAndClear()

            // then
            assertThat(parts.map { transactionRepository.findById(it.id!!) }).containsOnlyNulls()
            assertThat(installmentPlanRepository.findById(plan.id!!)).isNull()
        }
    }

    @Nested
    @DisplayName("통계 조회")
    inner class Statistics {

        @Test
        fun `월별 종류 합계는 빈 달을 0원으로 채운다`() {
            // given
            val food = savedCategory()
            val housing = savedCategory("주거")
            general(food, "점심", 10_000, LocalDate.of(2026, 9, 30))
            general(food, "점심", 20_000, november.atDay(1))
            val rent = fixedExpenseRepository.save(FixedExpense.create(housing, "월세", Money.of(650_000), 1, november))
            transactionRepository.saveAll(listOf(rent.occurrenceIn(november)))
            flushAndClear()

            // when
            val result = statisticsQuery.monthlyKindAmounts(YearMonth.of(2026, 9), november)

            // then
            assertThat(result.map { it.month }).containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), november)
            assertThat(result.map { it.amounts.total.amount }).containsExactly(10_000L, 0L, 670_000L)
            assertThat(result[2].amounts.fixed).isEqualTo(Money.of(650_000))
            assertThat(result[2].amounts.general).isEqualTo(Money.of(20_000))
        }

        @Test
        fun `카테고리별 월 합계를 낸다`() {
            val food = savedCategory("식비")
            val cafe = savedCategory("카페")
            general(food, "점심", 10_000, november.atDay(1))
            general(food, "저녁", 15_000, november.atDay(30))
            general(cafe, "커피", 5_000, november.atDay(2))
            general(cafe, "커피", 4_000, LocalDate.of(2026, 12, 1))
            flushAndClear()

            val result = statisticsQuery.monthlyCategoryExpenses(november, november).associate { it.categoryId to it.amount }

            assertThat(result).containsEntry(food, Money.of(25_000)).containsEntry(cafe, Money.of(5_000)).hasSize(2)
        }

        @Test
        fun `태그 합계는 거래를 태그마다 중복 집계하고 금액순으로 자른다`() {
            // given
            val food = savedCategory()
            general(food, "회식", 80_000, november.atDay(5), "회식", "저녁")
            general(food, "저녁", 20_000, november.atDay(6), "저녁")
            general(food, "점심", 9_000, november.atDay(7), "점심")
            flushAndClear()

            // when
            val result = statisticsQuery.tagTotals(november.atDay(1), november.atEndOfMonth(), 2)

            // then: 저녁 100,000(2건) > 회식 80,000(1건). 점심은 잘린다
            assertThat(result.map { it.tag.name }).containsExactly("저녁", "회식")
            assertThat(result[0].amount).isEqualTo(Money.of(100_000))
            assertThat(result[0].transactionCount).isEqualTo(2)
        }
    }

    @Test
    fun `식별자로 저장하려는 거래가 DB 에 없으면 실패한다`() {
        val food = savedCategory()
        val ghost = Transaction.createGeneral(food, Money.of(1), "유령", november.atDay(1)).assignId(TransactionId(999_999))

        org.assertj.core.api.Assertions.assertThatThrownBy { transactionRepository.save(ghost) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
