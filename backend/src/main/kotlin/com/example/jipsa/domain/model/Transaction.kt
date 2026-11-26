package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.checkInvariant
import java.time.LocalDate

/**
 * 거래 애그리거트 루트. 지출 한 건이다 (PRD 3.1).
 *
 * ## 지출 종류와 출처
 * 종류마다 만들어지는 길이 하나뿐이고, 출처 식별자가 그 길을 증명한다.
 * - [ExpenseKind.GENERAL]: 출처 없음. [createGeneral] 로 사용자가 직접 등록한다.
 * - [ExpenseKind.FIXED]: [fixedExpenseId] 필수. [createFixedOccurrence] 로 고정비 기록 때 만들거나,
 *   일반 지출을 [convertToFixedOccurrence] 로 바꿔 만든다.
 * - [ExpenseKind.INSTALLMENT]: [installmentPlanId] 와 [installmentSequence] 필수.
 *   [createInstallmentPart] 로 할부 등록 때 만든다.
 *
 * ## 할부 회차의 잠금
 * 회차의 금액과 소비일은 바꿀 수 없다. 금액을 바꾸면 "회차 합계 == 총액" 이 깨지고,
 * 소비일을 바꾸면 회차 일정이 어긋난다. 카테고리·태그·메모는 바꿀 수 있다.
 */
class Transaction private constructor(
    override val id: TransactionId?,
    val kind: ExpenseKind,
    val categoryId: CategoryId,
    val amount: Money,
    val merchant: String,
    val spentDate: LocalDate,
    val tags: Tags,
    val memo: String?,
    val paymentMethod: String?,
    val fixedExpenseId: FixedExpenseId?,
    val installmentPlanId: InstallmentPlanId?,
    val installmentSequence: Int?,
) : AggregateRoot<TransactionId> {

    init {
        checkInvariant(amount.isPositive) { "거래 금액은 0원보다 커야 합니다. 입력값: $amount" }
        ExpenseText.checkMerchant(merchant)
        ExpenseText.checkMemo(memo)
        ExpenseText.checkPaymentMethod(paymentMethod)
        checkSource()
    }

    private fun checkSource() {
        val hasFixedSource = fixedExpenseId != null
        val hasInstallmentSource = installmentPlanId != null || installmentSequence != null
        when (kind) {
            ExpenseKind.GENERAL -> checkInvariant(!hasFixedSource && !hasInstallmentSource) {
                "일반 지출은 고정비나 할부 출처를 가질 수 없습니다."
            }
            ExpenseKind.FIXED -> checkInvariant(hasFixedSource && !hasInstallmentSource) {
                "고정비 거래는 고정비 출처만 가져야 합니다."
            }
            ExpenseKind.INSTALLMENT -> {
                checkInvariant(!hasFixedSource && installmentPlanId != null && installmentSequence != null) {
                    "할부 거래는 할부 계획과 회차를 모두 가져야 합니다."
                }
                // 위 검사가 installmentSequence 의 존재를 보장한다.
                checkInvariant(installmentSequence!! >= 1) {
                    "할부 회차는 1 이상이어야 합니다. 입력값: $installmentSequence"
                }
            }
        }
    }

    val isInstallmentPart: Boolean get() = kind == ExpenseKind.INSTALLMENT

    val isFixedOccurrence: Boolean get() = kind == ExpenseKind.FIXED

    /**
     * 하나만 따로 삭제할 수 있는지 여부. 일반 지출만 그렇다 (PRD 3.1).
     *
     * 할부 회차를 하나만 지우면 "회차 합계 == 총액" 이 깨지고, 고정비 거래를 지우면 다음 기록 때
     * 다시 생겨 지운 의미가 없다. 각각 할부 해지·고정비 종료로 처리한다.
     */
    val isDeletable: Boolean get() = kind == ExpenseKind.GENERAL

    /** 금액을 고친다. 할부 회차는 거부한다. */
    fun correctAmount(newAmount: Money): Transaction {
        rejectIfInstallmentPart("금액")
        return rebuild(amount = newAmount)
    }

    /** 소비일을 고친다. 할부 회차는 거부한다. */
    fun reschedule(newSpentDate: LocalDate): Transaction {
        rejectIfInstallmentPart("소비일")
        return rebuild(spentDate = newSpentDate)
    }

    fun renameMerchant(newMerchant: String): Transaction = rebuild(merchant = newMerchant.trim())

    fun recategorize(newCategoryId: CategoryId): Transaction = rebuild(categoryId = newCategoryId)

    fun retag(newTags: Tags): Transaction = rebuild(tags = newTags)

    /** 메모를 바꾼다. `null` 이나 빈 문자열은 메모를 지운다. */
    fun rewriteMemo(newMemo: String?): Transaction = rebuild(memo = ExpenseText.normalizeOptional(newMemo))

    fun changePaymentMethod(newPaymentMethod: String?): Transaction =
        rebuild(paymentMethod = ExpenseText.normalizeOptional(newPaymentMethod))

    fun assignId(assignedId: TransactionId): Transaction = rebuild(id = assignedId)

    /**
     * 이 일반 지출을 [fixedExpenseId] 고정비의 거래로 바꾼다 (PRD 3.2.1).
     *
     * 소비일·금액·사용처·카테고리·태그·메모·결제 수단은 그대로 둔다. 실제로 나간 날과 금액이기 때문이다.
     * 할부 회차는 할부 계획에 묶여 있고, 고정비 거래는 이미 출처가 있으므로 일반 지출만 바꿀 수 있다.
     */
    fun convertToFixedOccurrence(fixedExpenseId: FixedExpenseId): Transaction {
        if (kind != ExpenseKind.GENERAL) {
            throw DomainStateException("일반 지출만 고정비로 전환할 수 있습니다. 지금 종류: $kind, 사용처: $merchant")
        }
        return Transaction(
            id = id,
            kind = ExpenseKind.FIXED,
            categoryId = categoryId,
            amount = amount,
            merchant = merchant,
            spentDate = spentDate,
            tags = tags,
            memo = memo,
            paymentMethod = paymentMethod,
            fixedExpenseId = fixedExpenseId,
            installmentPlanId = null,
            installmentSequence = null,
        )
    }

    private fun rejectIfInstallmentPart(field: String) {
        if (isInstallmentPart) {
            throw DomainStateException(
                "할부 회차의 ${field}은(는) 바꿀 수 없습니다. 할부 계획을 해지하고 다시 등록하세요. " +
                    "사용처: $merchant ($installmentSequence 회차)",
            )
        }
    }

    /** 불변식을 다시 검증하며 일부 값만 바꾼 인스턴스를 만든다. `copy()` 를 노출하지 않기 위함이다. */
    private fun rebuild(
        id: TransactionId? = this.id,
        categoryId: CategoryId = this.categoryId,
        amount: Money = this.amount,
        merchant: String = this.merchant,
        spentDate: LocalDate = this.spentDate,
        tags: Tags = this.tags,
        memo: String? = this.memo,
        paymentMethod: String? = this.paymentMethod,
    ): Transaction = Transaction(
        id = id,
        kind = kind,
        categoryId = categoryId,
        amount = amount,
        merchant = merchant,
        spentDate = spentDate,
        tags = tags,
        memo = memo,
        paymentMethod = paymentMethod,
        fixedExpenseId = fixedExpenseId,
        installmentPlanId = installmentPlanId,
        installmentSequence = installmentSequence,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transaction) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "Transaction(id=$id, kind=$kind, merchant='$merchant', amount=$amount, spentDate=$spentDate)"

    companion object {
        /** 일반 지출을 만든다. 사용자가 직접 등록하는 유일한 종류다. */
        fun createGeneral(
            categoryId: CategoryId,
            amount: Money,
            merchant: String,
            spentDate: LocalDate,
            tags: Tags = Tags.EMPTY,
            memo: String? = null,
            paymentMethod: String? = null,
        ): Transaction = Transaction(
            id = null,
            kind = ExpenseKind.GENERAL,
            categoryId = categoryId,
            amount = amount,
            merchant = merchant.trim(),
            spentDate = spentDate,
            tags = tags,
            memo = ExpenseText.normalizeOptional(memo),
            paymentMethod = ExpenseText.normalizeOptional(paymentMethod),
            fixedExpenseId = null,
            installmentPlanId = null,
            installmentSequence = null,
        )

        /** 고정비 한 달 분을 만든다. [FixedExpense] 를 거쳐서만 호출된다. */
        internal fun createFixedOccurrence(
            fixedExpenseId: FixedExpenseId,
            categoryId: CategoryId,
            amount: Money,
            merchant: String,
            spentDate: LocalDate,
            tags: Tags,
            paymentMethod: String?,
        ): Transaction = Transaction(
            id = null,
            kind = ExpenseKind.FIXED,
            categoryId = categoryId,
            amount = amount,
            merchant = merchant,
            spentDate = spentDate,
            tags = tags,
            memo = null,
            paymentMethod = paymentMethod,
            fixedExpenseId = fixedExpenseId,
            installmentPlanId = null,
            installmentSequence = null,
        )

        /** 할부 회차 하나를 만든다. [InstallmentPlan] 을 거쳐서만 호출된다. */
        internal fun createInstallmentPart(
            installmentPlanId: InstallmentPlanId,
            installmentSequence: Int,
            categoryId: CategoryId,
            amount: Money,
            merchant: String,
            spentDate: LocalDate,
            tags: Tags,
            paymentMethod: String?,
        ): Transaction = Transaction(
            id = null,
            kind = ExpenseKind.INSTALLMENT,
            categoryId = categoryId,
            amount = amount,
            merchant = merchant,
            spentDate = spentDate,
            tags = tags,
            memo = null,
            paymentMethod = paymentMethod,
            fixedExpenseId = null,
            installmentPlanId = installmentPlanId,
            installmentSequence = installmentSequence,
        )

        /** 저장된 데이터로부터 복원한다. 매퍼에서만 사용한다. 불변식은 다시 검증된다. */
        fun reconstitute(
            id: TransactionId,
            kind: ExpenseKind,
            categoryId: CategoryId,
            amount: Money,
            merchant: String,
            spentDate: LocalDate,
            tags: Tags,
            memo: String?,
            paymentMethod: String?,
            fixedExpenseId: FixedExpenseId?,
            installmentPlanId: InstallmentPlanId?,
            installmentSequence: Int?,
        ): Transaction = Transaction(
            id, kind, categoryId, amount, merchant, spentDate, tags, memo, paymentMethod,
            fixedExpenseId, installmentPlanId, installmentSequence,
        )
    }
}
