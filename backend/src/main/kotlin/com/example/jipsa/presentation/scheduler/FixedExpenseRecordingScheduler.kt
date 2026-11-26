package com.example.jipsa.presentation.scheduler

import com.example.jipsa.application.port.FixedExpenseUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.YearMonth

/**
 * 고정비 자동 기록 (PRD 3.2). 시간이 요청을 대신하는 인바운드 어댑터다.
 *
 * 서버가 매월 1일에 꺼져 있었어도 기록이 빠지지 않도록 **기동할 때도** 이번 달 분을 기록한다.
 * 기록은 멱등이므로 몇 번 불려도 안전하다.
 */
@Component
class FixedExpenseRecordingScheduler(
    private val fixedExpenseUseCase: FixedExpenseUseCase,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun recordOnStartup() = recordCurrentMonth()

    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Seoul")
    fun recordMonthly() = recordCurrentMonth()

    private fun recordCurrentMonth() {
        val month = YearMonth.now(clock)
        val count = fixedExpenseUseCase.recordFor(month)
        log.info("{} 고정비 {}건을 새로 기록했습니다.", month, count)
    }
}
