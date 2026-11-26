package com.example.jipsa.presentation.scheduler

import com.example.jipsa.application.port.FixedExpenseUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@DisplayName("FixedExpenseRecordingScheduler")
class FixedExpenseRecordingSchedulerTest {

    // UTC 로는 10월 31일이지만 한국 시간으로는 11월 1일이다
    private val clock = Clock.fixed(Instant.parse("2026-10-31T15:30:00Z"), ZoneId.of("Asia/Seoul"))
    private val useCase = mockk<FixedExpenseUseCase>()
    private val scheduler = FixedExpenseRecordingScheduler(useCase, clock)

    @Test
    fun `기동할 때와 매월 1일에 한국 시간 기준 이번 달을 기록한다`() {
        every { useCase.recordFor(YearMonth.of(2026, 11)) } returns 3

        scheduler.recordOnStartup()
        scheduler.recordMonthly()

        verify(exactly = 2) { useCase.recordFor(YearMonth.of(2026, 11)) }
    }
}
