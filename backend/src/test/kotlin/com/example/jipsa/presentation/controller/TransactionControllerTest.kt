package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.RegisterGeneralTransactionCommand
import com.example.jipsa.application.port.TransactionUseCase
import com.example.jipsa.application.port.UpdateTransactionCommand
import com.example.jipsa.domain.exception.DomainStateException
import com.example.jipsa.domain.exception.ResourceNotFoundException
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.Tag
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.fixture.generalTransaction
import com.example.jipsa.fixture.installmentPlan
import com.example.jipsa.fixture.won
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@WebMvcTest(TransactionController::class)
@Import(TransactionControllerTest.MockUseCaseConfiguration::class)
@DisplayName("TransactionController API")
class TransactionControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var useCase: TransactionUseCase

    @BeforeEach
    fun resetMocks() = clearMocks(useCase)

    @Test
    fun `일반 지출을 등록하면 201 과 Location 을 반환한다`() {
        // given
        val command = slot<RegisterGeneralTransactionCommand>()
        every { useCase.registerGeneral(capture(command)) } returns generalTransaction(id = 7)

        // when & then
        mockMvc.perform(
            post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(
                """{"categoryId":1,"amount":9000,"merchant":"김밥천국","spentDate":"2026-11-26","tags":["#점심","점심","혼밥"]}""",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/transactions/7"))
            .andExpect(jsonPath("$.kind").value("GENERAL"))
            .andExpect(jsonPath("$.deletable").value(true))
            .andExpect(jsonPath("$.locked").value(false))
            .andExpect(jsonPath("$.memo").doesNotExist())

        // 태그는 정규화되어 중복이 합쳐진다
        assertThat(command.captured.tags.names).containsExactly("점심", "혼밥")
    }

    @Test
    fun `필수 값이 없으면 필드별 오류와 함께 400 을 반환한다`() {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""{"amount":9000}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.length()").value(3))
    }

    @Test
    fun `음수 금액은 도메인 값 객체가 거부해 400 이다`() {
        mockMvc.perform(
            post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId":1,"amount":-1,"merchant":"x","spentDate":"2026-11-26"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
    }

    @Test
    fun `월과 조건으로 검색한다`() {
        // given
        val criteria = slot<TransactionSearchCriteria>()
        every { useCase.search(capture(criteria)) } returns
            listOf(installmentPlan().scheduleParts()[8].assignId(TransactionId(3)))

        // when & then
        mockMvc.perform(
            get("/api/transactions")
                .param("month", "2026-11").param("kind", "INSTALLMENT").param("categoryId", "11")
                .param("tag", "#전자기기").param("keyword", "맥북"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].installmentSequence").value(9))
            .andExpect(jsonPath("$[0].locked").value(true))
            .andExpect(jsonPath("$[0].deletable").value(false))

        assertThat(criteria.captured).isEqualTo(
            TransactionSearchCriteria(
                from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 30), kind = ExpenseKind.INSTALLMENT,
                categoryId = CategoryId(11), tag = Tag.of("전자기기"), keyword = "맥북",
            ),
        )
    }

    @Test
    fun `월이 없거나 형식이 틀리면 400 이다`() {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/transactions?month=2026-13")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/transactions?month=2026-11&kind=SALARY"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `바뀐 필드만 담아 수정한다`() {
        val command = slot<UpdateTransactionCommand>()
        every { useCase.update(capture(command)) } returns generalTransaction(amount = 12_000)

        mockMvc.perform(
            patch("/api/transactions/1").contentType(MediaType.APPLICATION_JSON).content("""{"amount":12000,"clearMemo":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(12000))

        assertThat(command.captured).isEqualTo(
            UpdateTransactionCommand(TransactionId(1), amount = won(12_000), clearMemo = true),
        )
    }

    @Test
    fun `할부 회차 삭제는 409 다`() {
        every { useCase.delete(TransactionId(1)) } throws DomainStateException("해지하세요")

        mockMvc.perform(delete("/api/transactions/1"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("DOMAIN_STATE_CONFLICT"))
    }

    @Test
    fun `없는 거래는 404 다`() {
        every { useCase.getById(TransactionId(9)) } throws ResourceNotFoundException("거래", 9)

        mockMvc.perform(get("/api/transactions/9")).andExpect(status().isNotFound)
    }

    @Test
    fun `삭제하면 204 다`() {
        every { useCase.delete(TransactionId(1)) } returns Unit

        mockMvc.perform(delete("/api/transactions/1")).andExpect(status().isNoContent)
        verify { useCase.delete(TransactionId(1)) }
    }

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun transactionUseCase(): TransactionUseCase = mockk()
    }
}
