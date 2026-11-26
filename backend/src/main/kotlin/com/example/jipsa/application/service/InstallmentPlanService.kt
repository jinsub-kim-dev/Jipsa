package com.example.jipsa.application.service

import com.example.jipsa.application.port.InstallmentPlanUseCase
import com.example.jipsa.application.port.RegisterInstallmentPlanCommand
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 할부 유스케이스 오케스트레이션.
 *
 * 등록과 해지는 계획과 회차 거래를 함께 바꾼다. 회차가 일부만 남은 할부가 보이지 않도록
 * 한 트랜잭션에서 처리한다 (backend/CLAUDE.md 4.1 예외).
 */
@Service
@Transactional(readOnly = true)
class InstallmentPlanService(
    private val installmentPlanRepository: InstallmentPlanRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : InstallmentPlanUseCase {

    @Transactional
    override fun register(command: RegisterInstallmentPlanCommand): InstallmentPlan {
        categoryRepository.findById(command.categoryId)
            ?: throw ResourceNotFoundException("카테고리", command.categoryId)

        val saved = installmentPlanRepository.save(
            InstallmentPlan.create(
                categoryId = command.categoryId,
                merchant = command.merchant,
                totalAmount = command.totalAmount,
                installmentMonths = command.installmentMonths,
                firstBillingMonth = command.firstBillingMonth,
                billingDay = command.billingDay,
                tags = command.tags,
                paymentMethod = command.paymentMethod,
            ),
        )
        transactionRepository.saveAll(saved.scheduleParts())
        return saved
    }

    override fun getAll(): List<InstallmentPlan> = installmentPlanRepository.findAll()

    @Transactional
    override fun cancel(id: InstallmentPlanId) {
        installmentPlanRepository.findById(id) ?: throw ResourceNotFoundException("할부 계획", id)
        transactionRepository.deleteAllByInstallmentPlanId(id)
        installmentPlanRepository.deleteById(id)
    }
}
