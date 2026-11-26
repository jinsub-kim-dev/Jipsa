package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.CardImportResult
import com.example.jipsa.application.port.CardStatementImportUseCase
import com.example.jipsa.domain.model.CardPurchase
import com.example.jipsa.domain.model.CardStatement
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.service.UnprocessedPurchase
import com.example.jipsa.fixture.won
import com.example.jipsa.presentation.statement.CardStatementFileReader
import com.example.jipsa.presentation.statement.HyundaiCardStatementReader
import com.example.jipsa.presentation.statement.SamsungCardStatementReader
import com.example.jipsa.presentation.statement.WooriCardStatementReader
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@WebMvcTest(CardStatementController::class)
@Import(
    CardStatementControllerTest.MockConfiguration::class,
    CardStatementFileReader::class,
    HyundaiCardStatementReader::class,
    SamsungCardStatementReader::class,
    WooriCardStatementReader::class,
)
@DisplayName("카드 이용내역 가져오기 API")
class CardStatementControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var cardStatementImportUseCase: CardStatementImportUseCase

    private val captured = slot<CardStatement>()

    @BeforeEach
    fun setUp() {
        clearMocks(cardStatementImportUseCase)
        every { cardStatementImportUseCase.importStatement(capture(captured)) } returns emptyResult
    }

    private val emptyResult = CardImportResult(0, emptyList(), 0, emptyList(), emptyList(), CategoryId(13), emptyList())

    private fun bytes(workbook: Workbook): ByteArray = ByteArrayOutputStream().also { workbook.use { wb -> wb.write(it) } }.toByteArray()

    /** 삼성카드 상세 시트. 둘째 줄은 금액이 소수라 읽을 수 없다. */
    private fun samsungFile(): ByteArray = bytes(
        XSSFWorkbook().apply {
            createSheet("■ 국내이용내역").apply {
                createRow(0).apply {
                    listOf("승인일자", "가맹점명", "승인금액(원)", "할부개월", "승인번호").forEachIndexed { i, v -> createCell(i).setCellValue(v) }
                }
                createRow(1).apply {
                    createCell(0).setCellValue("2026.11.25")
                    createCell(1).setCellValue("OO김밥")
                    createCell(2).setCellValue(8000.0)
                    createCell(3).setCellValue("0")
                    createCell(4).setCellValue("10000001")
                }
                createRow(2).apply {
                    createCell(0).setCellValue("2026.11.24")
                    createCell(1).setCellValue("맘스터치")
                    createCell(2).setCellValue(100.5)
                    createCell(3).setCellValue("0")
                    createCell(4).setCellValue("77650710")
                }
            }
        },
    )

    private fun wooriFile(): ByteArray = bytes(
        HSSFWorkbook().apply {
            createSheet("Page ").apply {
                createRow(0).apply {
                    listOf("No", "승인번호", "승인일자", "승인금액", "할부", "취소일", "가맹점").forEachIndexed { i, v -> createCell(i).setCellValue(v) }
                }
                createRow(1).apply {
                    listOf("1", "10000006", "2026/11/25 11:18:25").forEachIndexed { i, v -> createCell(i).setCellValue(v) }
                    createCell(3).setCellValue(10000.0)
                    createCell(5).setCellValue("-")
                    createCell(6).setCellValue("쿠팡　주식회사")
                }
            }
        },
    )

    @Test
    fun `카드사를 고르지 않아도 파일을 보고 알아낸다`() {
        // when
        mockMvc.perform(multipart("/api/card-statements").file(MockMultipartFile("file", "woori.xls", null, wooriFile())))
            // then
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("woori"))
            .andExpect(jsonPath("$.paymentMethod").value("우리카드"))
            .andExpect(jsonPath("$.fileName").value("woori.xls"))
        assertThat(captured.captured.entries.single().merchant).isEqualTo("쿠팡 주식회사")
    }

    @Test
    fun `확장자만 xls 인 HTML 표(현대카드)도 알아낸다`() {
        // given
        val html = """
            <html><head><meta charset="utf-8"></head><body><table>
              <tr><th colspan="3">2026년 09월 이용대금명세서</th></tr>
              <tr><th>이용일</th><th>이용카드</th><th>이용가맹점</th><th>이용금액</th><th>할부/회차</th></tr>
              <tr><td>2026년 08월 17일</td><td>본인 OO카드</td><td>코스트코코리아</td   ><td>120,000</td><td></td></tr>
              <tr><td>-</td><td></td><td>총 합계 1 건</td><td>0</td><td></td></tr>
            </table></body></html>
        """.trimIndent()

        // when
        mockMvc.perform(
            multipart("/api/card-statements").file(MockMultipartFile("file", "hyundaicard.xls", null, html.toByteArray())),
        )
            // then
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("hyundai"))
            .andExpect(jsonPath("$.paymentMethod").value("현대카드"))
            .andExpect(jsonPath("$.totalRowCount").value(1))
        assertThat(captured.captured.entries.single().amount).isEqualTo(120_000)
    }

    @Test
    fun `삼성카드 파일도 알아낸다`() {
        mockMvc.perform(multipart("/api/card-statements").file(MockMultipartFile("file", "s.xlsx", null, samsungFile())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("samsung"))
        assertThat(captured.captured.paymentMethod).isEqualTo("삼성카드")
    }

    @Test
    fun `결과에 전체 줄 수와 추가·중복·미분류·처리 안 됨을 담는다`() {
        // given
        every { cardStatementImportUseCase.importStatement(capture(captured)) } returns CardImportResult(
            importedTransactionCount = 1,
            fixedExpenseMatches = listOf(CardPurchase.LumpSum(LocalDate.of(2026, 11, 25), "OO보험", won(15_000))),
            importedInstallmentPlanCount = 0,
            duplicates = emptyList(),
            uncategorized = listOf(CardPurchase.LumpSum(LocalDate.of(2026, 11, 25), "OO김밥", won(8_000))),
            uncategorizedCategoryId = CategoryId(13),
            unprocessed = listOf(UnprocessedPurchase(LocalDate.of(2026, 9, 28), "카카오T택시", 12_000, "승인이 취소되었습니다.")),
        )

        // when
        mockMvc.perform(multipart("/api/card-statements").file(MockMultipartFile("file", "s.xlsx", null, samsungFile())))
            // then
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalRowCount").value(2))
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.duplicateCount").value(0))
            .andExpect(jsonPath("$.fixedExpenseCount").value(1))
            .andExpect(jsonPath("$.fixedExpenses[0].merchant").value("OO보험"))
            .andExpect(jsonPath("$.uncategorizedCount").value(1))
            .andExpect(jsonPath("$.uncategorizedCategoryId").value(13))
            .andExpect(jsonPath("$.uncategorized[0].merchant").value("OO김밥"))
            .andExpect(jsonPath("$.uncategorized[0].spentDate").value("2026-11-25"))
            .andExpect(jsonPath("$.unprocessedCount").value(2))
            // 읽지 못한 줄이 먼저, 행 번호와 그 줄의 값을 담는다
            .andExpect(jsonPath("$.unprocessed[0].rowNumber").value(3))
            .andExpect(jsonPath("$.unprocessed[0].reason").value(containsString("정수")))
            .andExpect(jsonPath("$.unprocessed[0].content").value(containsString("맘스터치")))
            // 취소는 소비일·사용처·금액을 담는다
            .andExpect(jsonPath("$.unprocessed[1].merchant").value("카카오T택시"))
            .andExpect(jsonPath("$.unprocessed[1].reason").value("승인이 취소되었습니다."))
    }

    @Test
    fun `카드사를 경로로 정할 수도 있다`() {
        mockMvc.perform(multipart("/api/card-statements/woori").file(MockMultipartFile("file", "w.xls", null, wooriFile())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("woori"))
    }

    @Test
    fun `삼성카드 파일을 우리카드 경로로 올리면 400 이다`() {
        mockMvc.perform(multipart("/api/card-statements/woori").file(MockMultipartFile("file", "s.xlsx", null, samsungFile())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CARD_STATEMENT"))
    }

    @Test
    fun `지원하지 않는 카드사면 400 이다`() {
        mockMvc.perform(multipart("/api/card-statements/shinhan").file(MockMultipartFile("file", "h.xlsx", null, samsungFile())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("hyundai, samsung, woori")))
    }

    @Test
    fun `어느 카드사 형식도 아니면 400 이다`() {
        val unknown = bytes(XSSFWorkbook().apply { createSheet("x").createRow(0).createCell(0).setCellValue("날짜") })

        mockMvc.perform(multipart("/api/card-statements").file(MockMultipartFile("file", "x.xlsx", null, unknown)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CARD_STATEMENT"))
            .andExpect(jsonPath("$.message").value(containsString("지원하는 카드사")))
    }

    @Test
    fun `엑셀이 아니면 400 INVALID_CARD_STATEMENT 다`() {
        mockMvc.perform(multipart("/api/card-statements").file(MockMultipartFile("file", "a.txt", null, "hello".toByteArray())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CARD_STATEMENT"))
    }

    @Test
    fun `빈 파일이면 400 이다`() {
        mockMvc.perform(multipart("/api/card-statements").file(MockMultipartFile("file", ByteArray(0))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_CARD_STATEMENT"))
    }

    @Test
    fun `파일이 없으면 400 이다`() {
        mockMvc.perform(multipart("/api/card-statements"))
            .andExpect(status().isBadRequest)
    }

    @TestConfiguration
    class MockConfiguration {
        @Bean
        fun cardStatementImportUseCase(): CardStatementImportUseCase = mockk()
    }
}
