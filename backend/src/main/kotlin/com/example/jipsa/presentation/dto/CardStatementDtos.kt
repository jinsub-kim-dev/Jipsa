package com.example.jipsa.presentation.dto

import com.example.jipsa.application.port.CardImportResult
import com.example.jipsa.domain.model.CardPurchase
import com.example.jipsa.presentation.statement.CardStatementReading
import java.time.LocalDate

/**
 * 카드 이용내역 가져오기 결과 (PRD 3.5).
 *
 * 파일의 줄 하나는 다음 중 하나로 끝난다. 화면이 "왜 이 건이 안 들어왔는지" 를 설명할 수 있게
 * 건수뿐 아니라 목록을 함께 준다.
 * - 추가: 새 거래·할부 계획이 됐다. 그중 일부는 미분류([uncategorized])다
 * - 중복: 이미 있어서 건너뛰었다([duplicateCount])
 * - 처리 안 됨: 취소됐거나 읽을 수 없었다([unprocessed], 사유 포함)
 *
 * 취소는 승인 줄과 취소 줄이 한 건으로 묶이므로 줄 수와 건수가 꼭 같지는 않다.
 */
data class CardImportResponse(
    val fileName: String?,
    val issuer: String,
    val paymentMethod: String,
    /** 파일의 데이터 줄 수. */
    val totalRowCount: Int,
    val importedTransactionCount: Int,
    val importedInstallmentPlanCount: Int,
    val duplicateCount: Int,
    val uncategorizedCategoryId: Long,
    val uncategorized: List<CardImportItemResponse>,
    val unprocessed: List<CardImportUnprocessedResponse>,
) {
    val importedCount: Int get() = importedTransactionCount + importedInstallmentPlanCount
    val uncategorizedCount: Int get() = uncategorized.size
    val unprocessedCount: Int get() = unprocessed.size

    companion object {
        fun of(fileName: String?, reading: CardStatementReading, result: CardImportResult): CardImportResponse =
            CardImportResponse(
                fileName = fileName,
                issuer = reading.issuer,
                paymentMethod = reading.statement.paymentMethod,
                totalRowCount = reading.totalRowCount,
                importedTransactionCount = result.importedTransactionCount,
                importedInstallmentPlanCount = result.importedInstallmentPlanCount,
                duplicateCount = result.duplicates.size,
                uncategorizedCategoryId = result.uncategorizedCategoryId.value,
                uncategorized = result.uncategorized.map(CardImportItemResponse::from),
                // 파일 순서대로 보이도록 읽지 못한 줄(행 번호 있음)을 먼저, 취소·규칙 위반을 뒤에 둔다
                unprocessed = reading.rejectedRows.map {
                    CardImportUnprocessedResponse(it.reason, rowNumber = it.rowNumber, content = it.content)
                } + result.unprocessed.map {
                    CardImportUnprocessedResponse(it.reason, spentDate = it.spentDate, merchant = it.merchant, amount = it.amount)
                },
            )
    }
}

data class CardImportItemResponse(
    val spentDate: LocalDate,
    val merchant: String,
    val amount: Long,
    /** 할부면 개월 수. 일시불이면 없다. */
    val installmentMonths: Int?,
) {
    companion object {
        fun from(purchase: CardPurchase): CardImportItemResponse = CardImportItemResponse(
            spentDate = purchase.spentDate,
            merchant = purchase.merchant,
            amount = purchase.amount.amount,
            installmentMonths = (purchase as? CardPurchase.Installment)?.installmentMonths,
        )
    }
}

/**
 * 가져오지 않은 것. 읽지 못한 줄은 [rowNumber]·[content] 를, 읽었지만 들이지 않은 구매는
 * [spentDate]·[merchant]·[amount] 를 갖는다.
 */
data class CardImportUnprocessedResponse(
    val reason: String,
    val rowNumber: Int? = null,
    val content: String? = null,
    val spentDate: LocalDate? = null,
    val merchant: String? = null,
    val amount: Long? = null,
)
