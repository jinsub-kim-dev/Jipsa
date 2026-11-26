package com.example.jipsa.domain.service

import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.Transaction
import java.time.YearMonth

/**
 * 고정비 정의들로부터 한 달 분의 거래를 만드는 도메인 서비스 (PRD 3.2).
 *
 * 기록은 **멱등**이어야 한다. 서버 기동·매월 1일·고정비 등록 직후에 반복해서 불리므로,
 * 이미 기록된 고정비는 다시 만들지 않는다. 이 판단은 고정비 여러 개와 그 달의 기록 현황을
 * 함께 봐야 하므로 애그리거트 하나가 아니라 도메인 서비스에 둔다.
 */
class FixedExpenseRecorder {

    /**
     * [month] 에 기록해야 할 고정비 거래를 반환한다.
     *
     * @param fixedExpenses 영속화된 고정비 정의 전체
     * @param alreadyRecorded [month] 에 이미 거래가 있는 고정비 식별자
     */
    fun recordsFor(
        month: YearMonth,
        fixedExpenses: List<FixedExpense>,
        alreadyRecorded: Set<FixedExpenseId>,
    ): List<Transaction> = fixedExpenses
        .filter { it.isActiveIn(month) }
        .filterNot { it.id in alreadyRecorded }
        .map { it.occurrenceIn(month) }
}
