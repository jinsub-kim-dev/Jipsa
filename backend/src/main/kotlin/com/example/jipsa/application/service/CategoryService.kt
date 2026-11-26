package com.example.jipsa.application.service

import com.example.jipsa.application.port.CategoryUseCase
import com.example.jipsa.application.port.RegisterCategoryCommand
import com.example.jipsa.application.port.UpdateCategoryCommand
import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카테고리 유스케이스 오케스트레이션.
 *
 * 이름 유일성과 참조 무결성은 컬렉션 전체·다른 애그리거트를 봐야 하므로 애그리거트 하나로
 * 검증할 수 없다 (backend/CLAUDE.md 4.4 예외). 그 외 규칙은 [Category] 가 갖는다.
 */
@Service
@Transactional(readOnly = true)
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val installmentPlanRepository: InstallmentPlanRepository,
) : CategoryUseCase {

    @Transactional
    override fun register(command: RegisterCategoryCommand): Category {
        val category = Category.create(command.name, command.icon, command.color)
        rejectDuplicateName(category.name)
        return categoryRepository.save(category)
    }

    @Transactional
    override fun update(command: UpdateCategoryCommand): Category {
        val category = getById(command.id)

        val renamed = command.name?.trim()?.let { newName ->
            if (newName != category.name) rejectDuplicateName(newName)
            category.rename(newName)
        } ?: category

        val updated = if (command.icon != null || command.color != null) {
            renamed.changeAppearance(command.icon ?: renamed.icon, command.color ?: renamed.color)
        } else {
            renamed
        }
        return categoryRepository.save(updated)
    }

    override fun getAll(): List<Category> = categoryRepository.findAll()

    @Transactional
    override fun delete(id: CategoryId) {
        val category = getById(id)
        val inUse = transactionRepository.existsByCategoryId(id) ||
            fixedExpenseRepository.existsByCategoryId(id) ||
            installmentPlanRepository.existsByCategoryId(id)
        if (inUse) {
            throw DomainStateException(
                "이 카테고리를 쓰는 거래·고정비·할부가 있어 삭제할 수 없습니다. 카테고리: ${category.name}",
            )
        }
        categoryRepository.deleteById(id)
    }

    private fun getById(id: CategoryId): Category =
        categoryRepository.findById(id) ?: throw ResourceNotFoundException("카테고리", id)

    private fun rejectDuplicateName(name: String) {
        if (categoryRepository.existsByName(name)) {
            throw DomainStateException("이미 있는 카테고리 이름입니다: $name")
        }
    }
}
