package com.example.jipsa.application.port

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId

/** 카테고리 관리 인바운드 포트. `application/port` 는 인바운드 포트 전용이다. */
interface CategoryUseCase {

    fun register(command: RegisterCategoryCommand): Category

    fun update(command: UpdateCategoryCommand): Category

    fun getAll(): List<Category>

    fun delete(id: CategoryId)
}

data class RegisterCategoryCommand(
    val name: String,
    val icon: String,
    val color: String,
)

/** `null` 인 필드는 변경하지 않는다. 아이콘과 색은 함께 바꾼다. */
data class UpdateCategoryCommand(
    val id: CategoryId,
    val name: String?,
    val icon: String?,
    val color: String?,
)
