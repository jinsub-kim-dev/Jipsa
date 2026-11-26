package com.example.jipsa.presentation.statement

import com.example.jipsa.domain.model.CardPurchase
import com.example.jipsa.domain.model.CardStatementEntry
import com.example.jipsa.fixture.won
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.ByteArrayInputStream
import java.time.LocalDate

/**
 * 현대카드 이용대금명세서 읽기.
 *
 * 실제 명세서는 개인 금융 정보라 저장소에 두지 않는다. 같은 구조(확장자만 xls 인 HTML 표, 제목 줄의
 * colspan, 닫는 태그의 공백, 소계·합계 줄)를 테스트 안에서 만든다.
 */
@DisplayName("현대카드 이용내역 읽기")
class HyundaiCardStatementReaderTest {

    private val reader = HyundaiCardStatementReader()

    private fun row(date: String, merchant: String, amount: String, installment: String = "") = """
        <tr>
            <td align="center">$date</td>
            <td>본인 OO카드</td>
            <td>$merchant</td   >
            <td align="right">$amount</td>
            <td align="center" style='mso-number-format:"\@";'>
                $installment
            </td>
            <td align="center">0%</td>
            <td align="right">0</td>
            <td align="right">$amount</td>
            <td align="right">0</td>
            <td align="right">0</td>
        </tr>
    """

    private fun subtotal(label: String) = """
        <tr>
            <td align="center" bgcolor="#f2f2f2">-</td>
            <td bgcolor="#f2f2f2"></td>
            <td bgcolor="#f2f2f2">$label</td>
            <td align="right" bgcolor="#f2f2f2">0</td>
        </tr>
    """

    private fun statement(vararg rows: String): ByteArrayInputStream {
        // 실제 파일처럼 앞에 빈 줄이 여럿 있다
        val html = "\r\n".repeat(30) + """
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml"xml:lang="ko" lang="ko">
            <head><meta http-equiv="Content-Type" content="text/html; charset=utf-8" /></head>
            <body>
            <table cellspacing="0" cellpadding="0" border="1">
                <tr><th colspan="10" bgcolor="#f2f2f2">2026년 09월 이용대금명세서</th></tr>
                <tr><th colspan="10" align="left">결제 상세내역</th></tr>
                <tr>
                    <th>이용일</th><th>이용카드</th><th>이용가맹점</th><th>이용금액</th><th>할부/회차</th>
                    <th>적립/할인율(%)</th><th>예상적립/할인</th><th>결제원금</th><th>결제후잔액</th><th>수수료(이자)</th>
                </tr>
                ${rows.joinToString("\n")}
            </table>
            </body></html>
        """
        return ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `명세서의 이용 줄을 이용 건으로 읽는다`() {
        // given
        val file = statement(row("2026년 08월 17일", "코스트코코리아", "120,000"), subtotal("일시불 소계 1 건"))

        // when
        val reading = reader.read(file)

        // then
        assertThat(reading.issuer).isEqualTo("hyundai")
        assertThat(reading.statement.paymentMethod).isEqualTo("현대카드")
        assertThat(reading.statement.entries).containsExactly(
            CardStatementEntry("row-4", LocalDate.of(2026, 8, 17), "코스트코코리아", 120_000, 0),
        )
    }

    @Test
    fun `소계·합계 줄은 건너뛰고 세지 않으며 그 뒤의 할부 구역도 읽는다`() {
        // given
        val file = statement(
            row("2026년 08월 17일", "코스트코코리아", "120,000"),
            row("2026년 08월 17일", "코스트코코리아", "6,000"),
            subtotal("일 시 불 소계 2 건"),
            row("2026년 07월 03일", "전자랜드", "1,200,000", "2/6"),
            subtotal("할 부 소계 1 건"),
            subtotal("총 합계 3 건"),
        )

        // when
        val reading = reader.read(file)

        // then
        assertThat(reading.statement.entries.map { it.merchant }).containsExactly("코스트코코리아", "코스트코코리아", "전자랜드")
        assertThat(reading.totalRowCount).isEqualTo(3)
        assertThat(reading.rejectedRows).isEmpty()
    }

    @Test
    fun `같은 날 같은 가게라도 줄마다 다른 구매다`() {
        // 승인번호가 없으므로 줄 번호로 구분한다
        val file = statement(row("2026년 08월 17일", "코스트코코리아", "6,000"), row("2026년 08월 17일", "코스트코코리아", "6,000"))

        assertThat(reader.read(file).statement.purchases()).hasSize(2)
    }

    @ParameterizedTest(name = "할부/회차 \"{0}\" -> {1}개월")
    @CsvSource("'', 0", "2/6, 6", "12/12, 12", "3, 3")
    fun `할부 회차 표기에서 전체 개월 수를 읽는다`(value: String, expected: Int) {
        val file = statement(row("2026년 07월 03일", "전자랜드", "1,200,000", value))

        assertThat(reader.read(file).statement.entries.single().installmentMonths).isEqualTo(expected)
    }

    @Test
    fun `할부는 이용금액이 총액이고 이용일이 첫 회차다`() {
        // 9월 명세서에서도 10월 명세서에서도 같은 할부 계획이 되어 중복으로 걸러진다
        val file = statement(row("2026년 07월 03일", "전자랜드", "1,200,000", "2/6"))

        val purchase = reader.read(file).statement.purchases().single()

        assertThat(purchase).isEqualTo(CardPurchase.Installment(LocalDate.of(2026, 7, 3), "전자랜드", won(1_200_000), 6))
    }

    @Test
    fun `취소(음수) 줄은 같은 사용처·같은 금액의 승인과 짝지어 함께 빠진다`() {
        // given
        val file = statement(
            row("2026년 08월 17일", "코스트코코리아", "6,000"),
            row("2026년 08월 20일", "코스트코코리아", "-6,000"),
            row("2026년 08월 21일", "코스트코코리아", "6,000"),
        )

        // when
        val statement = reader.read(file).statement

        // then
        assertThat(statement.purchases().map { it.spentDate }).containsExactly(LocalDate.of(2026, 8, 21))
        assertThat(statement.cancellations()).hasSize(1)
    }

    @Test
    fun `짝이 없는 취소는 취소만 있는 것으로 남는다`() {
        val statement = reader.read(statement(row("2026년 08월 20일", "코스트코코리아", "-6,000"))).statement

        assertThat(statement.cancellations().single().reason).contains("취소 내역만")
    }

    @Test
    fun `전각 문자와 이어진 공백을 고른다`() {
        val file = statement(row("2026년 08월 17일", "ＧＳ２５　　ＯＯ점", "3,000"))

        assertThat(reader.read(file).statement.entries.single().merchant).isEqualTo("GS25 OO점")
    }

    @Test
    fun `읽을 수 없는 줄은 사유와 함께 뺀다`() {
        // given
        val file = statement(row("어제", "코스트코코리아", "6,000"), row("2026년 08월 17일", "코스트코코리아", "오천원"))

        // when
        val rejected = reader.read(file).rejectedRows

        // then
        assertThat(rejected.map { it.reason }).satisfiesExactly(
            { assertThat(it).contains("이용일") },
            { assertThat(it).contains("이용금액") },
        )
        assertThat(rejected.first().content).contains("코스트코코리아")
    }

    @Test
    fun `할부 회차를 읽을 수 없으면 그 줄을 뺀다`() {
        val file = statement(row("2026년 07월 03일", "전자랜드", "1,200,000", "무이자"))

        assertThat(reader.read(file).rejectedRows.single().reason).contains("할부/회차")
    }

    @Test
    fun `다른 카드사 형식이면 형식 오류다`() {
        val html = "<table><tr><th>승인일자</th><th>가맹점명</th></tr></table>".toByteArray()

        assertThatThrownBy { reader.read(ByteArrayInputStream(html)) }
            .isInstanceOf(CardStatementFormatException::class.java)
            .hasMessageContaining("현대카드 이용내역 형식이 아닙니다")
    }
}
