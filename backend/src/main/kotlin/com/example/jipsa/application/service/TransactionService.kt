package com.example.jipsa.application.service

import com.example.jipsa.application.port.RegisterGeneralTransactionCommand
import com.example.jipsa.application.port.TransactionUseCase
import com.example.jipsa.application.port.UpdateTransactionCommand
import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 거래 유스케이스 오케스트레이션.
 *
 * 수정 가능 여부(할부 회차 잠금)는 [Transaction] 이 판단한다. 이 서비스는 바뀐 필드만
 * 도메인 메서드로 전달한다.
 */
@Service
@Transactional(readOnly = true)
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : TransactionUseCase {

    @Transactional
    override fun registerGeneral(command: RegisterGeneralTransactionCommand): Transaction {
        ensureCategoryExists(command.categoryId)
        return transactionRepository.save(
            Transaction.createGeneral(
                categoryId = command.categoryId,
                amount = command.amount,
                merchant = command.merchant,
                spentDate = command.spentDate,
                tags = command.tags,
                memo = command.memo,
                paymentMethod = command.paymentMethod,
            ),
        )
    }

    @Transactional
    override fun update(command: UpdateTransactionCommand): Transaction {
        command.categoryId?.let(::ensureCategoryExists)

        var transaction = getById(command.id)
        command.amount?.let { transaction = transaction.correctAmount(it) }
        command.spentDate?.let { transaction = transaction.reschedule(it) }
        command.merchant?.let { transaction = transaction.renameMerchant(it) }
        command.categoryId?.let { transaction = transaction.recategorize(it) }
        command.tags?.let { transaction = transaction.retag(it) }
        if (command.clearMemo) {
            transaction = transaction.rewriteMemo(null)
        } else {
            command.memo?.let { transaction = transaction.rewriteMemo(it) }
        }
        command.paymentMethod?.let { transaction = transaction.changePaymentMethod(it) }

        return transactionRepository.save(transaction)
    }

    override fun getById(id: TransactionId): Transaction =
        transactionRepository.findById(id) ?: throw ResourceNotFoundException("거래", id)

    override fun search(criteria: TransactionSearchCriteria): List<Transaction> =
        transactionRepository.search(criteria)

    /**
     * 일반 지출만 개별 삭제할 수 있다. 판단은 [Transaction.isDeletable] 이 하고,
     * 이 서비스는 거부할 때 무엇을 대신 해야 하는지 안내만 한다.
     */
    @Transactional
    override fun delete(id: TransactionId) {
        val transaction = getById(id)
        if (!transaction.isDeletable) {
            val guide = if (transaction.isInstallmentPart) "할부 계획을 해지하세요." else "금액을 고치거나 고정비를 종료하세요."
            throw DomainStateException("${transaction.kind} 거래는 하나만 삭제할 수 없습니다. $guide 사용처: ${transaction.merchant}")
        }
        transactionRepository.deleteById(id)
    }

    private fun ensureCategoryExists(categoryId: CategoryId) {
        categoryRepository.findById(categoryId) ?: throw ResourceNotFoundException("카테고리", categoryId)
    }
}
