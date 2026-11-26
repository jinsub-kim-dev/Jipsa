package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.model.CardStatementEntry
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * 우리카드 "국내 거래승인내역" 엑셀(xls) (PRD 3.5).
 *
 * - 머리글 위에 제목·회원 정보(마스킹된 주민번호·성명)·이용기간이 있다. 읽지 않는다.
 * - 데이터 아래에 합계 줄("국내 승인건수" 등)이 있다. "No" 열이 숫자가 아닌 줄에서 멈춘다.
 * - 가맹점명에 **전각 문자**가 섞여 있다(`쿠팡　주식회사`, `ＡＫ　ＰＬＡＺＡ`). 반각으로 바꾼다.
 *   그대로 두면 화면에서 어색하고, 같은 가게가 다른 사용처로 보인다.
 * - 취소는 음수 줄이 아니라 **취소일 열**로 표시된다. 취소일이 있으면 같은 승인번호의 취소 건을 더해
 *   도메인이 승인과 묶어 빼게 한다.
 * - 결제일 열이 없다. 할부도 소비일(승인일자) 기준이므로 필요 없다(PRD 3.5).
 */
@Component
class WooriCardStatementReader : TabularCardStatementReader() {

    override val issuer: String = "woori"
    override val paymentMethod: String = PAYMENT_METHOD
    override val requiredColumns: List<String> =
        listOf(NO, APPROVAL_NUMBER, SPENT_DATE, AMOUNT, INSTALLMENT_MONTHS, CANCELLED_DATE, MERCHANT)

    override fun isEnd(row: Row): Boolean = row.text(NO).toIntOrNull() == null

    override fun entriesOf(row: Row): List<CardStatementEntry> {
        val approval = CardStatementEntry(
            approvalNumber = row.text(APPROVAL_NUMBER),
            spentDate = row.date(SPENT_DATE, DATE_FORMATS) ?: throw CardStatementFormatException("$SPENT_DATE 가 없습니다."),
            merchant = MerchantText.normalize(row.text(MERCHANT)),
            amount = row.amount(AMOUNT),
            installmentMonths = installmentMonths(row.text(INSTALLMENT_MONTHS)),
        )
        val cancelled = row.text(CANCELLED_DATE).let { it.isNotEmpty() && it != NOT_CANCELLED }
        return if (cancelled) listOf(approval, approval.copy(amount = -approval.amount)) else listOf(approval)
    }

    /** "" → 0(일시불), "3", "03", "3개월" → 3. */
    private fun installmentMonths(value: String): Int {
        if (value.isEmpty()) return 0
        return value.filter(Char::isDigit).toIntOrNull()
            ?: throw CardStatementFormatException("$INSTALLMENT_MONTHS 이(가) 숫자가 아닙니다. 입력값: $value")
    }

    companion object {
        /** 가져온 거래의 결제 수단 (PRD 3.5). */
        const val PAYMENT_METHOD: String = "우리카드"

        const val NO: String = "No"
        const val APPROVAL_NUMBER: String = "승인번호"
        const val SPENT_DATE: String = "승인일자"
        const val AMOUNT: String = "승인금액"
        const val INSTALLMENT_MONTHS: String = "할부"
        const val CANCELLED_DATE: String = "취소일"
        const val MERCHANT: String = "가맹점"

        private const val NOT_CANCELLED = "-"

        /** "2026/11/25 11:18:25" — 시각은 버린다. */
        private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("yyyy/MM/dd[ HH:mm[:ss]]"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )
    }
}
