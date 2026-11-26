package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.model.CardStatementEntry
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * 현대카드 "이용대금명세서" (PRD 3.5).
 *
 * - 확장자는 xls 지만 **실제로는 HTML 표**다. [StatementDocument] 가 표로 읽어 준다.
 * - 승인 내역이 아니라 명세서다. 일시불·할부 구역마다 **소계 줄**이, 끝에 **총 합계 줄**이 있다.
 *   이 줄들은 이용일이 `-` 다. 할부 구역이 소계 뒤에 이어지므로 거기서 멈추지 않고 건너뛴다.
 * - **승인번호가 없다.** 줄마다 따로 번호를 붙이고, 음수 금액(취소) 줄은 같은 사용처·같은 금액의
 *   승인 줄과 짝지어 같은 번호를 준다. 그래야 도메인이 둘을 묶어 뺀다. 짝이 없으면 취소만 남는다.
 * - 할부/회차는 `3/6` 처럼 "이번 회차/전체 개월" 로 나온다. 이용금액이 할부 총액이다.
 *   소비일(이용일) 기준이므로 매달 명세서에 같은 할부가 다시 나와도 중복으로 걸러진다.
 */
@Component
class HyundaiCardStatementReader : TabularCardStatementReader() {

    override val issuer: String = "hyundai"
    override val paymentMethod: String = PAYMENT_METHOD
    override val requiredColumns: List<String> = listOf(SPENT_DATE, MERCHANT, AMOUNT, INSTALLMENT)

    override fun isData(row: Row): Boolean = row.text(SPENT_DATE) != SUMMARY_MARK

    override fun entriesOf(row: Row): List<CardStatementEntry> = listOf(
        CardStatementEntry(
            approvalNumber = "$ROW_APPROVAL_PREFIX${row.rowNumber}",
            spentDate = row.date(SPENT_DATE, DATE_FORMATS) ?: throw CardStatementFormatException("$SPENT_DATE 이(가) 없습니다."),
            merchant = MerchantText.normalize(row.text(MERCHANT)),
            amount = row.amount(AMOUNT),
            installmentMonths = installmentMonths(row.text(INSTALLMENT)),
        ),
    )

    /** 취소(음수) 줄을 같은 사용처·같은 금액의 승인 줄과 짝짓는다. 승인 하나는 취소 하나와만 짝이 된다. */
    override fun complete(entries: List<CardStatementEntry>): List<CardStatementEntry> {
        val paired = mutableSetOf<Int>()
        return entries.map { entry ->
            if (entry.amount >= 0) return@map entry
            val match = entries.indices.firstOrNull { index ->
                val candidate = entries[index]
                index !in paired && candidate.amount == -entry.amount && candidate.merchant == entry.merchant
            } ?: return@map entry
            paired += match
            entry.copy(approvalNumber = entries[match].approvalNumber)
        }
    }

    /** "" → 0(일시불), "3/6" → 6, "6" → 6. */
    private fun installmentMonths(value: String): Int {
        if (value.isEmpty()) return 0
        val total = value.substringAfter('/').filter(Char::isDigit)
        return total.toIntOrNull()
            ?: throw CardStatementFormatException("$INSTALLMENT 을(를) 읽을 수 없습니다. 입력값: $value")
    }

    companion object {
        /** 가져온 거래의 결제 수단 (PRD 3.5). */
        const val PAYMENT_METHOD: String = "현대카드"

        const val SPENT_DATE: String = "이용일"
        const val MERCHANT: String = "이용가맹점"
        const val AMOUNT: String = "이용금액"
        const val INSTALLMENT: String = "할부/회차"

        /** 소계·합계 줄의 이용일. */
        private const val SUMMARY_MARK = "-"
        private const val ROW_APPROVAL_PREFIX = "row-"

        private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ISO_LOCAL_DATE,
        )
    }
}
