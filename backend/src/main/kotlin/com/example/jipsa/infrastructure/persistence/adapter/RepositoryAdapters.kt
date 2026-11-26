package com.example.jipsa.infrastructure.persistence.adapter

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.FixedExpense
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
import com.example.jipsa.infrastructure.persistence.jpa.CategoryJpaRepository
import com.example.jipsa.infrastructure.persistence.jpa.FixedExpenseJpaRepository
import com.example.jipsa.infrastructure.persistence.jpa.InstallmentPlanJpaRepository
import com.example.jipsa.infrastructure.persistence.jpa.TransactionJpaRepository
import com.example.jipsa.infrastructure.persistence.mapper.CategoryMapper
import com.example.jipsa.infrastructure.persistence.mapper.FixedExpenseMapper
import com.example.jipsa.infrastructure.persistence.mapper.InstallmentPlanMapper
import com.example.jipsa.infrastructure.persistence.mapper.TransactionMapper
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.YearMonth

/*
 * 아웃바운드 포트의 JPA 구현체들. 도메인은 이 클래스들을 알지 못한다. 스프링이 포트 타입으로 주입한다.
 *
 * 저장 규칙은 공통이다. 식별자가 있으면 영속 엔티티를 읽어 상태를 반영하고(더티 체킹으로 UPDATE),
 * 없으면 새 엔티티를 만든다.
 */

private fun missing(name: String, id: Any): Nothing =
    throw IllegalStateException("존재하지 않는 ${name}을(를) 저장하려 했습니다. id=$id")

@Repository
class CategoryRepositoryAdapter(
    private val jpaRepository: CategoryJpaRepository,
) : CategoryRepository {

    override fun save(category: Category): Category {
        val entity = category.id
            ?.let { id ->
                jpaRepository.findById(id.value).orElse(null)?.also { CategoryMapper.applyTo(it, category) }
                    ?: missing("카테고리", id)
            }
            ?: CategoryMapper.toEntity(category)
        return CategoryMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: CategoryId): Category? =
        jpaRepository.findById(id.value).map(CategoryMapper::toDomain).orElse(null)

    override fun findAll(): List<Category> = jpaRepository.findAllByOrderByIdAsc().map(CategoryMapper::toDomain)

    override fun existsByName(name: String): Boolean = jpaRepository.existsByName(name)

    override fun deleteById(id: CategoryId) = jpaRepository.deleteById(id.value)
}

@Repository
class TransactionRepositoryAdapter(
    private val jpaRepository: TransactionJpaRepository,
) : TransactionRepository {

    override fun save(transaction: Transaction): Transaction {
        val entity = transaction.id
            ?.let { id ->
                jpaRepository.findById(id.value).orElse(null)?.also { TransactionMapper.applyTo(it, transaction) }
                    ?: missing("거래", id)
            }
            ?: TransactionMapper.toEntity(transaction)
        return TransactionMapper.toDomain(jpaRepository.save(entity))
    }

    /** 새 거래만 받는다. 고정비 기록과 할부 회차 생성에 쓴다. */
    override fun saveAll(transactions: List<Transaction>): List<Transaction> =
        jpaRepository.saveAll(transactions.map(TransactionMapper::toEntity)).map(TransactionMapper::toDomain)

    override fun findById(id: TransactionId): Transaction? =
        jpaRepository.findById(id.value).map(TransactionMapper::toDomain).orElse(null)

    override fun search(criteria: TransactionSearchCriteria): List<Transaction> = jpaRepository
        .search(
            from = criteria.from,
            to = criteria.to,
            kind = criteria.kind,
            categoryId = criteria.categoryId?.value,
            tag = criteria.tag?.name,
            keyword = criteria.keyword?.trim()?.takeIf(String::isNotEmpty)?.let { "%${it.lowercase()}%" },
        )
        .map(TransactionMapper::toDomain)

    override fun findAllByKind(kind: ExpenseKind): List<Transaction> =
        jpaRepository.findAllByKindOrderBySpentDateAscIdAsc(kind).map(TransactionMapper::toDomain)

    override fun deleteById(id: TransactionId) = jpaRepository.deleteById(id.value)

    override fun deleteAllByInstallmentPlanId(installmentPlanId: InstallmentPlanId) {
        jpaRepository.deleteAllByInstallmentPlanId(installmentPlanId.value)
    }

    override fun deleteAllByFixedExpenseIdAndSpentDateAfter(fixedExpenseId: FixedExpenseId, date: LocalDate) {
        jpaRepository.deleteAllByFixedExpenseIdAndSpentDateAfter(fixedExpenseId.value, date)
    }

    override fun existsByCategoryId(categoryId: CategoryId): Boolean = jpaRepository.existsByCategoryId(categoryId.value)

    override fun findRecordedFixedExpenseIds(month: YearMonth): Set<FixedExpenseId> = jpaRepository
        .findFixedExpenseIdsBetween(month.atDay(1), month.atEndOfMonth())
        .map(::FixedExpenseId)
        .toSet()
}

@Repository
class FixedExpenseRepositoryAdapter(
    private val jpaRepository: FixedExpenseJpaRepository,
) : FixedExpenseRepository {

    override fun save(fixedExpense: FixedExpense): FixedExpense {
        val entity = fixedExpense.id
            ?.let { id ->
                jpaRepository.findById(id.value).orElse(null)?.also { FixedExpenseMapper.applyTo(it, fixedExpense) }
                    ?: missing("고정비", id)
            }
            ?: FixedExpenseMapper.toEntity(fixedExpense)
        return FixedExpenseMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: FixedExpenseId): FixedExpense? =
        jpaRepository.findById(id.value).map(FixedExpenseMapper::toDomain).orElse(null)

    override fun findAll(): List<FixedExpense> =
        jpaRepository.findAllByOrderByDayOfMonthAscIdAsc().map(FixedExpenseMapper::toDomain)

    override fun existsByCategoryId(categoryId: CategoryId): Boolean = jpaRepository.existsByCategoryId(categoryId.value)
}

@Repository
class InstallmentPlanRepositoryAdapter(
    private val jpaRepository: InstallmentPlanJpaRepository,
) : InstallmentPlanRepository {

    /** 할부 계획은 등록 후 바뀌지 않는다(바꾸려면 해지 후 다시 등록). 새 계획만 받는다. */
    override fun save(plan: InstallmentPlan): InstallmentPlan =
        InstallmentPlanMapper.toDomain(jpaRepository.save(InstallmentPlanMapper.toEntity(plan)))

    override fun findById(id: InstallmentPlanId): InstallmentPlan? =
        jpaRepository.findById(id.value).map(InstallmentPlanMapper::toDomain).orElse(null)

    override fun findAll(): List<InstallmentPlan> =
        jpaRepository.findAllByOrderByFirstBillingMonthAscIdAsc().map(InstallmentPlanMapper::toDomain)

    override fun existsByCategoryId(categoryId: CategoryId): Boolean = jpaRepository.existsByCategoryId(categoryId.value)

    override fun deleteById(id: InstallmentPlanId) = jpaRepository.deleteById(id.value)
}
