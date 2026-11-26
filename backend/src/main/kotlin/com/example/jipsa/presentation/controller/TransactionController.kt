package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.TransactionUseCase
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.model.ExpenseKind
import com.example.jipsa.domain.model.Tag
import com.example.jipsa.domain.model.TransactionId
import com.example.jipsa.domain.model.TransactionSearchCriteria
import com.example.jipsa.presentation.dto.RegisterTransactionRequest
import com.example.jipsa.presentation.dto.TransactionResponse
import com.example.jipsa.presentation.dto.UpdateTransactionRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.YearMonth

/**
 * 거래 API.
 *
 * `POST` 는 일반 지출만 등록한다. 고정비·할부 거래는 `/api/fixed-expenses`, `/api/installment-plans` 가 만든다.
 */
@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactionUseCase: TransactionUseCase,
) {

    @PostMapping
    fun register(@Valid @RequestBody request: RegisterTransactionRequest): ResponseEntity<TransactionResponse> {
        val response = TransactionResponse.from(transactionUseCase.registerGeneral(request.toCommand()))
        return ResponseEntity.created(URI.create("/api/transactions/${response.id}")).body(response)
    }

    /**
     * 한 달의 거래를 검색한다. 소비일 내림차순.
     *
     * @param keyword 사용처·메모·태그 이름에 부분 일치
     */
    @GetMapping
    fun search(
        @RequestParam month: YearMonth,
        @RequestParam(required = false) kind: ExpenseKind?,
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) keyword: String?,
    ): List<TransactionResponse> = transactionUseCase
        .search(
            TransactionSearchCriteria(
                from = month.atDay(1),
                to = month.atEndOfMonth(),
                kind = kind,
                categoryId = categoryId?.let(::CategoryId),
                tag = tag?.takeIf(String::isNotBlank)?.let(Tag::of),
                keyword = keyword,
            ),
        )
        .map(TransactionResponse::from)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): TransactionResponse =
        TransactionResponse.from(transactionUseCase.getById(TransactionId(id)))

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: UpdateTransactionRequest): TransactionResponse =
        TransactionResponse.from(transactionUseCase.update(request.toCommand(TransactionId(id))))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = transactionUseCase.delete(TransactionId(id))
}
