package com.example.jipsa.fixture

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
import java.time.LocalDate
import java.time.YearMonth

/*
 * 테스트 픽스처 팩토리 (backend/CLAUDE.md 5.4).
 * 기본값은 유효한 값이며, 테스트는 검증하려는 값만 바꿔 넘긴다.
 */

fun won(amount: Long): Money = Money.of(amount)

fun category(
    name: String = "식비",
    icon: String = "🍚",
    color: String = "#EF4444",
    id: Long? = 1L,
): Category {
    val created = Category.create(name, icon, color)
    return id?.let { created.assignId(CategoryId(it)) } ?: created
}

fun generalTransaction(
    amount: Long = 10_000,
    merchant: String = "김밥천국",
    spentDate: LocalDate = LocalDate.of(2026, 11, 10),
    categoryId: Long = 1L,
    tags: Tags = Tags.of("점심"),
    memo: String? = null,
    paymentMethod: String? = "신한카드",
    id: Long? = 1L,
): Transaction {
    val created = Transaction.createGeneral(
        categoryId = CategoryId(categoryId),
        amount = won(amount),
        merchant = merchant,
        spentDate = spentDate,
        tags = tags,
        memo = memo,
        paymentMethod = paymentMethod,
    )
    return id?.let { created.assignId(TransactionId(it)) } ?: created
}

fun fixedExpense(
    merchant: String = "월세",
    amount: Long = 650_000,
    dayOfMonth: Int = 1,
    startMonth: YearMonth = YearMonth.of(2026, 1),
    endMonth: YearMonth? = null,
    categoryId: Long = 7L,
    tags: Tags = Tags.of("집"),
    id: Long? = 1L,
): FixedExpense {
    val created = FixedExpense.create(
        categoryId = CategoryId(categoryId),
        merchant = merchant,
        amount = won(amount),
        dayOfMonth = dayOfMonth,
        startMonth = startMonth,
        tags = tags,
        paymentMethod = "신한카드",
    )
    val withId = id?.let { created.assignId(FixedExpenseId(it)) } ?: created
    return endMonth?.let(withId::terminate) ?: withId
}

fun installmentPlan(
    merchant: String = "맥북 프로 14",
    totalAmount: Long = 3_096_000,
    installmentMonths: Int = 12,
    firstBillingMonth: YearMonth = YearMonth.of(2026, 3),
    billingDay: Int = 15,
    categoryId: Long = 11L,
    tags: Tags = Tags.of("전자기기"),
    id: Long? = 1L,
): InstallmentPlan {
    val created = InstallmentPlan.create(
        categoryId = CategoryId(categoryId),
        merchant = merchant,
        totalAmount = won(totalAmount),
        installmentMonths = installmentMonths,
        firstBillingMonth = firstBillingMonth,
        billingDay = billingDay,
        tags = tags,
        paymentMethod = "현대카드",
    )
    return id?.let { created.assignId(InstallmentPlanId(it)) } ?: created
}
