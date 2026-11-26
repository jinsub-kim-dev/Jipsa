package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.model.CardStatementEntry
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * 삼성카드 이용내역 엑셀 읽기.
 *
 * 실제 이용내역은 개인 금융 정보라 저장소에 두지 않는다. 같은 구조(요약 시트 + 상세 시트,
 * 글자 셀과 숫자 셀이 섞인 열)의 엑셀을 테스트 안에서 만든다.
 */
@DisplayName("삼성카드 이용내역 읽기")
class SamsungCardStatementReaderTest {

    private val reader = SamsungCardStatementReader()

    private val header = listOf(
        "카드번호", "본인가족구분", "승인일자", "승인시각", "가맹점명", "승인금액(원)",
        "일시불할부구분", "할부개월", "승인번호", "취소여부", "사용포인트", "결제일",
    )

    /** 삼성카드 파일처럼 금액·포인트는 숫자 셀, 나머지는 글자 셀로 쓴다. */
    private fun row(date: String, merchant: String, amount: Any, kind: String, months: String, approval: String, cancel: String, billing: String) =
        listOf("123456******7890", "본인", date, "12:00:00", merchant, amount, kind, months, approval, cancel, 0.0, billing)

    private fun workbook(vararg detailRows: List<Any?>, detailHeader: List<String> = header): ByteArrayInputStream {
        val workbook = XSSFWorkbook()
        workbook.createSheet("■ 카드이용내역").apply {
            createRow(0).apply { listOf("조회기간", "이용지역").forEachIndexed { i, v -> createCell(i).setCellValue(v) } }
            createRow(1).apply { createCell(0).setCellValue("2026.08.26 ~ 2026.11.26") }
        }
        workbook.createSheet("■ 국내이용내역").apply {
            createRow(0).apply { detailHeader.forEachIndexed { i, v -> createCell(i).setCellValue(v) } }
            detailRows.forEachIndexed { r, values ->
                createRow(r + 1).apply {
                    values.forEachIndexed { i, v ->
                        when (v) {
                            is Number -> createCell(i).setCellValue(v.toDouble())
                            is String -> createCell(i).setCellValue(v)
                            null -> Unit
                        }
                    }
                }
            }
        }
        return ByteArrayOutputStream().also { workbook.use { wb -> wb.write(it) } }.toByteArray().let(::ByteArrayInputStream)
    }

    @Test
    fun `상세 시트의 승인 줄을 이용 건으로 읽는다`() {
        // given
        val file = workbook(row("2026.11.25", "세븐일레븐 OO점", 2500.0, "일시불", "0", "10000001", "-", "20261213"))

        // when
        val statement = reader.read(file).statement

        // then
        assertThat(statement.paymentMethod).isEqualTo("삼성카드")
        assertThat(statement.entries).containsExactly(
            CardStatementEntry("10000001", LocalDate.of(2026, 11, 25), "세븐일레븐 OO점", 2_500, 0),
        )
    }

    @Test
    fun `취소 줄은 음수 금액으로 읽는다`() {
        // given
        val file = workbook(row("2026.09.28", "카카오_택시_선승인", -12000.0, "일시불", "0", "10000002", "전체취소", " "))

        // when
        val entry = reader.read(file).statement.entries.single()

        // then
        assertThat(entry.amount).isEqualTo(-12_000)
    }

    @Test
    fun `할부 개월을 읽는다`() {
        val file = workbook(row("2026.09.28", "가나의원", 240000.0, "할부", "2", "10000004", "-", "20261013"))

        assertThat(reader.read(file).statement.entries.single().installmentMonths).isEqualTo(2)
    }

    @Test
    fun `글자로 된 금액은 쉼표를 허용한다`() {
        val file = workbook(row("2026.11.25", "트레이더스", "100,000", "일시불", "0", "1", "-", "20261213"))

        assertThat(reader.read(file).statement.entries.single().amount).isEqualTo(100_000)
    }

    @Test
    fun `열 순서가 바뀌어도 머리글 이름으로 읽는다`() {
        // given
        val reordered = header.reversed()
        val values = row("2026.11.25", "OO김밥", 9000.0, "일시불", "0", "10000005", "-", "20261213").reversed()

        // when
        val entry = reader.read(workbook(values, detailHeader = reordered)).statement.entries.single()

        // then
        assertThat(entry.merchant).isEqualTo("OO김밥")
        assertThat(entry.amount).isEqualTo(9_000)
    }

    @Test
    fun `빈 줄은 건너뛴다`() {
        val file = workbook(
            row("2026.11.25", "OO김밥", 9000.0, "일시불", "0", "1", "-", "20261213"),
            listOf("", " "),
            row("2026.11.24", "맘스터치", 8300.0, "일시불", "0", "2", "-", "20261213"),
        )

        assertThat(reader.read(file).statement.entries.map { it.merchant }).containsExactly("OO김밥", "맘스터치")
    }

    @Test
    fun `엑셀이 아니면 형식 오류다`() {
        assertThatThrownBy { reader.read(ByteArrayInputStream("not excel".toByteArray())) }
            .isInstanceOf(CardStatementFormatException::class.java)
            .hasMessageContaining("엑셀 파일을 열 수 없습니다")
    }

    @Test
    fun `필요한 열이 있는 시트가 없으면 형식 오류다`() {
        assertThatThrownBy { reader.read(workbook(detailHeader = listOf("날짜", "가게", "금액"))) }
            .isInstanceOf(CardStatementFormatException::class.java)
            .hasMessageContaining("승인번호")
    }

    @Test
    fun `읽을 수 없는 줄은 빼고 나머지는 읽는다`() {
        // given
        val file = workbook(
            row("2026.11.25", "OO김밥", 8000.0, "일시불", "0", "1", "-", "20261213"),
            row("2026.11.24", "맘스터치", 100.5, "일시불", "0", "2", "-", "20261213"),
            row("2026.11.23", "버거킹", 9000.0, "일시불", "0", "3", "-", "20261213"),
        )

        // when
        val reading = reader.read(file)

        // then
        assertThat(reading.statement.entries.map { it.merchant }).containsExactly("OO김밥", "버거킹")
        assertThat(reading.totalRowCount).isEqualTo(3)
        assertThat(reading.rejectedRows.map { it.rowNumber }).containsExactly(3)
    }

    @Test
    fun `읽지 못한 줄은 사유와 그 줄의 값을 남긴다`() {
        // given
        val file = workbook(row("2026.11.24", "맘스터치", 100.5, "일시불", "0", "2", "-", "20261213"))

        // when
        val rejected = reader.read(file).rejectedRows.single()

        // then
        assertThat(rejected.reason).contains("정수")
        assertThat(rejected.content).contains("맘스터치")
        assertThat(rejected.content).contains("2026.11.24")
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "날짜로 읽을 수 없는 승인일자, 어제, 맘스터치, 0, 승인일자",
        "비어 있는 승인일자, '', 맘스터치, 0, 승인일자",
        "숫자가 아닌 할부개월, 2026.11.25, 맘스터치, 두달, 할부개월",
        "비어 있는 가맹점명(사용처 불변식), 2026.11.25, '', 0, 사용처",
    )
    fun `줄 하나의 문제는 사유와 함께 뺀다`(case: String, date: String, merchant: String, months: String, reasonKeyword: String) {
        val file = workbook(row(date, merchant, 1000.0, "일시불", months, "1", "-", "20261213"))

        val reading = reader.read(file)

        assertThat(reading.statement.entries).isEmpty()
        assertThat(reading.rejectedRows.single().reason).contains(reasonKeyword)
    }
}
