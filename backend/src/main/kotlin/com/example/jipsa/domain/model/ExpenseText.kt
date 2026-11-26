package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.checkInvariant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 여러 애그리거트가 공유하는 지출 속성의 규칙 (PRD 3절).
 *
 * 거래·고정비·할부가 모두 사용처와 결제 수단을 갖는다. 규칙이 세 곳에 복사되면
 * 길이 제한 하나를 바꿀 때 반드시 어긋나므로 한 곳에 둔다.
 */
internal object ExpenseText {
    const val MERCHANT_MAX_LENGTH: Int = 100
    const val MEMO_MAX_LENGTH: Int = 200
    const val PAYMENT_METHOD_MAX_LENGTH: Int = 30

    fun checkMerchant(merchant: String) {
        checkInvariant(merchant.isNotBlank()) { "사용처는 비어 있을 수 없습니다." }
        checkInvariant(merchant.length <= MERCHANT_MAX_LENGTH) {
            "사용처는 ${MERCHANT_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${merchant.length}"
        }
    }

    fun checkMemo(memo: String?) {
        if (memo == null) return
        checkInvariant(memo.length <= MEMO_MAX_LENGTH) {
            "메모는 ${MEMO_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${memo.length}"
        }
    }

    fun checkPaymentMethod(paymentMethod: String?) {
        if (paymentMethod == null) return
        checkInvariant(paymentMethod.length <= PAYMENT_METHOD_MAX_LENGTH) {
            "결제 수단은 ${PAYMENT_METHOD_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${paymentMethod.length}"
        }
    }

    /** 빈 문자열은 "값 없음" 으로 정규화한다. 공백 메모가 저장되지 않게 한다. */
    fun normalizeOptional(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)
}

/**
 * 납부일을 그 달에 맞춘다. **그 달에 없는 날이면 말일로 보정한다.** (PRD 3.2, 3.3)
 *
 * 31일 납부 → 4월 30일, 평년 2월 28일, 윤년 2월 29일.
 */
internal fun YearMonth.atDayClamped(dayOfMonth: Int): LocalDate = atDay(minOf(dayOfMonth, lengthOfMonth()))

internal const val MIN_DAY_OF_MONTH: Int = 1
internal const val MAX_DAY_OF_MONTH: Int = 31
