package com.example.jipsa.domain.repository

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.InstallmentPlanId

/** 할부 계획 애그리거트의 아웃바운드 포트. */
interface InstallmentPlanRepository {

    fun save(plan: InstallmentPlan): InstallmentPlan

    fun findById(id: InstallmentPlanId): InstallmentPlan?

    /** 첫 납부월 오름차순, 같으면 ID 오름차순. */
    fun findAll(): List<InstallmentPlan>

    fun existsByCategoryId(categoryId: CategoryId): Boolean

    fun deleteById(id: InstallmentPlanId)
}
