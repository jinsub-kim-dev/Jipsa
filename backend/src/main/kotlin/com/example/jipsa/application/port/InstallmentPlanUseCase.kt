package com.example.jipsa.application.port

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import java.time.YearMonth

/** 할부 인바운드 포트 (PRD 3.3). */
interface InstallmentPlanUseCase {

    /** 할부를 등록하고 모든 회차 거래를 만든다. */
    fun register(command: RegisterInstallmentPlanCommand): InstallmentPlan

    fun getAll(): List<InstallmentPlan>

    /** 할부를 해지한다. 계획과 모든 회차 거래를 함께 지운다. */
    fun cancel(id: InstallmentPlanId)
}

data class RegisterInstallmentPlanCommand(
    val categoryId: CategoryId,
    val merchant: String,
    val totalAmount: Money,
    val installmentMonths: Int,
    val firstBillingMonth: YearMonth,
    val billingDay: Int,
    val tags: Tags,
    val paymentMethod: String?,
)
