package com.example.jipsa.application.service

import com.example.jipsa.application.port.CardImportResult
import com.example.jipsa.application.port.CardStatementImportUseCase
import com.example.jipsa.domain.model.CardStatement
import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.domain.service.CardStatementImporter
import com.example.jipsa.domain.service.MerchantCategoryClassifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카드 이용내역 가져오기 오케스트레이션 (PRD 3.5).
 *
 * 무엇을 만들지는 [CardStatementImporter] 가 정하고, 이 서비스는 읽고 저장만 한다.
 *
 * 한 유스케이스에서 거래·할부 계획·카테고리(미분류) 세 애그리거트를 바꾼다 (backend/CLAUDE.md 4.1 예외).
 * 파일 하나는 전부 들어가거나 전부 들어가지 않아야 한다. 절반만 들어간 뒤 다시 가져오면
 * 내용 비교가 어디까지 들어갔는지 맞혀야 하는데, 할부 회차가 일부만 남는 경우까지 생긴다.
 */
@Service
@Transactional(readOnly = true)
class CardStatementImportService(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val installmentPlanRepository: InstallmentPlanRepository,
    private val cardStatementImporter: CardStatementImporter,
) : CardStatementImportUseCase {

    @Transactional
    override fun importStatement(statement: CardStatement): CardImportResult {
        val categories = categoryRepository.findAll()
        val uncategorizedId = uncategorizedIdIn(categories)
        val existingTransactions = statement.lumpSumPeriod()
            ?.let { transactionRepository.search(TransactionSearchCriteria(from = it.start, to = it.endInclusive)) }
            .orEmpty()

        val plan = cardStatementImporter.plan(
            statement = statement,
            categories = categories,
            uncategorizedId = uncategorizedId,
            existingTransactions = existingTransactions,
            existingPlans = installmentPlanRepository.findAll(),
        )

        transactionRepository.saveAll(plan.transactions)
        plan.installmentPlans.forEach { installmentPlan ->
            val saved = installmentPlanRepository.save(installmentPlan)
            transactionRepository.saveAll(saved.scheduleParts())
        }

        return CardImportResult(
            importedTransactionCount = plan.transactions.size,
            importedInstallmentPlanCount = plan.installmentPlans.size,
            duplicates = plan.duplicates,
            uncategorized = plan.uncategorized,
            uncategorizedCategoryId = uncategorizedId,
            unprocessed = plan.unprocessed,
        )
    }

    /** 미분류 카테고리의 식별자. 없으면 만든다. */
    private fun uncategorizedIdIn(categories: List<Category>): CategoryId {
        val existing = categories.firstOrNull { it.name == MerchantCategoryClassifier.UNCATEGORIZED_NAME }
        val category = existing ?: categoryRepository.save(
            Category.create(
                MerchantCategoryClassifier.UNCATEGORIZED_NAME,
                MerchantCategoryClassifier.UNCATEGORIZED_ICON,
                MerchantCategoryClassifier.UNCATEGORIZED_COLOR,
            ),
        )
        return requireNotNull(category.id) { "영속화된 카테고리에는 식별자가 있어야 합니다." }
    }
}
