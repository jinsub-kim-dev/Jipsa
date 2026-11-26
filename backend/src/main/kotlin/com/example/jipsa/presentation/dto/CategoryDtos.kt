package com.example.jipsa.presentation.dto

import com.example.jipsa.application.port.RegisterCategoryCommand
import com.example.jipsa.application.port.UpdateCategoryCommand
import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/*
 * 검증 어노테이션은 `@field:` 타깃을 명시해야 한다. 컴파일러 옵션
 * `-Xannotation-default-target=param-property` 때문에 타깃을 생략하면 Bean Validation 이 인식하지 못한다.
 * 길이·형식 같은 규칙은 도메인이 검증하고(400 INVARIANT_VIOLATION), 여기서는 필수 여부만 본다.
 */

data class RegisterCategoryRequest(
    @field:NotBlank(message = "카테고리 이름은 필수입니다.")
    val name: String?,
    @field:NotBlank(message = "아이콘은 필수입니다.")
    val icon: String?,
    @field:NotNull(message = "색은 필수입니다. (#RRGGBB)")
    val color: String?,
) {
    fun toCommand(): RegisterCategoryCommand = RegisterCategoryCommand(name!!, icon!!, color!!)
}

/** 값이 없는 필드는 변경하지 않는다. */
data class UpdateCategoryRequest(
    val name: String?,
    val icon: String?,
    val color: String?,
) {
    fun toCommand(id: CategoryId): UpdateCategoryCommand = UpdateCategoryCommand(id, name, icon, color)
}

data class CategoryResponse(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
) {
    companion object {
        fun from(category: Category): CategoryResponse = CategoryResponse(
            id = requireNotNull(category.id) { "영속화되지 않은 카테고리는 응답으로 변환할 수 없습니다." }.value,
            name = category.name,
            icon = category.icon,
            color = category.color,
        )
    }
}
