package com.example.jipsa.infrastructure.persistence.jpa

import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.infrastructure.persistence.entity.CategoryJpaEntity
import com.example.jipsa.infrastructure.persistence.entity.FixedExpenseJpaEntity
import com.example.jipsa.infrastructure.persistence.entity.InstallmentPlanJpaEntity
import com.example.jipsa.infrastructure.persistence.entity.TransactionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface CategoryJpaRepository : JpaRepository<CategoryJpaEntity, Long> {

    fun existsByName(name: String): Boolean

    fun findAllByOrderByIdAsc(): List<CategoryJpaEntity>
}

interface FixedExpenseJpaRepository : JpaRepository<FixedExpenseJpaEntity, Long> {

    fun existsByCategoryId(categoryId: Long): Boolean

    fun findAllByOrderByDayOfMonthAscIdAsc(): List<FixedExpenseJpaEntity>
}

interface InstallmentPlanJpaRepository : JpaRepository<InstallmentPlanJpaEntity, Long> {

    fun existsByCategoryId(categoryId: Long): Boolean

    fun findAllByOrderByFirstBillingMonthAscIdAsc(): List<InstallmentPlanJpaEntity>
}

interface TransactionJpaRepository : JpaRepository<TransactionJpaEntity, Long> {

    fun existsByCategoryId(categoryId: Long): Boolean

    /**
     * 검색. 값이 `null` 인 조건은 적용하지 않는다.
     *
     * [keyword] 는 호출부가 소문자 `%...%` 패턴으로 만들어 넘긴다.
     */
    @Query(
        """
        SELECT t FROM TransactionJpaEntity t
        WHERE t.spentDate BETWEEN :from AND :to
          AND (:kind IS NULL OR t.kind = :kind)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:tag IS NULL OR EXISTS (
                SELECT 1 FROM TransactionJpaEntity tt JOIN tt.tags tg WHERE tt.id = t.id AND tg = :tag))
          AND (:keyword IS NULL
               OR LOWER(t.merchant) LIKE :keyword
               OR LOWER(COALESCE(t.memo, '')) LIKE :keyword
               OR EXISTS (
                SELECT 1 FROM TransactionJpaEntity tk JOIN tk.tags kg WHERE tk.id = t.id AND LOWER(kg) LIKE :keyword))
        ORDER BY t.spentDate DESC, t.id DESC
        """,
    )
    fun search(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("kind") kind: ExpenseKind?,
        @Param("categoryId") categoryId: Long?,
        @Param("tag") tag: String?,
        @Param("keyword") keyword: String?,
    ): List<TransactionJpaEntity>

    @Query(
        """
        SELECT DISTINCT t.fixedExpenseId FROM TransactionJpaEntity t
        WHERE t.fixedExpenseId IS NOT NULL AND t.spentDate BETWEEN :from AND :to
        """,
    )
    fun findFixedExpenseIdsBetween(@Param("from") from: LocalDate, @Param("to") to: LocalDate): List<Long>

    /** 태그 행은 DB 의 ON DELETE CASCADE 가 지운다. 일괄 삭제 후 영속성 컨텍스트를 비운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TransactionJpaEntity t WHERE t.installmentPlanId = :planId")
    fun deleteAllByInstallmentPlanId(@Param("planId") planId: Long): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TransactionJpaEntity t WHERE t.fixedExpenseId = :fixedExpenseId AND t.spentDate > :date")
    fun deleteAllByFixedExpenseIdAndSpentDateAfter(
        @Param("fixedExpenseId") fixedExpenseId: Long,
        @Param("date") date: LocalDate,
    ): Int
}
