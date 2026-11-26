package com.example.jipsa.presentation.dto

import com.example.jipsa.application.port.FixedExpenseConversionResult
import com.example.jipsa.application.port.RegisterFixedExpenseCommand
import com.example.jipsa.application.port.RegisterInstallmentPlanCommand
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentProgress
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.YearMonth

// --- 고정비 ---

data class RegisterFixedExpenseRequest(
    @field:NotNull(message = "카테고리는 필수입니다.")
    val categoryId: Long?,
    @field:NotBlank(message = "사용처는 필수입니다.")
    val merchant: String?,
    @field:NotNull(message = "금액은 필수입니다.")
    val amount: Long?,
    @field:NotNull(message = "납부일은 필수입니다.")
    val dayOfMonth: Int?,
    @field:NotNull(message = "시작월은 필수입니다. (yyyy-MM)")
    val startMonth: YearMonth?,
    val tags: List<String>?,
    val paymentMethod: String?,
) {
    fun toCommand(): RegisterFixedExpenseCommand = RegisterFixedExpenseCommand(
        categoryId = CategoryId(categoryId!!),
        merchant = merchant!!,
        amount = Money.of(amount!!),
        dayOfMonth = dayOfMonth!!,
        startMonth = startMonth!!,
        tags = Tags.of(tags.orEmpty()),
        paymentMethod = paymentMethod,
    )
}

data class TerminateFixedExpenseRequest(
    @field:NotNull(message = "마지막 달은 필수입니다. (yyyy-MM)")
    val lastMonth: YearMonth?,
)

/** 일반 지출을 고정비로 전환하는 요청 (PRD 3.2.1). */
data class ConvertToFixedExpenseRequest(
    @field:NotNull(message = "전환할 거래는 필수입니다.")
    val transactionId: Long?,
)

/**
 * 고정비로 전환 결과. 화면이 "몇 건이 함께 바뀌었고 어느 달은 그대로인지" 를 설명할 수 있게 목록을 준다.
 *
 * @property ambiguousMonths 같은 사용처가 여럿이라 바꾸지 않은 달
 */
data class FixedExpenseConversionResponse(
    val fixedExpense: FixedExpenseResponse,
    val convertedTransactions: List<TransactionResponse>,
    val ambiguousMonths: List<YearMonth>,
) {
    val convertedTransactionCount: Int get() = convertedTransactions.size

    companion object {
        fun of(result: FixedExpenseConversionResult, month: YearMonth): FixedExpenseConversionResponse =
            FixedExpenseConversionResponse(
                fixedExpense = FixedExpenseResponse.from(result.fixedExpense, month),
                convertedTransactions = result.convertedTransactions.map(TransactionResponse::from),
                ambiguousMonths = result.ambiguousMonths,
            )
    }
}

/**
 * 고정비 응답. [month] 를 기준으로 그 달에 나가는지와 실제 납부일(말일 보정 반영)을 함께 담는다.
 */
data class FixedExpenseResponse(
    val id: Long,
    val categoryId: Long,
    val merchant: String,
    val amount: Long,
    val dayOfMonth: Int,
    val tags: List<String>,
    val paymentMethod: String?,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    val month: YearMonth,
    val activeInMonth: Boolean,
    val dueDateInMonth: LocalDate,
) {
    companion object {
        fun from(fixedExpense: FixedExpense, month: YearMonth): FixedExpenseResponse = FixedExpenseResponse(
            id = requireNotNull(fixedExpense.id) { "영속화되지 않은 고정비는 응답으로 변환할 수 없습니다." }.value,
            categoryId = fixedExpense.categoryId.value,
            merchant = fixedExpense.merchant,
            amount = fixedExpense.amount.amount,
            dayOfMonth = fixedExpense.dayOfMonth,
            tags = fixedExpense.tags.names,
            paymentMethod = fixedExpense.paymentMethod,
            startMonth = fixedExpense.startMonth,
            endMonth = fixedExpense.endMonth,
            month = month,
            activeInMonth = fixedExpense.isActiveIn(month),
            dueDateInMonth = fixedExpense.dueDateIn(month),
        )
    }
}

data class RecordFixedExpensesResponse(
    val month: YearMonth,
    val recordedCount: Int,
)

// --- 할부 ---

data class RegisterInstallmentPlanRequest(
    @field:NotNull(message = "카테고리는 필수입니다.")
    val categoryId: Long?,
    @field:NotBlank(message = "사용처는 필수입니다.")
    val merchant: String?,
    @field:NotNull(message = "총액은 필수입니다.")
    val totalAmount: Long?,
    @field:NotNull(message = "할부 개월 수는 필수입니다.")
    val installmentMonths: Int?,
    @field:NotNull(message = "첫 납부월은 필수입니다. (yyyy-MM)")
    val firstBillingMonth: YearMonth?,
    @field:NotNull(message = "납부일은 필수입니다.")
    val billingDay: Int?,
    val tags: List<String>?,
    val paymentMethod: String?,
) {
    fun toCommand(): RegisterInstallmentPlanCommand = RegisterInstallmentPlanCommand(
        categoryId = CategoryId(categoryId!!),
        merchant = merchant!!,
        totalAmount = Money.of(totalAmount!!),
        installmentMonths = installmentMonths!!,
        firstBillingMonth = firstBillingMonth!!,
        billingDay = billingDay!!,
        tags = Tags.of(tags.orEmpty()),
        paymentMethod = paymentMethod,
    )
}

/**
 * 할부 응답. 회차별 금액과 [month] 시점의 진행 상황은 서버가 계산한다.
 * 화면이 1회차 나머지 가산 규칙을 다시 구현하지 않게 하기 위함이다.
 */
data class InstallmentPlanResponse(
    val id: Long,
    val categoryId: Long,
    val merchant: String,
    val totalAmount: Long,
    val installmentMonths: Int,
    val monthlyAmount: Long,
    val firstInstallmentAmount: Long,
    val firstBillingMonth: YearMonth,
    val lastBillingMonth: YearMonth,
    val billingDay: Int,
    val tags: List<String>,
    val paymentMethod: String?,
    val month: YearMonth,
    /** [month] 가 할부 기간이 아니면 없다. */
    val progress: InstallmentProgressResponse?,
) {
    companion object {
        fun from(plan: InstallmentPlan, month: YearMonth): InstallmentPlanResponse = InstallmentPlanResponse(
            id = requireNotNull(plan.id) { "영속화되지 않은 할부 계획은 응답으로 변환할 수 없습니다." }.value,
            categoryId = plan.categoryId.value,
            merchant = plan.merchant,
            totalAmount = plan.totalAmount.amount,
            installmentMonths = plan.installmentMonths,
            monthlyAmount = plan.monthlyAmount.amount,
            firstInstallmentAmount = plan.firstInstallmentAmount.amount,
            firstBillingMonth = plan.firstBillingMonth,
            lastBillingMonth = plan.lastBillingMonth,
            billingDay = plan.billingDay,
            tags = plan.tags.names,
            paymentMethod = plan.paymentMethod,
            month = month,
            progress = plan.progressAt(month)?.let(InstallmentProgressResponse::from),
        )
    }
}

data class InstallmentProgressResponse(
    val currentSequence: Int,
    val installmentMonths: Int,
    val amountThisMonth: Long,
    val paidAmount: Long,
    val remainingAmount: Long,
    val remainingSequences: Int,
    val lastSequence: Boolean,
) {
    companion object {
        fun from(progress: InstallmentProgress): InstallmentProgressResponse = InstallmentProgressResponse(
            currentSequence = progress.currentSequence,
            installmentMonths = progress.installmentMonths,
            amountThisMonth = progress.amountThisMonth.amount,
            paidAmount = progress.paidAmount.amount,
            remainingAmount = progress.remainingAmount.amount,
            remainingSequences = progress.remainingSequences,
            lastSequence = progress.isLastSequence,
        )
    }
}
