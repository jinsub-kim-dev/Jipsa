package com.example.jipsa.infrastructure.persistence.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.LocalDate

/** 고정비 영속화 엔티티. 연월은 그 달 1일의 날짜로 저장한다. */
@Entity
@Table(name = "fixed_expense")
class FixedExpenseJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Column(name = "merchant", nullable = false, length = 100)
    var merchant: String,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Column(name = "day_of_month", nullable = false)
    var dayOfMonth: Int,

    @Column(name = "payment_method", length = 30)
    var paymentMethod: String? = null,

    @Column(name = "start_month", nullable = false)
    var startMonth: LocalDate,

    @Column(name = "end_month")
    var endMonth: LocalDate? = null,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fixed_expense_tag", joinColumns = [JoinColumn(name = "fixed_expense_id")])
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 20)
    @BatchSize(size = 100)
    var tags: MutableList<String> = mutableListOf(),

) : BaseTimeEntity()
