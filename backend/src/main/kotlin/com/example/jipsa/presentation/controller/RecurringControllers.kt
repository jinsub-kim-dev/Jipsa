package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.FixedExpenseUseCase
import com.example.jipsa.application.port.InstallmentPlanUseCase
import com.example.jipsa.domain.model.FixedExpenseId
import com.example.jipsa.domain.model.InstallmentPlanId
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.presentation.dto.ConvertToFixedExpenseRequest
import com.example.jipsa.presentation.dto.FixedExpenseConversionResponse
import com.example.jipsa.presentation.dto.FixedExpenseResponse
import com.example.jipsa.presentation.dto.InstallmentPlanResponse
import com.example.jipsa.presentation.dto.RecordFixedExpensesResponse
import com.example.jipsa.presentation.dto.RegisterFixedExpenseRequest
import com.example.jipsa.presentation.dto.RegisterInstallmentPlanRequest
import com.example.jipsa.presentation.dto.TerminateFixedExpenseRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Clock
import java.time.YearMonth

/** 고정비 API (PRD 3.2). 조회는 [month] 기준으로 그 달의 납부 여부와 납부일을 함께 준다. */
@RestController
@RequestMapping("/api/fixed-expenses")
class FixedExpenseController(
    private val fixedExpenseUseCase: FixedExpenseUseCase,
    private val clock: Clock,
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterFixedExpenseRequest): ResponseEntity<FixedExpenseResponse> {
        val saved = fixedExpenseUseCase.register(request.toCommand())
        val response = FixedExpenseResponse.from(saved, YearMonth.now(clock))
        return ResponseEntity.created(URI.create("/api/fixed-expenses/${response.id}")).body(response)
    }

    /** 일반 지출로 고정비를 만들고 같은 사용처의 다른 달 일반 지출도 함께 바꾼다 (PRD 3.2.1). */
    @PostMapping("/conversions")
    fun convert(@Valid @RequestBody request: ConvertToFixedExpenseRequest): ResponseEntity<FixedExpenseConversionResponse> {
        val result = fixedExpenseUseCase.convertFromTransaction(TransactionId(request.transactionId!!))
        val response = FixedExpenseConversionResponse.of(result, YearMonth.now(clock))
        return ResponseEntity.created(URI.create("/api/fixed-expenses/${response.fixedExpense.id}")).body(response)
    }

    @GetMapping
    fun getAll(@RequestParam(required = false) month: YearMonth?): List<FixedExpenseResponse> {
        val target = month ?: YearMonth.now(clock)
        return fixedExpenseUseCase.getAll().map { FixedExpenseResponse.from(it, target) }
    }

    @PostMapping("/{id}/terminate")
    fun terminate(
        @PathVariable id: Long,
        @Valid @RequestBody request: TerminateFixedExpenseRequest,
    ): FixedExpenseResponse {
        val lastMonth = request.lastMonth!!
        return FixedExpenseResponse.from(fixedExpenseUseCase.terminate(FixedExpenseId(id), lastMonth), lastMonth)
    }

    /** [month] 에 아직 기록되지 않은 고정비를 기록한다. 멱등이다. */
    @PostMapping("/records")
    fun record(@RequestParam month: YearMonth): RecordFixedExpensesResponse =
        RecordFixedExpensesResponse(month, fixedExpenseUseCase.recordFor(month))
}

/** 할부 API (PRD 3.3). 조회는 [month] 시점의 진행 상황을 함께 준다. */
@RestController
@RequestMapping("/api/installment-plans")
class InstallmentPlanController(
    private val installmentPlanUseCase: InstallmentPlanUseCase,
    private val clock: Clock,
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterInstallmentPlanRequest): ResponseEntity<InstallmentPlanResponse> {
        val saved = installmentPlanUseCase.register(request.toCommand())
        val response = InstallmentPlanResponse.from(saved, saved.firstBillingMonth)
        return ResponseEntity.created(URI.create("/api/installment-plans/${response.id}")).body(response)
    }

    @GetMapping
    fun getAll(@RequestParam(required = false) month: YearMonth?): List<InstallmentPlanResponse> {
        val target = month ?: YearMonth.now(clock)
        return installmentPlanUseCase.getAll().map { InstallmentPlanResponse.from(it, target) }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable id: Long) = installmentPlanUseCase.cancel(InstallmentPlanId(id))
}
