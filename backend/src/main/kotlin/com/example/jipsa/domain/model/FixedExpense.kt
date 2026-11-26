package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.exception.checkInvariant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 고정비 애그리거트 루트. 매월 같은 날 나가는 지출의 **정의**다 (PRD 3.2).
 *
 * 실제 지출은 이 정의로부터 매월 기록되는 [ExpenseKind.FIXED] 거래가 담당한다.
 * 정의와 기록을 나누는 이유: 관리비처럼 달마다 금액이 조금씩 다른 고정비는 기록된 거래의
 * 금액만 고치면 되고, 정의를 고쳐도 지난달 기록은 바뀌지 않아야 한다.
 *
 * 불변식:
 * - 금액은 0원보다 크다.
 * - 납부일은 1~31 이다. 그 달에 없는 날은 기록할 때 말일로 보정한다.
 * - 종료월이 있으면 시작월보다 앞설 수 없다.
 */
class FixedExpense private constructor(
    override val id: FixedExpenseId?,
    val categoryId: CategoryId,
    val merchant: String,
    val amount: Money,
    val dayOfMonth: Int,
    val tags: Tags,
    val paymentMethod: String?,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
) : AggregateRoot<FixedExpenseId> {

    init {
        checkInvariant(amount.isPositive) { "고정비 금액은 0원보다 커야 합니다. 입력값: $amount" }
        checkInvariant(dayOfMonth in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH) {
            "납부일은 $MIN_DAY_OF_MONTH~$MAX_DAY_OF_MONTH 사이여야 합니다. 입력값: $dayOfMonth"
        }
        ExpenseText.checkMerchant(merchant)
        ExpenseText.checkPaymentMethod(paymentMethod)
        if (endMonth != null) {
            checkInvariant(!endMonth.isBefore(startMonth)) {
                "종료월은 시작월보다 앞설 수 없습니다. 시작월: $startMonth, 종료월: $endMonth"
            }
        }
    }

    /** [month] 에 이 고정비가 나가는지 여부. 시작월과 종료월을 포함한다. */
    fun isActiveIn(month: YearMonth): Boolean =
        !month.isBefore(startMonth) && (endMonth == null || !month.isAfter(endMonth))

    /** [month] 의 납부일. 그 달에 없는 날이면 말일이다. */
    fun dueDateIn(month: YearMonth): LocalDate = month.atDayClamped(dayOfMonth)

    /**
     * [month] 분의 고정비 거래를 만든다.
     *
     * @throws InvariantViolationException 영속화되지 않았거나 [month] 에 나가지 않는 고정비인 경우
     */
    fun occurrenceIn(month: YearMonth): Transaction {
        val fixedExpenseId = id ?: throw InvariantViolationException(
            "영속화되지 않은 고정비로는 거래를 기록할 수 없습니다. 사용처: $merchant",
        )
        checkInvariant(isActiveIn(month)) {
            "$month 은(는) 이 고정비의 기간($startMonth ~ ${endMonth ?: "계속"})이 아닙니다. 사용처: $merchant"
        }
        return Transaction.createFixedOccurrence(
            fixedExpenseId = fixedExpenseId,
            categoryId = categoryId,
            amount = amount,
            merchant = merchant,
            spentDate = dueDateIn(month),
            tags = tags,
            paymentMethod = paymentMethod,
        )
    }

    /**
     * [lastMonth] 를 마지막으로 고정비를 끝낸다.
     *
     * 이미 끝난 고정비의 종료월을 다시 정할 수 있다(앞당기거나 늦추기). 시작월보다 앞설 수는 없다.
     */
    fun terminate(lastMonth: YearMonth): FixedExpense = rebuild(endMonth = lastMonth)

    fun assignId(assignedId: FixedExpenseId): FixedExpense = rebuild(id = assignedId)

    private fun rebuild(
        id: FixedExpenseId? = this.id,
        endMonth: YearMonth? = this.endMonth,
    ): FixedExpense = FixedExpense(
        id, categoryId, merchant, amount, dayOfMonth, tags, paymentMethod, startMonth, endMonth,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FixedExpense) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "FixedExpense(id=$id, merchant='$merchant', amount=$amount, day=$dayOfMonth, $startMonth~${endMonth ?: ""})"

    companion object {
        fun create(
            categoryId: CategoryId,
            merchant: String,
            amount: Money,
            dayOfMonth: Int,
            startMonth: YearMonth,
            tags: Tags = Tags.EMPTY,
            paymentMethod: String? = null,
        ): FixedExpense = FixedExpense(
            id = null,
            categoryId = categoryId,
            merchant = merchant.trim(),
            amount = amount,
            dayOfMonth = dayOfMonth,
            tags = tags,
            paymentMethod = ExpenseText.normalizeOptional(paymentMethod),
            startMonth = startMonth,
            endMonth = null,
        )

        /** 저장된 데이터로부터 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(
            id: FixedExpenseId,
            categoryId: CategoryId,
            merchant: String,
            amount: Money,
            dayOfMonth: Int,
            tags: Tags,
            paymentMethod: String?,
            startMonth: YearMonth,
            endMonth: YearMonth?,
        ): FixedExpense = FixedExpense(
            id, categoryId, merchant, amount, dayOfMonth, tags, paymentMethod, startMonth, endMonth,
        )
    }
}
