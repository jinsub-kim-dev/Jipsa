package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.model.CardStatementEntry
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * 삼성카드 "일시불+할부 카드이용내역조회" 엑셀(xlsx) (PRD 3.5).
 *
 * - 요약 시트와 상세 시트가 있다. 상세 시트만 머리글이 맞는다.
 * - 취소는 같은 승인번호의 **음수 금액 줄**로 나온다. 그래서 "취소여부" 열은 읽지 않는다.
 * - 결제일 열은 읽지 않는다. 할부도 소비일(승인일자) 기준이다.
 */
@Component
class SamsungCardStatementReader : TabularCardStatementReader() {

    override val issuer: String = "samsung"
    override val paymentMethod: String = PAYMENT_METHOD
    override val requiredColumns: List<String> =
        listOf(SPENT_DATE, MERCHANT, AMOUNT, INSTALLMENT_MONTHS, APPROVAL_NUMBER)

    override fun entriesOf(row: Row): List<CardStatementEntry> = listOf(
        CardStatementEntry(
            approvalNumber = row.text(APPROVAL_NUMBER),
            spentDate = row.date(SPENT_DATE, DATE_FORMATS) ?: throw CardStatementFormatException("$SPENT_DATE 가 없습니다."),
            merchant = row.text(MERCHANT),
            amount = row.amount(AMOUNT),
            installmentMonths = row.text(INSTALLMENT_MONTHS).ifEmpty { "0" }.toIntOrNull()
                ?: throw CardStatementFormatException("$INSTALLMENT_MONTHS 이(가) 숫자가 아닙니다."),
        ),
    )

    companion object {
        /** 가져온 거래의 결제 수단 (PRD 3.5). */
        const val PAYMENT_METHOD: String = "삼성카드"

        const val SPENT_DATE: String = "승인일자"
        const val MERCHANT: String = "가맹점명"
        const val AMOUNT: String = "승인금액(원)"
        const val INSTALLMENT_MONTHS: String = "할부개월"
        const val APPROVAL_NUMBER: String = "승인번호"

        private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.BASIC_ISO_DATE,
        )
    }
}
