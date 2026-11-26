package com.example.jipsa.infrastructure.persistence.mapper

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.infrastructure.persistence.entity.CategoryJpaEntity
import com.example.jipsa.infrastructure.persistence.entity.FixedExpenseJpaEntity
import com.example.jipsa.infrastructure.persistence.entity.InstallmentPlanJpaEntity
import com.example.jipsa.infrastructure.persistence.entity.TransactionJpaEntity
import java.time.LocalDate
import java.time.YearMonth

/*
 * 도메인 모델과 JPA 엔티티 사이의 변환 (backend/CLAUDE.md 3.2).
 * 도메인으로 복원할 때 불변식이 다시 검증되므로, DB 에 잘못된 값이 있으면 여기서 드러난다.
 */

private const val NOT_PERSISTED = "영속화되지 않은 엔티티는 도메인으로 변환할 수 없습니다."

private fun YearMonth.toFirstDay(): LocalDate = atDay(1)

private fun LocalDate.toYearMonth(): YearMonth = YearMonth.from(this)

/** 엔티티의 태그 목록을 도메인 상태로 교체한다. 같은 컬렉션 인스턴스를 유지해야 Hibernate 가 변경을 추적한다. */
private fun MutableList<String>.replaceWith(tags: Tags) {
    clear()
    addAll(tags.names)
}

object CategoryMapper {

    fun toDomain(entity: CategoryJpaEntity): Category = Category.reconstitute(
        id = CategoryId(requireNotNull(entity.id) { NOT_PERSISTED }),
        name = entity.name,
        icon = entity.icon,
        color = entity.color,
    )

    fun toEntity(category: Category): CategoryJpaEntity = CategoryJpaEntity(
        id = category.id?.value,
        name = category.name,
        icon = category.icon,
        color = category.color,
    )

    fun applyTo(entity: CategoryJpaEntity, category: Category) {
        entity.name = category.name
        entity.icon = category.icon
        entity.color = category.color
    }
}

object TransactionMapper {

    fun toDomain(entity: TransactionJpaEntity): Transaction = Transaction.reconstitute(
        id = TransactionId(requireNotNull(entity.id) { NOT_PERSISTED }),
        kind = entity.kind,
        categoryId = CategoryId(entity.categoryId),
        amount = Money.of(entity.amount),
        merchant = entity.merchant,
        spentDate = entity.spentDate,
        tags = Tags.of(entity.tags),
        memo = entity.memo,
        paymentMethod = entity.paymentMethod,
        fixedExpenseId = entity.fixedExpenseId?.let(::FixedExpenseId),
        installmentPlanId = entity.installmentPlanId?.let(::InstallmentPlanId),
        installmentSequence = entity.installmentSequence,
    )

    fun toEntity(transaction: Transaction): TransactionJpaEntity = TransactionJpaEntity(
        id = transaction.id?.value,
        kind = transaction.kind,
        categoryId = transaction.categoryId.value,
        amount = transaction.amount.amount,
        merchant = transaction.merchant,
        spentDate = transaction.spentDate,
        memo = transaction.memo,
        paymentMethod = transaction.paymentMethod,
        fixedExpenseId = transaction.fixedExpenseId?.value,
        installmentPlanId = transaction.installmentPlanId?.value,
        installmentSequence = transaction.installmentSequence,
        tags = transaction.tags.names.toMutableList(),
    )

    /** 종류와 출처는 바뀌지 않으므로 반영하지 않는다. */
    fun applyTo(entity: TransactionJpaEntity, transaction: Transaction) {
        entity.categoryId = transaction.categoryId.value
        entity.amount = transaction.amount.amount
        entity.merchant = transaction.merchant
        entity.spentDate = transaction.spentDate
        entity.memo = transaction.memo
        entity.paymentMethod = transaction.paymentMethod
        entity.tags.replaceWith(transaction.tags)
    }
}

object FixedExpenseMapper {

    fun toDomain(entity: FixedExpenseJpaEntity): FixedExpense = FixedExpense.reconstitute(
        id = FixedExpenseId(requireNotNull(entity.id) { NOT_PERSISTED }),
        categoryId = CategoryId(entity.categoryId),
        merchant = entity.merchant,
        amount = Money.of(entity.amount),
        dayOfMonth = entity.dayOfMonth,
        tags = Tags.of(entity.tags),
        paymentMethod = entity.paymentMethod,
        startMonth = entity.startMonth.toYearMonth(),
        endMonth = entity.endMonth?.toYearMonth(),
    )

    fun toEntity(fixedExpense: FixedExpense): FixedExpenseJpaEntity = FixedExpenseJpaEntity(
        id = fixedExpense.id?.value,
        categoryId = fixedExpense.categoryId.value,
        merchant = fixedExpense.merchant,
        amount = fixedExpense.amount.amount,
        dayOfMonth = fixedExpense.dayOfMonth,
        paymentMethod = fixedExpense.paymentMethod,
        startMonth = fixedExpense.startMonth.toFirstDay(),
        endMonth = fixedExpense.endMonth?.toFirstDay(),
        tags = fixedExpense.tags.names.toMutableList(),
    )

    fun applyTo(entity: FixedExpenseJpaEntity, fixedExpense: FixedExpense) {
        entity.categoryId = fixedExpense.categoryId.value
        entity.merchant = fixedExpense.merchant
        entity.amount = fixedExpense.amount.amount
        entity.dayOfMonth = fixedExpense.dayOfMonth
        entity.paymentMethod = fixedExpense.paymentMethod
        entity.startMonth = fixedExpense.startMonth.toFirstDay()
        entity.endMonth = fixedExpense.endMonth?.toFirstDay()
        entity.tags.replaceWith(fixedExpense.tags)
    }
}

object InstallmentPlanMapper {

    fun toDomain(entity: InstallmentPlanJpaEntity): InstallmentPlan = InstallmentPlan.reconstitute(
        id = InstallmentPlanId(requireNotNull(entity.id) { NOT_PERSISTED }),
        categoryId = CategoryId(entity.categoryId),
        merchant = entity.merchant,
        totalAmount = Money.of(entity.totalAmount),
        installmentMonths = entity.installmentMonths,
        firstBillingMonth = entity.firstBillingMonth.toYearMonth(),
        billingDay = entity.billingDay,
        tags = Tags.of(entity.tags),
        paymentMethod = entity.paymentMethod,
    )

    fun toEntity(plan: InstallmentPlan): InstallmentPlanJpaEntity = InstallmentPlanJpaEntity(
        id = plan.id?.value,
        categoryId = plan.categoryId.value,
        merchant = plan.merchant,
        totalAmount = plan.totalAmount.amount,
        installmentMonths = plan.installmentMonths,
        firstBillingMonth = plan.firstBillingMonth.toFirstDay(),
        billingDay = plan.billingDay,
        paymentMethod = plan.paymentMethod,
        tags = plan.tags.names.toMutableList(),
    )
}
