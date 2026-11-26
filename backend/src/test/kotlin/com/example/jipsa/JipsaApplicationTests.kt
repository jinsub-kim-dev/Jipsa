package com.example.jipsa

import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.support.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 전체 애플리케이션 컨텍스트 스모크 테스트.
 *
 * `@SpringBootTest` 는 최소 개수만 유지한다 (backend/CLAUDE.md 5.2).
 * 시드 데이터(`db/seed`)를 포함한 전체 마이그레이션이 적용되는 유일한 지점이므로,
 * 시드가 도메인 불변식을 만족하는지도 여기서 검증한다. 매퍼가 복원하며 불변식을 다시 검사한다.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@Transactional
@DisplayName("애플리케이션 컨텍스트")
class JipsaApplicationTests {

    @field:Autowired
    private lateinit var categoryRepository: CategoryRepository

    @field:Autowired
    private lateinit var transactionRepository: TransactionRepository

    @field:Autowired
    private lateinit var fixedExpenseRepository: FixedExpenseRepository

    @field:Autowired
    private lateinit var installmentPlanRepository: InstallmentPlanRepository

    @Test
    fun `컨텍스트가 정상적으로 구성된다`() {
        // 빈 구성 오류, 순환 참조, JPA 매핑 불일치가 있으면 여기서 실패한다.
    }

    @Test
    fun `시드 데이터가 도메인 불변식을 만족한 상태로 적재된다`() {
        // when: 매퍼가 도메인으로 복원하는 과정에서 불변식이 검증된다
        val transactions = transactionRepository.search(
            TransactionSearchCriteria(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 30)),
        )

        // then
        assertThat(categoryRepository.findAll().map { it.name }).contains("식비", "주거·통신", "구독")
        assertThat(transactions.map { it.kind }.toSet()).containsExactlyInAnyOrder(*ExpenseKind.entries.toTypedArray())
        assertThat(fixedExpenseRepository.findAll()).isNotEmpty()
    }

    @Test
    fun `시드 할부의 회차 합계는 총액과 같다`() {
        val all = transactionRepository.search(TransactionSearchCriteria(LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31)))

        installmentPlanRepository.findAll().forEach { plan ->
            val parts = all.filter { it.installmentPlanId == plan.id }
            assertThat(parts).hasSize(plan.installmentMonths)
            assertThat(Money.sum(parts.map { it.amount })).isEqualTo(plan.totalAmount)
        }
    }
}
