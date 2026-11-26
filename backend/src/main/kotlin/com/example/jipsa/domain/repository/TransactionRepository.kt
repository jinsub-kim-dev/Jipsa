package com.example.jipsa.domain.repository

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import java.time.LocalDate
import java.time.YearMonth

/** 거래 애그리거트의 아웃바운드 포트. */
interface TransactionRepository {

    fun save(transaction: Transaction): Transaction

    fun saveAll(transactions: List<Transaction>): List<Transaction>

    fun findById(id: TransactionId): Transaction?

    /** 조건에 맞는 거래. 소비일 내림차순, 같으면 ID 내림차순(최근 등록 먼저). */
    fun search(criteria: TransactionSearchCriteria): List<Transaction>

    fun deleteById(id: TransactionId)

    fun deleteAllByInstallmentPlanId(installmentPlanId: InstallmentPlanId)

    /** [fixedExpenseId] 의 거래 중 소비일이 [date] 보다 뒤인 것을 지운다. 고정비 종료에 쓴다. */
    fun deleteAllByFixedExpenseIdAndSpentDateAfter(fixedExpenseId: FixedExpenseId, date: LocalDate)

    fun existsByCategoryId(categoryId: CategoryId): Boolean

    /** [month] 에 이미 거래가 기록된 고정비 식별자. 고정비 기록의 멱등성 판단에 쓴다. */
    fun findRecordedFixedExpenseIds(month: YearMonth): Set<FixedExpenseId>
}
