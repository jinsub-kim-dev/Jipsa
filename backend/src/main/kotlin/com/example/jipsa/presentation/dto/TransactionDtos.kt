package com.example.jipsa.presentation.dto

import com.example.jipsa.application.port.RegisterGeneralTransactionCommand
import com.example.jipsa.application.port.UpdateTransactionCommand
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

/** 일반 지출 등록 요청. 고정비·할부는 각자의 API 로 등록한다. */
data class RegisterTransactionRequest(
    @field:NotNull(message = "카테고리는 필수입니다.")
    val categoryId: Long?,
    @field:NotNull(message = "금액은 필수입니다.")
    val amount: Long?,
    @field:NotBlank(message = "사용처는 필수입니다.")
    val merchant: String?,
    @field:NotNull(message = "소비일은 필수입니다.")
    val spentDate: LocalDate?,
    val tags: List<String>?,
    val memo: String?,
    val paymentMethod: String?,
) {
    fun toCommand(): RegisterGeneralTransactionCommand = RegisterGeneralTransactionCommand(
        categoryId = CategoryId(categoryId!!),
        amount = Money.of(amount!!),
        merchant = merchant!!,
        spentDate = spentDate!!,
        tags = Tags.of(tags.orEmpty()),
        memo = memo,
        paymentMethod = paymentMethod,
    )
}

/**
 * 거래 수정 요청. **바뀐 필드만 담는다.** 값이 없는 필드는 변경하지 않는다.
 *
 * 메모를 지우려면 [clearMemo] 를 `true` 로 보낸다. 빈 문자열과 "보내지 않음" 을 구분하기 위함이다.
 */
data class UpdateTransactionRequest(
    val categoryId: Long?,
    val amount: Long?,
    val merchant: String?,
    val spentDate: LocalDate?,
    val tags: List<String>?,
    val memo: String?,
    val clearMemo: Boolean?,
    val paymentMethod: String?,
) {
    fun toCommand(id: TransactionId): UpdateTransactionCommand = UpdateTransactionCommand(
        id = id,
        categoryId = categoryId?.let(::CategoryId),
        amount = amount?.let(Money::of),
        merchant = merchant,
        spentDate = spentDate,
        tags = tags?.let(Tags::of),
        memo = memo,
        clearMemo = clearMemo ?: false,
        paymentMethod = paymentMethod,
    )
}

/**
 * 거래 응답.
 *
 * [locked] 와 [deletable] 은 도메인 규칙의 결과다. 화면이 종류를 보고 다시 판단하지 않도록 내려준다.
 */
data class TransactionResponse(
    val id: Long,
    val kind: ExpenseKind,
    val categoryId: Long,
    val amount: Long,
    val merchant: String,
    val spentDate: LocalDate,
    val tags: List<String>,
    val memo: String?,
    val paymentMethod: String?,
    val fixedExpenseId: Long?,
    val installmentPlanId: Long?,
    val installmentSequence: Int?,
    /** 금액과 소비일을 바꿀 수 없는지 여부 (할부 회차). */
    val locked: Boolean,
    /** 하나만 따로 삭제할 수 있는지 여부 (일반 지출). */
    val deletable: Boolean,
) {
    companion object {
        fun from(transaction: Transaction): TransactionResponse = TransactionResponse(
            id = requireNotNull(transaction.id) { "영속화되지 않은 거래는 응답으로 변환할 수 없습니다." }.value,
            kind = transaction.kind,
            categoryId = transaction.categoryId.value,
            amount = transaction.amount.amount,
            merchant = transaction.merchant,
            spentDate = transaction.spentDate,
            tags = transaction.tags.names,
            memo = transaction.memo,
            paymentMethod = transaction.paymentMethod,
            fixedExpenseId = transaction.fixedExpenseId?.value,
            installmentPlanId = transaction.installmentPlanId?.value,
            installmentSequence = transaction.installmentSequence,
            locked = transaction.isInstallmentPart,
            deletable = transaction.isDeletable,
        )
    }
}
