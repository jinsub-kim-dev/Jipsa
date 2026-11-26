package com.example.jipsa.infrastructure.persistence.entity

import com.example.jipsa.domain.model.ExpenseKind
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.LocalDate

/**
 * 거래 영속화 엔티티.
 *
 * 태그는 별도 테이블(`transaction_tag`)에 둔다. 태그별 집계를 DB 의 GROUP BY 로 하기 위해서다.
 * 목록 조회에서 태그를 거래마다 따로 읽지 않도록 [BatchSize] 로 묶어 읽는다.
 */
@Entity
@Table(name = "transaction")
class TransactionJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    var kind: ExpenseKind,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Column(name = "merchant", nullable = false, length = 100)
    var merchant: String,

    @Column(name = "spent_date", nullable = false)
    var spentDate: LocalDate,

    @Column(name = "memo", length = 200)
    var memo: String? = null,

    @Column(name = "payment_method", length = 30)
    var paymentMethod: String? = null,

    @Column(name = "fixed_expense_id")
    var fixedExpenseId: Long? = null,

    @Column(name = "installment_plan_id")
    var installmentPlanId: Long? = null,

    @Column(name = "installment_sequence")
    var installmentSequence: Int? = null,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "transaction_tag", joinColumns = [JoinColumn(name = "transaction_id")])
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 20)
    @BatchSize(size = 200)
    var tags: MutableList<String> = mutableListOf(),

) : BaseTimeEntity()
