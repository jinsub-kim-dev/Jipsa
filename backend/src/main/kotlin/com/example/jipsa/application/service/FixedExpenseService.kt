package com.example.jipsa.application.service

import com.example.jipsa.application.port.FixedExpenseUseCase
import com.example.jipsa.application.port.RegisterFixedExpenseCommand
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.domain.service.FixedExpenseRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.YearMonth

/**
 * 고정비 유스케이스 오케스트레이션.
 *
 * 등록·종료·기록은 고정비와 거래 두 애그리거트를 함께 바꾼다. 고정비의 정의와 그 기록이
 * 어긋난 상태(종료했는데 다음 달 거래가 남음, 등록했는데 이번 달 기록이 없음)가 보이지 않도록
 * 한 트랜잭션에서 처리한다 (backend/CLAUDE.md 4.1 예외).
 */
@Service
@Transactional(readOnly = true)
class FixedExpenseService(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val fixedExpenseRecorder: FixedExpenseRecorder,
    private val clock: Clock,
) : FixedExpenseUseCase {

    @Transactional
    override fun register(command: RegisterFixedExpenseCommand): FixedExpense {
        categoryRepository.findById(command.categoryId)
            ?: throw ResourceNotFoundException("카테고리", command.categoryId)

        val saved = fixedExpenseRepository.save(
            FixedExpense.create(
                categoryId = command.categoryId,
                merchant = command.merchant,
                amount = command.amount,
                dayOfMonth = command.dayOfMonth,
                startMonth = command.startMonth,
                tags = command.tags,
                paymentMethod = command.paymentMethod,
            ),
        )
        recordFor(YearMonth.now(clock))
        return saved
    }

    override fun getAll(): List<FixedExpense> = fixedExpenseRepository.findAll()

    @Transactional
    override fun terminate(id: FixedExpenseId, lastMonth: YearMonth): FixedExpense {
        val fixedExpense = fixedExpenseRepository.findById(id) ?: throw ResourceNotFoundException("고정비", id)
        val terminated = fixedExpenseRepository.save(fixedExpense.terminate(lastMonth))
        transactionRepository.deleteAllByFixedExpenseIdAndSpentDateAfter(id, lastMonth.atEndOfMonth())
        return terminated
    }

    @Transactional
    override fun recordFor(month: YearMonth): Int {
        val records = fixedExpenseRecorder.recordsFor(
            month = month,
            fixedExpenses = fixedExpenseRepository.findAll(),
            alreadyRecorded = transactionRepository.findRecordedFixedExpenseIds(month),
        )
        if (records.isNotEmpty()) transactionRepository.saveAll(records)
        return records.size
    }
}
