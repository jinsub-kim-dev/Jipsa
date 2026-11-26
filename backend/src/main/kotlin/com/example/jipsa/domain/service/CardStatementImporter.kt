package com.example.jipsa.domain.service

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.model.CardPurchase
import com.example.jipsa.domain.model.CardStatement
import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.FixedExpense
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlan
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

/**
 * 카드 이용내역을 거래·할부 계획으로 옮길 계획을 세우는 도메인 서비스 (PRD 3.5).
 *
 * 이미 있는 거래·할부 계획, 카테고리 목록, 분류 규칙을 함께 봐야 하므로 애그리거트 하나가 아니라
 * 도메인 서비스에 둔다. 저장은 하지 않는다. 무엇을 만들지만 정한다.
 *
 * ## 중복 판단
 * 승인번호를 저장하지 않으므로 **내용으로 비교**한다. 같은 내용이 여러 번이면 **개수까지** 맞춘다.
 * 같은 날 같은 가게에서 같은 금액을 두 번 결제한 경우, 처음 가져올 때는 둘 다 들어가고
 * 다시 가져올 때는 둘 다 건너뛴다.
 * 결제 수단도 비교한다. 카드가 여럿이면 같은 날 같은 가게에서 같은 금액을 다른 카드로 낼 수 있다.
 *
 * ## 고정비
 * 등록된 고정비와 사용처가 같은 일시불은 그 달의 고정비 거래가 된다. 한 달에 하나다.
 * 자동 기록(납부일·고정비 금액 그대로)만 있으면 그것을 카드의 실제값으로 고친다. 자동 기록은 예상값이다.
 *
 * ## 처리하지 못한 구매
 * 거래·할부 계획의 불변식에 걸리는 구매(예: 61개월 할부)는 **그 구매만** 빼고 사유를 남긴다.
 * 한 건 때문에 파일 전체를 거부하면 나머지를 가져올 길이 없다.
 */
class CardStatementImporter(private val classifier: MerchantCategoryClassifier) {

    /**
     * @param categories 현재 카테고리 전체. 분류 규칙의 카테고리 이름을 식별자로 바꾸는 데 쓴다
     * @param uncategorizedId 미분류 카테고리. 호출하는 쪽이 미리 만들어 둔다
     * @param existingTransactions [CardStatement.lumpSumPeriod] 의 **달 전체**에 이미 있는 거래.
     *   고정비의 자동 기록은 납부일에 있어 이용내역 기간 밖일 수 있다
     * @param existingPlans 이미 있는 할부 계획 전체
     * @param fixedExpenses 등록된 고정비 전체
     */
    fun plan(
        statement: CardStatement,
        categories: List<Category>,
        uncategorizedId: CategoryId,
        existingTransactions: List<Transaction>,
        existingPlans: List<InstallmentPlan>,
        fixedExpenses: List<FixedExpense> = emptyList(),
    ): CardImportPlan {
        val categoryIdByName: Map<String, CategoryId> =
            categories.mapNotNull { category -> category.id?.let { category.name to it } }.toMap()
        val categoryOf = { merchant: String ->
            classifier.categoryNameOf(merchant)?.let(categoryIdByName::get) ?: uncategorizedId
        }
        val paymentMethod = statement.paymentMethod

        val remainingTransactions =
            Remaining(existingTransactions.map { LumpSumKey(it.spentDate, it.merchant, it.amount, it.paymentMethod) })
        val remainingPlans = Remaining(
            existingPlans.map {
                InstallmentKey(it.merchant, it.totalAmount, it.installmentMonths, it.firstBillingMonth, it.paymentMethod)
            },
        )

        val fixedOccurrences = FixedOccurrences(fixedExpenses, existingTransactions)
        val transactions = mutableListOf<Transaction>()
        val fixedOccurrencesToCreate = mutableListOf<Transaction>()
        val corrections = mutableListOf<Transaction>()
        val fixedMatches = mutableListOf<CardPurchase>()
        val plans = mutableListOf<InstallmentPlan>()
        val duplicates = mutableListOf<CardPurchase>()
        val uncategorized = mutableListOf<CardPurchase>()
        val unprocessed = statement.cancellations()
            .map { UnprocessedPurchase(it.spentDate, it.merchant, it.approvedAmount, it.reason) }
            .toMutableList()

        for (purchase in statement.purchases()) {
            val isDuplicate = when (purchase) {
                is CardPurchase.LumpSum -> remainingTransactions.consume(
                    LumpSumKey(purchase.spentDate, purchase.merchant, purchase.amount, paymentMethod),
                )
                is CardPurchase.Installment -> remainingPlans.consume(
                    InstallmentKey(purchase.merchant, purchase.amount, purchase.installmentMonths, purchase.firstBillingMonth, paymentMethod),
                )
            }
            if (isDuplicate) {
                // 자동 기록과 똑같은 구매도 중복이다. 그 달의 고정비 거래는 정해졌으니 다른 구매가 고치지 않게 한다.
                if (purchase is CardPurchase.LumpSum) fixedOccurrences.markDuplicate(purchase)
                duplicates += purchase
                continue
            }

            val fixedMatch = (purchase as? CardPurchase.LumpSum)?.let(fixedOccurrences::claim)
            if (fixedMatch != null) {
                when (fixedMatch) {
                    is FixedMatch.New -> fixedOccurrencesToCreate += fixedMatch.transaction
                    is FixedMatch.Correction -> corrections += fixedMatch.transaction
                }
                fixedMatches += purchase
                continue
            }

            val categoryId = categoryOf(purchase.merchant)
            try {
                when (purchase) {
                    is CardPurchase.LumpSum -> transactions += Transaction.createGeneral(
                        categoryId = categoryId,
                        amount = purchase.amount,
                        merchant = purchase.merchant,
                        spentDate = purchase.spentDate,
                        paymentMethod = paymentMethod,
                    )
                    is CardPurchase.Installment -> plans += InstallmentPlan.create(
                        categoryId = categoryId,
                        merchant = purchase.merchant,
                        totalAmount = purchase.amount,
                        installmentMonths = purchase.installmentMonths,
                        firstBillingMonth = purchase.firstBillingMonth,
                        billingDay = purchase.billingDay,
                        paymentMethod = paymentMethod,
                    )
                }
            } catch (e: InvariantViolationException) {
                unprocessed += UnprocessedPurchase(purchase.spentDate, purchase.merchant, purchase.amount.amount, e.message.orEmpty())
                continue
            }
            if (categoryId == uncategorizedId) uncategorized += purchase
        }

        return CardImportPlan(
            transactions = transactions,
            fixedExpenseOccurrences = fixedOccurrencesToCreate,
            fixedExpenseCorrections = corrections,
            fixedExpenseMatches = fixedMatches,
            installmentPlans = plans,
            duplicates = duplicates,
            uncategorized = uncategorized,
            unprocessed = unprocessed,
        )
    }

    private data class LumpSumKey(val spentDate: LocalDate, val merchant: String, val amount: Money, val paymentMethod: String?)

    private data class InstallmentKey(
        val merchant: String,
        val totalAmount: Money,
        val installmentMonths: Int,
        val firstBillingMonth: YearMonth,
        val paymentMethod: String?,
    )

    private sealed interface FixedMatch {
        val transaction: Transaction

        /** 그 달에 고정비 거래가 없어 새로 만든다. */
        data class New(override val transaction: Transaction) : FixedMatch

        /** 그 달의 자동 기록을 실제값으로 고친다. */
        data class Correction(override val transaction: Transaction) : FixedMatch
    }

    /** 고정비별·달별로 고정비 거래가 이미 정해졌는지 기억한다. 한 달에 하나만 두기 위함이다. */
    private class FixedOccurrences(
        private val fixedExpenses: List<FixedExpense>,
        existingTransactions: List<Transaction>,
    ) {
        private val existingByMonth: Map<Pair<FixedExpenseId?, YearMonth>, Transaction> = existingTransactions
            .filter { it.fixedExpenseId != null }
            .associateBy { it.fixedExpenseId to YearMonth.from(it.spentDate) }
        private val taken = mutableSetOf<Pair<FixedExpenseId?, YearMonth>>()

        /** 이미 있어서 건너뛴 [purchase] 가 고정비의 것이면 그 달은 정해진 것으로 본다. */
        fun markDuplicate(purchase: CardPurchase.LumpSum) {
            fixedExpenseOf(purchase)?.let { taken += it.id to YearMonth.from(purchase.spentDate) }
        }

        private fun fixedExpenseOf(purchase: CardPurchase.LumpSum): FixedExpense? {
            val month = YearMonth.from(purchase.spentDate)
            return fixedExpenses.firstOrNull { it.isActiveIn(month) && it.isFor(purchase.merchant) }
        }

        /** [purchase] 를 고정비 거래로 만든다. 맞는 고정비가 없거나 그 달의 고정비 거래가 이미 실제값이면 `null` 이다. */
        fun claim(purchase: CardPurchase.LumpSum): FixedMatch? {
            val fixedExpense = fixedExpenseOf(purchase) ?: return null
            val key = fixedExpense.id to YearMonth.from(purchase.spentDate)
            if (key in taken) return null
            val existing = existingByMonth[key]
            val match = when {
                existing == null -> FixedMatch.New(fixedExpense.actualOccurrence(purchase.spentDate, purchase.amount))
                fixedExpense.isScheduledOccurrence(existing) ->
                    FixedMatch.Correction(existing.correctAmount(purchase.amount).reschedule(purchase.spentDate))
                else -> return null
            }
            taken += key
            return match
        }
    }

    /** 이미 있는 것의 개수. 하나 맞출 때마다 하나씩 줄여 개수까지 비교한다. */
    private class Remaining<K>(keys: List<K>) {
        private val counts: MutableMap<K, Int> = keys.groupingBy { it }.eachCount().toMutableMap()

        fun consume(key: K): Boolean {
            val count = counts[key] ?: return false
            if (count == 1) counts.remove(key) else counts[key] = count - 1
            return true
        }
    }
}

/**
 * 카드 이용내역 가져오기 계획 (PRD 3.5).
 *
 * @property transactions 새로 만들 일반 지출. 아직 저장되지 않았다
 * @property fixedExpenseOccurrences 새로 만들 고정비 거래. 아직 저장되지 않았다
 * @property fixedExpenseCorrections 실제값으로 고친 고정비 자동 기록. 이미 저장된 거래다
 * @property fixedExpenseMatches 고정비 거래가 된 구매(새로 만들었거나 자동 기록을 고쳤다)
 * @property installmentPlans 새로 등록할 할부 계획. 저장한 뒤 회차 거래를 만든다
 * @property duplicates 이미 있어서 건너뛴 구매
 * @property uncategorized 새로 만들 것 중 미분류로 들어간 구매
 * @property unprocessed 가져오지 않은 구매와 그 사유 (취소, 불변식 위반)
 */
data class CardImportPlan(
    val transactions: List<Transaction>,
    val fixedExpenseOccurrences: List<Transaction>,
    val fixedExpenseCorrections: List<Transaction>,
    val fixedExpenseMatches: List<CardPurchase>,
    val installmentPlans: List<InstallmentPlan>,
    val duplicates: List<CardPurchase>,
    val uncategorized: List<CardPurchase>,
    val unprocessed: List<UnprocessedPurchase>,
)

/**
 * 가져오지 않은 구매 (PRD 3.5).
 *
 * @property amount 원래 금액(원). 취소만 있는 경우 0 이다
 */
data class UnprocessedPurchase(
    val spentDate: LocalDate,
    val merchant: String,
    val amount: Long,
    val reason: String,
)
