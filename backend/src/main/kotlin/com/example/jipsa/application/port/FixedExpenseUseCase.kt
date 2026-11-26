package com.example.jipsa.application.port

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import java.time.YearMonth

/** 고정비 인바운드 포트 (PRD 3.2). */
interface FixedExpenseUseCase {

    /** 고정비를 등록하고, 시작월이 이번 달 이전이면 이번 달 분을 바로 기록한다. */
    fun register(command: RegisterFixedExpenseCommand): FixedExpense

    fun getAll(): List<FixedExpense>

    /** [lastMonth] 를 마지막으로 끝낸다. 그 뒤로 기록된 거래는 지운다. */
    fun terminate(id: FixedExpenseId, lastMonth: YearMonth): FixedExpense

    /**
     * 일반 지출 [transactionId] 로 고정비를 만들고, 같은 사용처의 다른 달 일반 지출도 그 고정비 거래로 바꾼다 (PRD 3.2.1).
     * 이번 달 분이 없으면 등록처럼 바로 기록한다.
     */
    fun convertFromTransaction(transactionId: TransactionId): FixedExpenseConversionResult

    /** [month] 에 아직 기록되지 않은 고정비를 기록한다. 멱등이다. 새로 기록한 건수를 반환한다. */
    fun recordFor(month: YearMonth): Int
}

data class FixedExpenseConversionResult(
    val fixedExpense: FixedExpense,
    /** 고정비 거래로 바뀐 거래. 소비일 순. */
    val convertedTransactions: List<Transaction>,
    /** 같은 사용처가 여럿이라 바꾸지 않은 달. */
    val ambiguousMonths: List<YearMonth>,
)

data class RegisterFixedExpenseCommand(
    val categoryId: CategoryId,
    val merchant: String,
    val amount: Money,
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    val tags: Tags,
    val paymentMethod: String?,
)
