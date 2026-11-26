package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.exception.checkInvariant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 할부 계획 애그리거트 루트 (PRD 3.3).
 *
 * 큰 지출을 한 달에 몰아 잡으면 그 달의 패턴이 왜곡된다. 총액을 회차로 나눠 각 달에 반영한다.
 * 실제 집계는 이 계획으로부터 만들어진 [ExpenseKind.INSTALLMENT] 거래가 담당하고,
 * 이 애그리거트는 "총액을 어떻게 나눌지" 의 단일 진실 공급원이다.
 *
 * ## 금액 분할 규칙
 * 월 납부액은 총액 ÷ 개월 수의 **몫(내림)** 이고, 나머지는 **1회차에 더한다.**
 * 회차 금액의 합은 항상 총액과 정확히 같다.
 *
 * ## 회차 일정
 * n회차의 소비일은 `firstBillingMonth + (n-1)개월` 의 [billingDay] 이며, 없는 날은 말일로 보정한다.
 *
 * 불변식:
 * - 총액 > 0, 개월 수 [MIN_INSTALLMENT_MONTHS]~[MAX_INSTALLMENT_MONTHS]
 * - 월 납부액은 1원 이상이다. 즉 총액 ≥ 개월 수
 * - 납부일은 1~31
 */
class InstallmentPlan private constructor(
    override val id: InstallmentPlanId?,
    val categoryId: CategoryId,
    val merchant: String,
    val totalAmount: Money,
    val installmentMonths: Int,
    val firstBillingMonth: YearMonth,
    val billingDay: Int,
    val tags: Tags,
    val paymentMethod: String?,
) : AggregateRoot<InstallmentPlanId> {

    init {
        checkInvariant(totalAmount.isPositive) { "할부 총액은 0원보다 커야 합니다. 입력값: $totalAmount" }
        checkInvariant(installmentMonths >= MIN_INSTALLMENT_MONTHS) {
            "할부는 ${MIN_INSTALLMENT_MONTHS}개월 이상이어야 합니다. 1개월은 일시불입니다. 입력값: $installmentMonths"
        }
        checkInvariant(installmentMonths <= MAX_INSTALLMENT_MONTHS) {
            "할부는 ${MAX_INSTALLMENT_MONTHS}개월 이하여야 합니다. 입력값: $installmentMonths"
        }
        checkInvariant(totalAmount.amount >= installmentMonths) {
            "월 납부액이 1원 미만입니다. 총액: $totalAmount, 개월 수: $installmentMonths"
        }
        checkInvariant(billingDay in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH) {
            "납부일은 $MIN_DAY_OF_MONTH~$MAX_DAY_OF_MONTH 사이여야 합니다. 입력값: $billingDay"
        }
        ExpenseText.checkMerchant(merchant)
        ExpenseText.checkPaymentMethod(paymentMethod)
    }

    /** 2회차 이후의 월 납부액. 총액 ÷ 개월 수의 몫이다. */
    val monthlyAmount: Money get() = totalAmount.divideWithRemainder(installmentMonths).first

    /** 1회차 납부액. 월 납부액에 나머지를 더한 금액이다. */
    val firstInstallmentAmount: Money
        get() = totalAmount.divideWithRemainder(installmentMonths).let { (quotient, remainder) -> quotient + remainder }

    /** 마지막 회차가 기록되는 연월. */
    val lastBillingMonth: YearMonth get() = firstBillingMonth.plusMonths((installmentMonths - 1).toLong())

    /** [sequence] 회차의 납부액. */
    fun amountOf(sequence: Int): Money {
        checkSequence(sequence)
        return if (sequence == 1) firstInstallmentAmount else monthlyAmount
    }

    /** [sequence] 회차의 소비일. */
    fun spentDateOf(sequence: Int): LocalDate {
        checkSequence(sequence)
        return firstBillingMonth.plusMonths((sequence - 1).toLong()).atDayClamped(billingDay)
    }

    /** 모든 회차의 납부액. 합계는 항상 [totalAmount] 다. */
    val installmentAmounts: List<Money> get() = (1..installmentMonths).map(::amountOf)

    /** [month] 에 기록되는 회차 번호. 그 달이 할부 기간이 아니면 `null` 이다. */
    fun sequenceIn(month: YearMonth): Int? {
        val sequence = monthsBetween(firstBillingMonth, month) + 1
        return sequence.takeIf { it in 1..installmentMonths }
    }

    /** [month] 에 나가는 금액. 할부 기간이 아니면 0원이다. 약정 지출 전망에 쓴다. */
    fun amountIn(month: YearMonth): Money = sequenceIn(month)?.let(::amountOf) ?: Money.ZERO

    /** [month] 시점의 진행 상황. 할부 기간이 아니면 `null` 이다. */
    fun progressAt(month: YearMonth): InstallmentProgress? {
        val current = sequenceIn(month) ?: return null
        val paid = Money.sum((1..current).map(::amountOf))
        return InstallmentProgress(
            currentSequence = current,
            installmentMonths = installmentMonths,
            amountThisMonth = amountOf(current),
            paidAmount = paid,
            remainingAmount = totalAmount - paid,
        )
    }

    /**
     * 모든 회차의 거래를 만든다. 금액 합계는 [totalAmount] 와 정확히 같다.
     *
     * @throws InvariantViolationException 영속화되지 않은 계획인 경우
     */
    fun scheduleParts(): List<Transaction> {
        val planId = id ?: throw InvariantViolationException(
            "영속화되지 않은 할부 계획으로는 회차 거래를 만들 수 없습니다. 사용처: $merchant",
        )
        return (1..installmentMonths).map { sequence ->
            Transaction.createInstallmentPart(
                installmentPlanId = planId,
                installmentSequence = sequence,
                categoryId = categoryId,
                amount = amountOf(sequence),
                merchant = merchant,
                spentDate = spentDateOf(sequence),
                tags = tags,
                paymentMethod = paymentMethod,
            )
        }
    }

    private fun checkSequence(sequence: Int) {
        checkInvariant(sequence in 1..installmentMonths) {
            "회차 번호는 1 이상 $installmentMonths 이하여야 합니다. 입력값: $sequence"
        }
    }

    fun assignId(assignedId: InstallmentPlanId): InstallmentPlan = InstallmentPlan(
        assignedId, categoryId, merchant, totalAmount, installmentMonths, firstBillingMonth, billingDay, tags,
        paymentMethod,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstallmentPlan) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "InstallmentPlan(id=$id, merchant='$merchant', total=$totalAmount, months=$installmentMonths, from=$firstBillingMonth)"

    companion object {
        const val MIN_INSTALLMENT_MONTHS: Int = 2
        const val MAX_INSTALLMENT_MONTHS: Int = 60

        private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
            (to.year - from.year) * 12 + (to.monthValue - from.monthValue)

        fun create(
            categoryId: CategoryId,
            merchant: String,
            totalAmount: Money,
            installmentMonths: Int,
            firstBillingMonth: YearMonth,
            billingDay: Int,
            tags: Tags = Tags.EMPTY,
            paymentMethod: String? = null,
        ): InstallmentPlan = InstallmentPlan(
            id = null,
            categoryId = categoryId,
            merchant = merchant.trim(),
            totalAmount = totalAmount,
            installmentMonths = installmentMonths,
            firstBillingMonth = firstBillingMonth,
            billingDay = billingDay,
            tags = tags,
            paymentMethod = ExpenseText.normalizeOptional(paymentMethod),
        )

        /** 저장된 데이터로부터 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(
            id: InstallmentPlanId,
            categoryId: CategoryId,
            merchant: String,
            totalAmount: Money,
            installmentMonths: Int,
            firstBillingMonth: YearMonth,
            billingDay: Int,
            tags: Tags,
            paymentMethod: String?,
        ): InstallmentPlan = InstallmentPlan(
            id, categoryId, merchant, totalAmount, installmentMonths, firstBillingMonth, billingDay, tags, paymentMethod,
        )
    }
}

/**
 * 할부의 한 시점 진행 상황. "12회 중 9회차, 남은 금액 774,000원" 을 표현한다.
 *
 * 화면이 남은 금액을 직접 계산하면 1회차 나머지 가산 규칙이 화면에 복제된다. 서버가 계산해 내려준다.
 */
data class InstallmentProgress(
    val currentSequence: Int,
    val installmentMonths: Int,
    /** 이 달에 나가는 회차 금액. 1회차면 나머지가 더해져 있다. */
    val amountThisMonth: Money,
    /** 이 달 회차까지 낸 금액. */
    val paidAmount: Money,
    val remainingAmount: Money,
) {
    val remainingSequences: Int get() = installmentMonths - currentSequence
    val isLastSequence: Boolean get() = currentSequence == installmentMonths
}
