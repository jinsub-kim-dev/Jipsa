package com.example.jipsa.domain.model

/**
 * 지출 종류 (PRD 2절).
 *
 * 줄일 수 있는 돈(일반 지출)과 이미 약속된 돈(고정비·할부)을 구분하는 축이다.
 */
enum class ExpenseKind {
    /** 일반 지출. 사용자가 직접 등록한다. */
    GENERAL,

    /** 고정비. [FixedExpense] 정의로부터만 만들어진다. */
    FIXED,

    /** 할부 회차. [InstallmentPlan] 등록으로만 만들어진다. */
    INSTALLMENT,
    ;

    /** 약정 지출(고정비 + 할부)인지 여부. 이미 약속되어 줄이기 어려운 지출이다. */
    val isCommitment: Boolean get() = this != GENERAL
}
