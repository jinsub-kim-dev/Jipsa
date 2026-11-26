package com.example.jipsa.presentation.statement

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * 표 형태의 이용내역 파일. 엑셀 시트와 HTML 표를 같은 모양으로 다룬다.
 *
 * 카드사는 "엑셀로 내려받기" 를 눌러도 진짜 엑셀(xls·xlsx)을 주기도 하고, 확장자만 xls 인
 * HTML 표(현대카드)를 주기도 한다. 카드사별 리더가 파일 형식까지 알 필요가 없도록 여기서 맞춘다.
 */
class StatementDocument(val tables: List<StatementTable>) {

    companion object {
        private val OLE2_SIGNATURE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())
        private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        /** @throws CardStatementFormatException 엑셀도 HTML 표도 아닌 경우 */
        fun open(input: InputStream): StatementDocument {
            val bytes = input.readBytes()
            return when {
                bytes.startsWith(OLE2_SIGNATURE) || bytes.startsWith(ZIP_SIGNATURE) -> fromWorkbook(bytes)
                looksLikeHtmlTable(bytes) -> fromHtml(bytes)
                else -> throw notReadable(null)
            }
        }

        private fun fromWorkbook(bytes: ByteArray): StatementDocument {
            val workbook = try {
                WorkbookFactory.create(ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                // POI 는 형식 오류마다 다른 예외를 던진다 (NotOfficeXmlFileException, IOException 등).
                throw notReadable(e)
            }
            return workbook.use(::fromWorkbook)
        }

        fun fromWorkbook(workbook: Workbook): StatementDocument {
            val formatter = DataFormatter()
            return StatementDocument(
                workbook.map { sheet ->
                    StatementTable(
                        sheet.map { row ->
                            val cells = MutableList(maxOf(row.lastCellNum.toInt(), 0)) { StatementCell.EMPTY }
                            for (cell in row) {
                                val text = formatter.formatCellValue(cell).trim()
                                cells[cell.columnIndex] = when {
                                    cell.cellType != CellType.NUMERIC -> StatementCell(text)
                                    DateUtil.isCellDateFormatted(cell) ->
                                        StatementCell(text, date = cell.dateCellValue.toInstant().atZone(ZONE).toLocalDate())
                                    else -> StatementCell(text, number = BigDecimal.valueOf(cell.numericCellValue))
                                }
                            }
                            StatementRow(row.rowNum + 1, cells)
                        },
                    )
                },
            )
        }

        /**
         * HTML 표를 읽는다. 문자 집합은 `<meta charset>` 이나 BOM 으로 알아낸다.
         * `colspan` 은 빈 칸으로 채워 열 위치를 맞춘다. 행 번호는 표 안에서의 순서다.
         */
        private fun fromHtml(bytes: ByteArray): StatementDocument {
            val html = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
            return StatementDocument(
                html.select("table").map { table ->
                    StatementTable(
                        table.select("tr").mapIndexed { index, tr ->
                            val cells = tr.select("th, td").flatMap { cell ->
                                val span = cell.attr("colspan").toIntOrNull()?.coerceIn(1, MAX_COLSPAN) ?: 1
                                listOf(StatementCell(cell.text().trim())) + List(span - 1) { StatementCell.EMPTY }
                            }
                            StatementRow(index + 1, cells)
                        },
                    )
                },
            )
        }

        /** 문자 집합을 몰라도 태그 이름은 ASCII 이므로 바이트 그대로 찾는다. */
        private fun looksLikeHtmlTable(bytes: ByteArray): Boolean =
            "<table" in String(bytes, Charsets.ISO_8859_1).lowercase()

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        private fun notReadable(cause: Throwable?) =
            CardStatementFormatException("엑셀 파일을 열 수 없습니다. 카드사에서 내려받은 엑셀 파일인지 확인하세요.", cause)

        private const val MAX_COLSPAN = 100
    }
}

/** 시트 하나 또는 HTML 표 하나. */
class StatementTable(val rows: List<StatementRow>)

/**
 * 한 줄.
 *
 * @property rowNumber 사람이 보는 행 번호(1부터). 엑셀은 시트의 행 번호, HTML 은 표 안의 순서다
 */
class StatementRow(val rowNumber: Int, private val cells: List<StatementCell>) {

    fun cell(index: Int): StatementCell = cells.getOrElse(index) { StatementCell.EMPTY }

    val texts: List<String> get() = cells.map(StatementCell::text)

    val isBlank: Boolean get() = cells.all { it.text.isEmpty() }
}

/**
 * 칸 하나. 엑셀 숫자 칸은 [number], 엑셀 날짜 칸은 [date] 를 함께 갖는다. HTML 칸은 글자뿐이다.
 */
data class StatementCell(
    val text: String,
    val number: BigDecimal? = null,
    val date: LocalDate? = null,
) {
    companion object {
        val EMPTY = StatementCell("")
    }
}
