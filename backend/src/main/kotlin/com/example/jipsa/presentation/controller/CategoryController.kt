package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.CategoryUseCase
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.presentation.dto.CategoryResponse
import com.example.jipsa.presentation.dto.RegisterCategoryRequest
import com.example.jipsa.presentation.dto.UpdateCategoryRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 카테고리 API. 도메인 모델을 직접 반환하지 않고 응답 DTO 로 변환한다. */
@RestController
@RequestMapping("/api/categories")
class CategoryController(
    private val categoryUseCase: CategoryUseCase,
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterCategoryRequest): ResponseEntity<CategoryResponse> {
        val response = CategoryResponse.from(categoryUseCase.register(request.toCommand()))
        return ResponseEntity.created(URI.create("/api/categories/${response.id}")).body(response)
    }

    @GetMapping
    fun getAll(): List<CategoryResponse> = categoryUseCase.getAll().map(CategoryResponse::from)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: UpdateCategoryRequest): CategoryResponse =
        CategoryResponse.from(categoryUseCase.update(request.toCommand(CategoryId(id))))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = categoryUseCase.delete(CategoryId(id))
}
