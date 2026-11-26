package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.domain.model.CardStatement
import com.example.jipsa.domain.model.CardStatementEntry
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 카드사 이용내역 파일을 [CardStatement] 로 읽는 입력 어댑터 (PRD 3.5). */
interface CardStatementReader {
    /** 경로에 쓰는 카드사 이름. 예: `samsung` */
    val issuer: String

    /** 이 카드사의 형식인지. 머리글이 맞는 표가 있으면 그렇다. */
    fun recognizes(document: StatementDocument): Boolean

    /** @throws CardStatementFormatException 이 카드사의 형식이 아닌 경우 */
    fun read(document: StatementDocument): CardStatementReading
}

/**
 * 파일을 읽은 결과.
 *
 * @property totalRowCount 머리글 아래 데이터 줄 수. 빈 줄과 합계·소계 줄은 세지 않는다
 * @property rejectedRows 읽을 수 없어 뺀 줄. 줄 하나 때문에 파일 전체를 거부하지 않는다
 */
data class CardStatementReading(
    val issuer: String,
    val statement: CardStatement,
    val totalRowCount: Int,
    val rejectedRows: List<RejectedRow>,
)

/**
 * 읽을 수 없는 줄.
 *
 * @property rowNumber 파일에서 보이는 행 번호(1부터)
 * @property content 그 줄의 값들. 사용자가 어느 거래인지 알아볼 수 있게 한다
 */
data class RejectedRow(
    val rowNumber: Int,
    val reason: String,
    val content: String,
)

/**
 * 표 형태(엑셀 시트·HTML 표) 이용내역의 공통 읽기.
 *
 * 파일 형식은 입력의 표현일 뿐이므로 도메인이 아니라 이 어댑터가 안다.
 * 열은 위치가 아니라 **머리글 이름**으로 찾는다. 카드사가 열 순서를 바꿔도 이름이 같으면 읽힌다.
 * 머리글이 없는 표(요약 시트 등)와 머리글 위의 줄(제목·회원 정보 등)은 건너뛴다.
 */
abstract class TabularCardStatementReader : CardStatementReader {

    protected abstract val paymentMethod: String

    /** 이 열이 모두 있는 줄을 머리글로 본다. */
    protected abstract val requiredColumns: List<String>

    /** 데이터가 끝났는지. 끝나면 그 뒤의 줄(합계 등)은 읽지 않는다. */
    protected open fun isEnd(row: Row): Boolean = false

    /** 데이터 줄인지. 소계처럼 데이터 사이에 끼어 있는 줄은 건너뛰고 세지 않는다. */
    protected open fun isData(row: Row): Boolean = true

    /** 한 줄을 이용 건으로 바꾼다. 취소가 따로 표시되는 형식은 한 줄이 두 건(승인·취소)이 될 수 있다. */
    protected abstract fun entriesOf(row: Row): List<CardStatementEntry>

    /** 모든 줄을 읽은 뒤 손볼 것. 승인번호가 없는 형식이 취소를 승인과 짝짓는 데 쓴다. */
    protected open fun complete(entries: List<CardStatementEntry>): List<CardStatementEntry> = entries

    override fun recognizes(document: StatementDocument): Boolean = locate(document) != null

    override fun read(document: StatementDocument): CardStatementReading {
        val (table, header) = locate(document) ?: throw CardStatementFormatException(
            "$paymentMethod 이용내역 형식이 아닙니다. 다음 열이 있는 표를 찾지 못했습니다: ${requiredColumns.joinToString()}",
        )
        val rows = table.rows
            .filter { it.rowNumber > header.rowNumber }
            .map { Row(it, header) }
            .filterNot { it.isBlank }
            .takeWhile { !isEnd(it) }
            .filter(::isData)

        val entries = mutableListOf<CardStatementEntry>()
        val rejected = mutableListOf<RejectedRow>()
        for (row in rows) {
            try {
                entries += entriesOf(row)
            } catch (e: CardStatementFormatException) {
                rejected += row.rejected(e.message.orEmpty())
            } catch (e: InvariantViolationException) {
                rejected += row.rejected(e.message.orEmpty())
            }
        }
        return CardStatementReading(issuer, CardStatement(paymentMethod, complete(entries)), rows.size, rejected)
    }

    /** 파일을 열어 읽는다. 카드사를 이미 알 때 쓴다. */
    fun read(input: InputStream): CardStatementReading = read(StatementDocument.open(input))

    private fun locate(document: StatementDocument): Pair<StatementTable, Header>? =
        document.tables.firstNotNullOfOrNull { table -> findHeader(table)?.let { table to it } }

    private fun findHeader(table: StatementTable): Header? =
        table.rows.take(HEADER_SEARCH_ROWS).firstNotNullOfOrNull { row ->
            val columns = row.texts.withIndex()
                .filter { it.value.isNotEmpty() }
                // 같은 이름이 두 번 나오면 앞의 열을 쓴다
                .reversed()
                .associate { (index, name) -> name to index }
            if (requiredColumns.all(columns::containsKey)) Header(row.rowNumber, columns) else null
        }

    protected class Header(val rowNumber: Int, private val columns: Map<String, Int>) {
        fun indexOf(column: String): Int = columns.getValue(column)
    }

    /** 머리글 이름으로 값을 꺼내는 한 줄. */
    protected class Row(private val row: StatementRow, private val header: Header) {

        val rowNumber: Int get() = row.rowNumber

        val isBlank: Boolean get() = row.isBlank

        fun rejected(reason: String): RejectedRow = RejectedRow(
            rowNumber = row.rowNumber,
            reason = reason,
            content = row.texts.filter(String::isNotEmpty).joinToString(" | ").take(CONTENT_MAX_LENGTH),
        )

        private fun cell(column: String): StatementCell = row.cell(header.indexOf(column))

        fun text(column: String): String = cell(column).text

        /** 금액은 원 단위 정수다. 숫자 칸이면 소수부가 없어야 하고, 글자 칸이면 쉼표를 허용한다. */
        fun amount(column: String): Long {
            val cell = cell(column)
            val value = cell.number ?: cell.text.replace(",", "").toBigDecimalOrNull()
                ?: throw CardStatementFormatException("$column 이(가) 숫자가 아닙니다.")
            return try {
                value.longValueExact()
            } catch (e: ArithmeticException) {
                throw CardStatementFormatException("$column 은(는) 원 단위 정수여야 합니다. 입력값: ${value.toPlainString()}", e)
            }
        }

        /** 글자 날짜는 [formats] 로, 엑셀 날짜 칸은 그대로 읽는다. 비어 있으면 `null` 이다. */
        fun date(column: String, formats: List<DateTimeFormatter>): LocalDate? {
            val cell = cell(column)
            cell.date?.let { return it }
            val value = cell.text.ifEmpty { return null }
            return formats.firstNotNullOfOrNull { format -> runCatching { format.parse(value, LocalDate::from) }.getOrNull() }
                ?: throw CardStatementFormatException("$column 을(를) 날짜로 읽을 수 없습니다. 입력값: $value")
        }
    }

    private companion object {
        const val HEADER_SEARCH_ROWS = 30
        const val CONTENT_MAX_LENGTH = 200
    }
}

/** 카드 이용내역 파일을 읽을 수 없는 경우. 400 `INVALID_CARD_STATEMENT` 로 응답한다. */
class CardStatementFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
