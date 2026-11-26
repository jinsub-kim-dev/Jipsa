package com.example.jipsa.domain.repository

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId

/**
 * 카테고리 애그리거트의 아웃바운드 포트.
 *
 * 인터페이스는 도메인에 두고 구현은 인프라에 둔다 (backend/CLAUDE.md 2절).
 */
interface CategoryRepository {

    fun save(category: Category): Category

    fun findById(id: CategoryId): Category?

    fun findAll(): List<Category>

    fun existsByName(name: String): Boolean

    fun deleteById(id: CategoryId)
}
