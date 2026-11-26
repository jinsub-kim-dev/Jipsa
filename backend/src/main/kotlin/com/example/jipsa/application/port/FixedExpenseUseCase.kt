package com.example.jipsa.application.port

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import java.time.YearMonth

/** 고정비 인바운드 포트 (PRD 3.2). */
interface FixedExpenseUseCase {

    /** 고정비를 등록하고, 시작월이 이번 달 이전이면 이번 달 분을 바로 기록한다. */
    fun register(command: RegisterFixedExpenseCommand): FixedExpense

    fun getAll(): List<FixedExpense>

    /** [lastMonth] 를 마지막으로 끝낸다. 그 뒤로 기록된 거래는 지운다. */
    fun terminate(id: FixedExpenseId, lastMonth: YearMonth): FixedExpense

    /** [month] 에 아직 기록되지 않은 고정비를 기록한다. 멱등이다. 새로 기록한 건수를 반환한다. */
    fun recordFor(month: YearMonth): Int
}

data class RegisterFixedExpenseCommand(
    val categoryId: CategoryId,
    val merchant: String,
    val amount: Money,
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    val tags: Tags,
    val paymentMethod: String?,
)
