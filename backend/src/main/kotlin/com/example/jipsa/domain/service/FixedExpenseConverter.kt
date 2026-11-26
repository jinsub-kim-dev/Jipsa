package com.example.jipsa.domain.service

import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.ExpenseText
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.Transaction
import java.time.YearMonth

/**
 * 일반 지출을 고정비로 전환할 계획을 세우는 도메인 서비스 (PRD 3.2.1).
 *
 * 고른 거래 하나가 아니라 같은 사용처의 다른 달 거래까지 봐야 하고, 이미 있는 고정비와도 겹치지 않아야 하므로
 * 애그리거트 하나가 아니라 도메인 서비스에 둔다. 저장은 하지 않는다.
 *
 * ## 달마다 하나
 * 고정비는 한 달에 한 번 나간다. 같은 달에 같은 사용처가 여럿이면(같은 보험사의 두 계약 등)
 * 고른 거래와 금액이 같은 것 하나를 고르고, 그래도 정할 수 없으면 그 달은 그대로 두고 알려준다.
 * 잘못 골라 바꾸면 고정비 거래는 하나만 지울 수 없어 되돌리기 어렵다.
 */
class FixedExpenseConverter {

    /**
     * @param source 사용자가 고른 일반 지출
     * @param generalTransactions 일반 지출 전체. [source] 가 들어 있어도 된다
     * @param fixedExpenses 이미 있는 고정비 전체
     * @throws DomainStateException 같은 사용처의 고정비가 이미 있거나 [source] 가 일반 지출이 아닌 경우
     */
    fun plan(
        source: Transaction,
        generalTransactions: List<Transaction>,
        fixedExpenses: List<FixedExpense>,
    ): FixedExpenseConversionPlan {
        fixedExpenses.firstOrNull { it.isFor(source.merchant) }?.let {
            throw DomainStateException(
                "'${it.merchant}' 고정비가 이미 있습니다. 같은 사용처의 카드 내역은 가져올 때 그 고정비로 들어갑니다.",
            )
        }

        val sourceMonth = YearMonth.from(source.spentDate)
        val candidatesByMonth = generalTransactions
            .filter { it.kind == ExpenseKind.GENERAL && ExpenseText.isSameMerchant(it.merchant, source.merchant) }
            // 고른 거래의 달은 고른 거래다. 이 조건이 고른 거래 자신도 걸러낸다.
            .filterNot { YearMonth.from(it.spentDate) == sourceMonth }
            .groupBy { YearMonth.from(it.spentDate) }

        val selected = mutableListOf(source)
        val ambiguousMonths = mutableListOf<YearMonth>()
        for ((month, candidates) in candidatesByMonth) {
            val chosen = candidates.singleOrNull() ?: candidates.singleOrNull { it.amount == source.amount }
            if (chosen == null) ambiguousMonths += month else selected += chosen
        }

        return FixedExpenseConversionPlan(
            fixedExpense = FixedExpense.defineFrom(source, startMonth = selected.minOf { YearMonth.from(it.spentDate) }),
            transactions = selected.sortedBy { it.spentDate },
            ambiguousMonths = ambiguousMonths.sorted(),
        )
    }
}

/**
 * 고정비로 전환 계획 (PRD 3.2.1).
 *
 * @property fixedExpense 새로 만들 고정비. 아직 저장되지 않았다
 * @property transactions 그 고정비 거래로 바꿀 일반 지출. 소비일 순
 * @property ambiguousMonths 같은 사용처가 여럿이라 바꾸지 않은 달
 */
data class FixedExpenseConversionPlan(
    val fixedExpense: FixedExpense,
    val transactions: List<Transaction>,
    val ambiguousMonths: List<YearMonth>,
) {
    /** 저장된 고정비 [fixedExpenseId] 의 거래로 바꾼 [transactions]. */
    fun convertedTo(fixedExpenseId: FixedExpenseId): List<Transaction> =
        transactions.map { it.convertToFixedOccurrence(fixedExpenseId) }
}
