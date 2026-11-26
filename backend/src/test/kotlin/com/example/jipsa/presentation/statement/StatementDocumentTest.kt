package com.example.jipsa.presentation.statement

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("이용내역 파일을 표로 읽기")
class StatementDocumentTest {

    private fun bytes(workbook: Workbook) = ByteArrayOutputStream().also { workbook.use { wb -> wb.write(it) } }.toByteArray()

    private fun open(bytes: ByteArray) = StatementDocument.open(ByteArrayInputStream(bytes))

    @Test
    fun `xlsx 는 시트마다 표 하나다`() {
        val workbook = XSSFWorkbook().apply {
            createSheet("요약").createRow(0).createCell(0).setCellValue("조회기간")
            createSheet("상세").createRow(0).createCell(0).setCellValue("승인일자")
        }

        val document = open(bytes(workbook))

        assertThat(document.tables.map { it.rows.single().texts }).containsExactly(listOf("조회기간"), listOf("승인일자"))
    }

    @Test
    fun `xls 의 숫자 칸은 숫자를, 날짜 칸은 날짜를 함께 준다`() {
        // given
        val workbook = HSSFWorkbook()
        val dateStyle = workbook.createCellStyle().apply { dataFormat = workbook.creationHelper.createDataFormat().getFormat("yyyy-mm-dd") }
        workbook.createSheet("내역").createRow(2).apply {
            createCell(1).setCellValue(10000.0)
            createCell(3).apply {
                setCellValue(java.util.Date.from(LocalDate.of(2026, 11, 25).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant()))
                cellStyle = dateStyle
            }
        }

        // when
        val row = open(bytes(workbook)).tables.single().rows.single()

        // then
        assertThat(row.rowNumber).isEqualTo(3)
        assertThat(row.cell(0)).isEqualTo(StatementCell.EMPTY)
        assertThat(row.cell(1).number).isEqualByComparingTo(BigDecimal("10000"))
        assertThat(row.cell(3).date).isEqualTo(LocalDate.of(2026, 11, 25))
        assertThat(row.cell(99)).isEqualTo(StatementCell.EMPTY)
    }

    @Test
    fun `확장자가 무엇이든 HTML 표면 표로 읽는다`() {
        val html = "\r\n\r\n<html><body><table><tr><th>이용일</th><td>  2026년 08월 17일 </td></tr></table></body></html>"

        val row = open(html.toByteArray()).tables.single().rows.single()

        assertThat(row.texts).containsExactly("이용일", "2026년 08월 17일")
        assertThat(row.cell(0).number).isNull()
    }

    @Test
    fun `colspan 은 빈 칸으로 채워 열 위치를 맞춘다`() {
        val html = "<table><tr><th colspan=\"3\">제목</th><td>끝</td></tr></table>"

        val row = open(html.toByteArray()).tables.single().rows.single()

        assertThat(row.texts).containsExactly("제목", "", "", "끝")
    }

    @Test
    fun `닫는 태그에 공백이 있어도 칸을 나눈다`() {
        val html = "<table><tr><td>코스트코코리아</td   ><td>120,000</td></tr></table>"

        assertThat(open(html.toByteArray()).tables.single().rows.single().texts).containsExactly("코스트코코리아", "120,000")
    }

    @Test
    fun `meta 의 문자 집합으로 글자를 읽는다`() {
        val html = "<html><head><meta charset=\"euc-kr\"></head><body><table><tr><td>코스트코</td></tr></table></body></html>"

        val row = open(html.toByteArray(charset("EUC-KR"))).tables.single().rows.single()

        assertThat(row.texts).containsExactly("코스트코")
    }

    @Test
    fun `HTML 표마다 표 하나이고 행 번호는 표 안의 순서다`() {
        val html = "<table><tr><td>a</td></tr></table><table><tr><td>b</td></tr><tr><td>c</td></tr></table>"

        val tables = open(html.toByteArray()).tables

        assertThat(tables.map { table -> table.rows.map { it.rowNumber } }).containsExactly(listOf(1), listOf(1, 2))
    }

    @Test
    fun `엑셀도 HTML 표도 아니면 읽을 수 없다`() {
        assertThatThrownBy { open("날짜,가게,금액\n".toByteArray()) }
            .isInstanceOf(CardStatementFormatException::class.java)
            .hasMessageContaining("엑셀 파일을 열 수 없습니다")
    }

    @Test
    fun `엑셀 서명인데 깨진 파일이면 읽을 수 없다`() {
        val broken = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01)

        assertThatThrownBy { open(broken) }.isInstanceOf(CardStatementFormatException::class.java)
    }

    @Test
    fun `빈 줄 판단은 모든 칸이 비었는지로 한다`() {
        val html = "<table><tr><td> </td><td></td></tr><tr><td></td><td>x</td></tr></table>"

        val rows = open(html.toByteArray()).tables.single().rows

        assertThat(rows.map { it.isBlank }).containsExactly(true, false)
    }
}
