package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.DomainStateException
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

    /** [merchant] 가 이 고정비의 사용처인지. 공백·대소문자·전각은 무시한다 (PRD 3.2). */
    fun isFor(merchant: String): Boolean = ExpenseText.isSameMerchant(this.merchant, merchant)

    /**
     * [month] 분의 고정비 거래를 만든다. 납부일과 정의된 금액으로 기록한다.
     *
     * @throws InvariantViolationException 영속화되지 않았거나 [month] 에 나가지 않는 고정비인 경우
     */
    fun occurrenceIn(month: YearMonth): Transaction = occurrence(dueDateIn(month), amount)

    /**
     * 실제로 나간 날과 금액으로 고정비 거래를 만든다. 카드 이용내역 가져오기에 쓴다 (PRD 3.5).
     *
     * 관리비처럼 달마다 금액이 다르고, 카드 결제일은 납부일과 며칠씩 어긋난다.
     *
     * @throws InvariantViolationException 영속화되지 않았거나 [spentDate] 의 달에 나가지 않는 고정비인 경우
     */
    fun actualOccurrence(spentDate: LocalDate, amount: Money): Transaction = occurrence(spentDate, amount)

    /**
     * [transaction] 이 이 고정비의 자동 기록 그대로인지. 납부일과 정의된 금액이 모두 같으면 그렇다.
     *
     * 자동 기록은 예상값이다. 카드 이용내역의 실제값이 들어오면 그것으로 고친다 (PRD 3.5).
     */
    fun isScheduledOccurrence(transaction: Transaction): Boolean =
        transaction.fixedExpenseId == id &&
            transaction.amount == amount &&
            transaction.spentDate == dueDateIn(YearMonth.from(transaction.spentDate))

    private fun occurrence(spentDate: LocalDate, amount: Money): Transaction {
        val fixedExpenseId = id ?: throw InvariantViolationException(
            "영속화되지 않은 고정비로는 거래를 기록할 수 없습니다. 사용처: $merchant",
        )
        val month = YearMonth.from(spentDate)
        checkInvariant(isActiveIn(month)) {
            "$month 은(는) 이 고정비의 기간($startMonth ~ ${endMonth ?: "계속"})이 아닙니다. 사용처: $merchant"
        }
        return Transaction.createFixedOccurrence(
            fixedExpenseId = fixedExpenseId,
            categoryId = categoryId,
            amount = amount,
            merchant = merchant,
            spentDate = spentDate,
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

        /**
         * 일반 지출 [source] 로 고정비를 정의한다 (PRD 3.2.1). 소비일의 일(日)이 납부일이다.
         *
         * @param startMonth 함께 전환하는 거래 중 가장 이른 달. [source] 의 달보다 늦을 수 없다
         * @throws DomainStateException [source] 가 일반 지출이 아닌 경우
         */
        fun defineFrom(source: Transaction, startMonth: YearMonth): FixedExpense {
            if (source.kind != ExpenseKind.GENERAL) {
                throw DomainStateException("일반 지출만 고정비로 전환할 수 있습니다. 지금 종류: ${source.kind}, 사용처: ${source.merchant}")
            }
            checkInvariant(!startMonth.isAfter(YearMonth.from(source.spentDate))) {
                "고정비의 시작월은 전환하는 거래의 달보다 늦을 수 없습니다. 시작월: $startMonth, 소비일: ${source.spentDate}"
            }
            return FixedExpense(
                id = null,
                categoryId = source.categoryId,
                merchant = source.merchant,
                amount = source.amount,
                dayOfMonth = source.spentDate.dayOfMonth,
                tags = source.tags,
                paymentMethod = source.paymentMethod,
                startMonth = startMonth,
                endMonth = null,
            )
        }

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
