package com.example.jipsa.application.port

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Tags
import com.example.jipsa.domain.model.Transaction
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import java.time.LocalDate

/**
 * 거래 인바운드 포트.
 *
 * 직접 등록할 수 있는 거래는 일반 지출뿐이다. 고정비·할부 거래는 각 정의를 등록할 때 만들어진다 (PRD 3.1).
 */
interface TransactionUseCase {

    fun registerGeneral(command: RegisterGeneralTransactionCommand): Transaction

    fun update(command: UpdateTransactionCommand): Transaction

    fun getById(id: TransactionId): Transaction

    fun search(criteria: TransactionSearchCriteria): List<Transaction>

    fun delete(id: TransactionId)
}

data class RegisterGeneralTransactionCommand(
    val categoryId: CategoryId,
    val amount: Money,
    val merchant: String,
    val spentDate: LocalDate,
    val tags: Tags,
    val memo: String?,
    val paymentMethod: String?,
)

/**
 * 거래 수정 명령. `null` 인 필드는 변경하지 않는다.
 *
 * 메모와 결제 수단은 "지우기" 와 "그대로 두기" 를 구분해야 하므로 [clearMemo] 를 따로 둔다.
 */
data class UpdateTransactionCommand(
    val id: TransactionId,
    val categoryId: CategoryId? = null,
    val amount: Money? = null,
    val merchant: String? = null,
    val spentDate: LocalDate? = null,
    val tags: Tags? = null,
    val memo: String? = null,
    val clearMemo: Boolean = false,
    val paymentMethod: String? = null,
)
