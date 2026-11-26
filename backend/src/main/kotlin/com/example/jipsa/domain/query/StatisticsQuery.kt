package com.example.jipsa.domain.query

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.Tag
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 집계 전용 조회 포트 (Read Model).
 *
 * backend/CLAUDE.md 4.1: 통계는 애그리거트를 재구성할 필요가 없으므로 리포지토리가 아닌
 * 전용 조회 포트로 분리한다. 집계는 DB 에서 수행한다. 모든 집계는 소비일 기준이다.
 */
interface StatisticsQuery {

    /** 월별 지출 종류별 합계. [from] 부터 [to] 까지 **거래가 없는 달도 0원으로 채워** 순서대로 반환한다. */
    fun monthlyKindAmounts(from: YearMonth, to: YearMonth): List<MonthlyKindAmounts>

    /** 카테고리별·월별 지출 합계. 거래가 없는 (카테고리, 월) 조합은 포함하지 않는다. */
    fun monthlyCategoryExpenses(from: YearMonth, to: YearMonth): List<MonthlyCategoryExpense>

    /**
     * 태그별 합계. 금액 내림차순, 같으면 태그 이름 오름차순.
     *
     * 거래 하나가 여러 태그에 들어가므로 **합계가 총 지출보다 클 수 있다** (PRD 4절).
     */
    fun tagTotals(from: LocalDate, to: LocalDate, limit: Int): List<TagTotal>
}

data class MonthlyCategoryExpense(
    val month: YearMonth,
    val categoryId: CategoryId,
    val amount: Money,
)

data class TagTotal(
    val tag: Tag,
    val amount: Money,
    val transactionCount: Int,
)
