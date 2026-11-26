package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.model.CardPurchase
import com.example.jipsa.domain.model.CardStatementEntry
import com.example.jipsa.domain.model.Money
import org.apache.poi.hssf.usermodel.HSSFWorkbook
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
 * 우리카드 이용내역 엑셀(xls) 읽기.
 *
 * 실제 이용내역은 개인 금융 정보라 저장소에 두지 않는다. 같은 구조(제목·회원 정보 → 머리글 →
 * 데이터 → 합계 줄)의 xls 를 테스트 안에서 만든다.
 */
@DisplayName("우리카드 이용내역 읽기")
class WooriCardStatementReaderTest {

    private val reader = WooriCardStatementReader()

    private val header = listOf("No", "회원", "카드", "요청방식", "승인번호", "승인일자", "승인금액", "할부", "접수일", "취소일", "가맹점", "", "사업자번호")

    private fun row(no: String, approval: String, date: String, amount: Double, months: String, cancel: String, merchant: String) =
        listOf(no, "본인", "M185", "일시불", approval, date, amount, months, "11/24", cancel, merchant, "", "000-00-00000")

    private fun workbook(vararg dataRows: List<Any>): ByteArrayInputStream {
        val workbook = HSSFWorkbook()
        val sheet = workbook.createSheet("Page ")
        var r = 0
        fun write(values: List<Any>) = sheet.createRow(r++).apply {
            values.forEachIndexed { i, v ->
                when (v) {
                    is Double -> createCell(i).setCellValue(v)
                    is Int -> createCell(i).setCellValue(v.toDouble())
                    is String -> if (v.isNotEmpty()) createCell(i).setCellValue(v)
                }
            }
        }
        write(listOf("국내 거래승인내역(정상)"))
        write(listOf("주민/사업자번호 :", "", "", "000000-*******"))
        write(listOf("이용기간 :", "", "", "2026/09/01~2026/11/25"))
        write(listOf("* 이용카드란의 번호는 16자리 중 14~16번째 숫자"))
        write(header)
        dataRows.forEach(::write)
        // 합계 줄. "승인번호" 열에 건수가 숫자로 들어 있다
        write(listOf("국내 승인건수", "", "", "", dataRows.size, "", "", "국내 승인금액"))
        return ByteArrayOutputStream().also { workbook.use { wb -> wb.write(it) } }.toByteArray().let(::ByteArrayInputStream)
    }

    @Test
    fun `머리글 아래 승인 줄을 이용 건으로 읽는다`() {
        // given
        val file = workbook(row("1", "10000006", "2026/11/25 11:18:25", 10000.0, "", "-", "보험사"))

        // when
        val statement = reader.read(file).statement

        // then
        assertThat(statement.paymentMethod).isEqualTo("우리카드")
        assertThat(statement.entries).containsExactly(
            CardStatementEntry("10000006", LocalDate.of(2026, 11, 25), "보험사", 10_000, 0),
        )
    }

    @Test
    fun `합계 줄 앞에서 멈춘다`() {
        val file = workbook(
            row("1", "1", "2026/11/25 11:18:25", 1000.0, "", "-", "가게A"),
            row("2", "2", "2026/11/24 03:43:31", 2000.0, "", "-", "가게B"),
        )

        assertThat(reader.read(file).statement.entries.map { it.merchant }).containsExactly("가게A", "가게B")
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "쿠팡　주식회사, 쿠팡 주식회사",
        "ＡＫ　ＰＬＡＺＡ（ＯＯ, AK PLAZA(OO",
        "지에스２５　ＯＯ점, 지에스25 OO점",
        "가나손해보험（주）, 가나손해보험(주)",
    )
    fun `전각 문자를 반각으로 바꾼다`(raw: String, expected: String) {
        val file = workbook(row("1", "1", "2026/11/25 11:18:25", 1000.0, "", "-", raw))

        assertThat(reader.read(file).statement.entries.single().merchant).isEqualTo(expected)
    }

    @Test
    fun `이어진 공백은 하나로 줄인다`() {
        val file = workbook(row("1", "1", "2026/11/25", 1000.0, "", "-", "가나다상사  주식회사"))

        assertThat(reader.read(file).statement.entries.single().merchant).isEqualTo("가나다상사 주식회사")
    }

    @Test
    fun `취소일이 있으면 같은 승인번호의 취소 건을 함께 만든다`() {
        // given
        val file = workbook(row("1", "777", "2026/11/25 11:18:25", 12000.0, "", "2026/11/26", "카카오T택시"))

        // when
        val entries = reader.read(file).statement.entries

        // then
        assertThat(entries.map { it.approvalNumber to it.amount }).containsExactly("777" to 12_000L, "777" to -12_000L)
    }

    @ParameterizedTest(name = "할부 \"{0}\" -> {1}개월")
    @CsvSource("'', 0", "3, 3", "03, 3", "3개월, 3")
    fun `할부 열을 개월 수로 읽는다`(value: String, expected: Int) {
        val file = workbook(row("1", "1", "2026/11/25", 30000.0, value, "-", "가게"))

        assertThat(reader.read(file).statement.entries.single().installmentMonths).isEqualTo(expected)
    }

    @Test
    fun `할부 열에 숫자가 없으면 그 줄을 뺀다`() {
        val file = workbook(row("1", "1", "2026/11/25", 30000.0, "무이자", "-", "가게"))

        assertThat(reader.read(file).rejectedRows.single().reason).contains("할부")
    }

    @Test
    fun `결제일이 없어도 할부를 소비일 기준으로 가져올 수 있다`() {
        // given
        val statement = reader.read(workbook(row("1", "1", "2026/11/25 10:00:00", 30000.0, "3", "-", "가게"))).statement

        // when
        val purchase = statement.purchases().single()

        // then
        assertThat(purchase).isEqualTo(CardPurchase.Installment(LocalDate.of(2026, 11, 25), "가게", Money.of(30_000), 3))
    }

    @Test
    fun `승인일자가 비어 있으면 그 줄을 행 번호와 함께 뺀다`() {
        // given: 머리글이 5행, 데이터 첫 줄이 6행이다
        val file = workbook(
            row("1", "1", "", 1000.0, "", "-", "가게"),
            row("2", "2", "2026/11/25", 2000.0, "", "-", "가게B"),
        )

        // when
        val reading = reader.read(file)

        // then
        assertThat(reading.rejectedRows.single().rowNumber).isEqualTo(6)
        assertThat(reading.rejectedRows.single().reason).contains("승인일자")
        assertThat(reading.statement.entries.map { it.merchant }).containsExactly("가게B")
        assertThat(reading.totalRowCount).isEqualTo(2)
    }

    @Test
    fun `합계 줄은 전체 줄 수에 넣지 않는다`() {
        val file = workbook(row("1", "1", "2026/11/25", 1000.0, "", "-", "가게"))

        assertThat(reader.read(file).totalRowCount).isEqualTo(1)
    }

    @Test
    fun `삼성카드 파일처럼 머리글이 다르면 형식 오류다`() {
        val other = HSSFWorkbook().apply {
            createSheet("내역").createRow(0).apply {
                listOf("승인일자", "가맹점명", "승인금액(원)").forEachIndexed { i, v -> createCell(i).setCellValue(v) }
            }
        }
        val file = ByteArrayOutputStream().also { other.use { wb -> wb.write(it) } }.toByteArray()

        assertThatThrownBy { reader.read(ByteArrayInputStream(file)) }
            .isInstanceOf(CardStatementFormatException::class.java)
            .hasMessageContaining("우리카드 이용내역 형식이 아닙니다")
    }
}
