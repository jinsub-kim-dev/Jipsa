package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.checkInvariant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 카드 이용내역 (PRD 3.5). 카드사에서 내려받은 승인 내역이며, 가져오기의 입력이다.
 *
 * 저장하지 않는다. [purchases] 로 실제 구매를 뽑아 거래·할부 계획으로 옮기는 것이 전부다.
 * 파일 형식(엑셀 열 이름 등)은 이 모델이 모른다. 형식은 읽는 쪽(프레젠테이션)이 해석한다.
 */
data class CardStatement(
    val paymentMethod: String,
    val entries: List<CardStatementEntry>,
) {

    init {
        checkInvariant(paymentMethod.isNotBlank()) { "카드 이용내역의 결제 수단은 비어 있을 수 없습니다." }
        ExpenseText.checkPaymentMethod(paymentMethod)
    }

    /**
     * 승인번호와 사용처로 묶어 취소를 반영한 구매 목록. 파일에 나온 순서를 따른다.
     *
     * - 묶음의 금액 합이 0원 이하면(전체 취소) 빠진다. [cancellations] 에 들어간다.
     * - 부분 취소면 남은 금액만 남는다.
     * - 소비일·할부 정보는 묶음의 **승인 줄**(양수 금액)에서 가져온다. 취소 줄은 금액만 보탠다.
     */
    fun purchases(): List<CardPurchase> = groups().mapNotNull { it.toPurchase() }

    /** 취소로 빠진 묶음. 가져오지 않은 이유를 보여주기 위해 남긴다. */
    fun cancellations(): List<CardCancellation> = groups().mapNotNull { it.toCancellation() }

    /**
     * 일시불 구매의 소비일 범위. 일시불이 없으면 `null` 이다.
     * 이 기간의 거래만 읽어 중복을 비교하면 된다.
     */
    fun lumpSumPeriod(): ClosedRange<LocalDate>? {
        val dates = purchases().filterIsInstance<CardPurchase.LumpSum>().map { it.spentDate }
        val first = dates.minOrNull() ?: return null
        return first..dates.max()
    }

    private fun groups(): List<EntryGroup> =
        entries.groupBy { it.approvalNumber to it.merchant }.values.map(::EntryGroup)

    private class EntryGroup(private val lines: List<CardStatementEntry>) {

        private val netAmount: Long = lines.sumOf { it.amount }

        /** 승인 줄. 취소 줄만 있는 묶음(승인이 조회 기간 밖)도 있을 수 있다. */
        private val approval: CardStatementEntry? = lines.firstOrNull { it.amount > 0 }

        fun toPurchase(): CardPurchase? {
            val line = approval ?: return null
            if (netAmount <= 0) return null
            val amount = Money.of(netAmount)
            return if (line.isInstallment) {
                CardPurchase.Installment(line.spentDate, line.merchant, amount, line.installmentMonths)
            } else {
                CardPurchase.LumpSum(line.spentDate, line.merchant, amount)
            }
        }

        /** [toPurchase] 와 정반대다. 묶음 하나는 구매이거나 취소이고, 둘 다이거나 둘 다 아닐 수 없다. */
        fun toCancellation(): CardCancellation? {
            val line = approval ?: return lines.first().let {
                CardCancellation(it.spentDate, it.merchant, approvedAmount = 0, CardCancellation.Kind.ORPHAN)
            }
            if (netAmount > 0) return null
            return CardCancellation(line.spentDate, line.merchant, line.amount, CardCancellation.Kind.FULL)
        }
    }
}

/**
 * 카드 이용내역의 한 줄 (PRD 3.5). 승인이면 금액이 양수, 취소면 음수다.
 *
 * @property installmentMonths 일시불이면 0 또는 1, 할부면 2 이상
 */
data class CardStatementEntry(
    val approvalNumber: String,
    val spentDate: LocalDate,
    val merchant: String,
    val amount: Long,
    val installmentMonths: Int,
) {

    init {
        checkInvariant(approvalNumber.isNotBlank()) { "승인번호는 비어 있을 수 없습니다. 사용처: $merchant" }
        ExpenseText.checkMerchant(merchant)
        checkInvariant(installmentMonths >= 0) {
            "할부 개월 수는 0 이상이어야 합니다. 사용처: $merchant, 입력값: $installmentMonths"
        }
    }

    val isInstallment: Boolean get() = installmentMonths >= InstallmentPlan.MIN_INSTALLMENT_MONTHS
}

/** 취소를 반영한 실제 구매 한 건 (PRD 3.5). */
sealed interface CardPurchase {
    /** 소비일. 승인일자다. */
    val spentDate: LocalDate
    val merchant: String
    val amount: Money

    /** 일시불. */
    data class LumpSum(
        override val spentDate: LocalDate,
        override val merchant: String,
        override val amount: Money,
    ) : CardPurchase

    /**
     * 할부. **첫 납부월과 납부일도 소비일(승인일자)에서 정한다.**
     *
     * 카드 대금의 결제일이 아니라 돈을 쓴 날을 기준으로 한다. 분석 축이 소비일 하나이고(PRD 1절),
     * 결제일을 주지 않는 카드사(우리카드)도 있다. 1회차가 승인한 날에, 이후 회차는 매달 같은 날에 잡힌다.
     */
    data class Installment(
        override val spentDate: LocalDate,
        override val merchant: String,
        override val amount: Money,
        val installmentMonths: Int,
    ) : CardPurchase {
        val firstBillingMonth: YearMonth get() = YearMonth.from(spentDate)
        val billingDay: Int get() = spentDate.dayOfMonth
    }
}

/**
 * 취소로 빠진 승인 (PRD 3.5). 가져오지 않지만, 왜 빠졌는지 보여줘야 하므로 남긴다.
 *
 * @property approvedAmount 원래 승인 금액. 승인 줄이 없으면(ORPHAN) 0 이다
 */
data class CardCancellation(
    val spentDate: LocalDate,
    val merchant: String,
    val approvedAmount: Long,
    val kind: Kind,
) {
    enum class Kind(val reason: String) {
        /** 승인과 취소가 함께 있고, 취소 금액이 승인 금액 이상이다. */
        FULL("승인이 취소되었습니다."),

        /** 취소 줄만 있다. 원래 승인이 조회 기간 밖에 있다. */
        ORPHAN("취소 내역만 있습니다. 원래 승인은 조회 기간 밖에 있습니다."),
    }

    val reason: String get() = kind.reason
}
