package com.example.jipsa.infrastructure.persistence.query

import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.KindAmounts
import com.example.jipsa.domain.model.Money
import com.example.jipsa.domain.model.MonthlyKindAmounts
import com.example.jipsa.domain.model.Tag
import com.example.jipsa.domain.query.MonthlyCategoryExpense
import com.example.jipsa.domain.query.StatisticsQuery
import com.example.jipsa.domain.query.TagTotal
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.YearMonth

/**
 * [StatisticsQuery] 조회 포트의 네이티브 SQL 구현.
 *
 * 집계는 DB 에서 수행한다. 애그리거트를 모두 읽어 메모리에서 합산하면 데이터가 늘어날수록
 * 감당할 수 없다. 애그리거트 재구성이 목적이 아니므로 조회 결과 타입으로 직접 매핑한다.
 *
 * `Number` 로 받는 이유: JDBC 드라이버가 `SUM` 은 `BigDecimal`, `COUNT` 는 `Long`,
 * `YEAR()` 는 `Integer` 로 넘겨 타입이 일정하지 않다.
 */
@Repository
class StatisticsQueryAdapter(
    private val entityManager: EntityManager,
) : StatisticsQuery {

    override fun monthlyKindAmounts(from: YearMonth, to: YearMonth): List<MonthlyKindAmounts> {
        val rows = entityManager
            .createNativeQuery(
                """
                SELECT YEAR(t.spent_date), MONTH(t.spent_date), t.kind, SUM(t.amount)
                FROM transaction t
                WHERE t.spent_date BETWEEN :from AND :to
                GROUP BY YEAR(t.spent_date), MONTH(t.spent_date), t.kind
                """.trimIndent(),
            )
            .setParameter("from", from.atDay(1))
            .setParameter("to", to.atEndOfMonth())
            .resultList
            .map { it as Array<*> }

        val byMonth = rows
            .groupBy({ YearMonth.of(it[0].asInt(), it[1].asInt()) }) { ExpenseKind.valueOf(it[2] as String) to it[3].asMoney() }
            .mapValues { (_, amounts) -> KindAmounts.of(amounts.toMap()) }

        // 거래가 없는 달도 0원으로 채운다. 추이 차트에서 빈 달이 빠지면 간격이 왜곡된다.
        return generateSequence(from) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(to) }
            .map { MonthlyKindAmounts(it, byMonth[it] ?: KindAmounts.ZERO) }
            .toList()
    }

    override fun monthlyCategoryExpenses(from: YearMonth, to: YearMonth): List<MonthlyCategoryExpense> = entityManager
        .createNativeQuery(
            """
            SELECT YEAR(t.spent_date), MONTH(t.spent_date), t.category_id, SUM(t.amount)
            FROM transaction t
            WHERE t.spent_date BETWEEN :from AND :to
            GROUP BY YEAR(t.spent_date), MONTH(t.spent_date), t.category_id
            """.trimIndent(),
        )
        .setParameter("from", from.atDay(1))
        .setParameter("to", to.atEndOfMonth())
        .resultList
        .map { it as Array<*> }
        .map { row ->
            MonthlyCategoryExpense(
                month = YearMonth.of(row[0].asInt(), row[1].asInt()),
                categoryId = CategoryId(row[2].asLong()),
                amount = row[3].asMoney(),
            )
        }

    override fun tagTotals(from: LocalDate, to: LocalDate, limit: Int): List<TagTotal> = entityManager
        .createNativeQuery(
            """
            SELECT tg.name, SUM(t.amount), COUNT(*)
            FROM transaction_tag tg
                     JOIN transaction t ON t.id = tg.transaction_id
            WHERE t.spent_date BETWEEN :from AND :to
            GROUP BY tg.name
            ORDER BY SUM(t.amount) DESC, tg.name ASC
            LIMIT :limit
            """.trimIndent(),
        )
        .setParameter("from", from)
        .setParameter("to", to)
        .setParameter("limit", limit)
        .resultList
        .map { it as Array<*> }
        .map { row -> TagTotal(Tag(row[0] as String), row[1].asMoney(), row[2].asInt()) }

    private fun Any?.asMoney(): Money = Money.of((this as Number).toLong())

    private fun Any?.asLong(): Long = (this as Number).toLong()

    private fun Any?.asInt(): Int = (this as Number).toInt()
}
