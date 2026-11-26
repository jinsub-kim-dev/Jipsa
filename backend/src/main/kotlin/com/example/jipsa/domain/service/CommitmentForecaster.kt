package com.example.jipsa.domain.service

import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.Money
import java.time.YearMonth

/**
 * 앞으로 n개월의 약정 지출(고정비 + 할부)을 전망하는 도메인 서비스 (PRD 4절).
 *
 * 고정비와 할부 두 애그리거트를 함께 봐야 하므로 도메인 서비스로 둔다.
 * "12월부터 항공권 할부가 끝나 월 18.6만원이 줄어든다" 같은 판단의 근거다.
 */
class CommitmentForecaster {

    /**
     * [baseMonth] **다음 달부터** [months] 개월의 약정 지출을 반환한다.
     *
     * 고정비는 정의된 금액으로 전망한다. 관리비처럼 달마다 고친 금액은 반영하지 않는다.
     */
    fun forecast(
        baseMonth: YearMonth,
        months: Int,
        fixedExpenses: List<FixedExpense>,
        installmentPlans: List<InstallmentPlan>,
    ): List<MonthlyCommitment> = (1..months).map { offset ->
        val month = baseMonth.plusMonths(offset.toLong())
        val activePlans = installmentPlans.filter { it.sequenceIn(month) != null }
        MonthlyCommitment(
            month = month,
            fixed = Money.sum(fixedExpenses.filter { it.isActiveIn(month) }.map(FixedExpense::amount)),
            installment = Money.sum(activePlans.map { it.amountIn(month) }),
            installmentMerchants = activePlans.map(InstallmentPlan::merchant),
        )
    }
}

/** 한 달의 약정 지출 전망. */
data class MonthlyCommitment(
    val month: YearMonth,
    val fixed: Money,
    val installment: Money,
    val installmentMerchants: List<String>,
) {
    val total: Money get() = fixed + installment
}
