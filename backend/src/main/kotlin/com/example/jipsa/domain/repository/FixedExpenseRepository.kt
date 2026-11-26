package com.example.jipsa.domain.repository

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId

/** 고정비 애그리거트의 아웃바운드 포트. */
interface FixedExpenseRepository {

    fun save(fixedExpense: FixedExpense): FixedExpense

    fun findById(id: FixedExpenseId): FixedExpense?

    /** 납부일 오름차순, 같으면 ID 오름차순. */
    fun findAll(): List<FixedExpense>

    fun existsByCategoryId(categoryId: CategoryId): Boolean
}
