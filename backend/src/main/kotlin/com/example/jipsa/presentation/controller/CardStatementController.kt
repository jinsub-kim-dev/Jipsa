package com.example.jipsa.presentation.controller

import com.example.jipsa.application.port.CardStatementImportUseCase
import com.example.jipsa.presentation.dto.CardImportResponse
import com.example.jipsa.presentation.statement.CardStatementFileReader
import com.example.jipsa.presentation.statement.CardStatementFormatException
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** 카드 이용내역 가져오기 API (PRD 3.5). */
@RestController
@RequestMapping("/api/card-statements")
class CardStatementController(
    private val cardStatementImportUseCase: CardStatementImportUseCase,
    private val cardStatementFileReader: CardStatementFileReader,
) {

    /** 이용내역 엑셀을 가져온다. 카드사는 파일을 보고 알아낸다. 다시 올려도 이미 있는 것은 건너뛴다. */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importStatement(@RequestParam("file") file: MultipartFile): CardImportResponse = importFile(file, issuer = null)

    /** [issuer] 카드사의 이용내역 엑셀을 가져온다. */
    @PostMapping("/{issuer}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importStatementOf(@PathVariable issuer: String, @RequestParam("file") file: MultipartFile): CardImportResponse =
        importFile(file, issuer)

    private fun importFile(file: MultipartFile, issuer: String?): CardImportResponse {
        if (file.isEmpty) throw CardStatementFormatException("빈 파일입니다.")
        val reading = file.inputStream.use { cardStatementFileReader.read(it, issuer) }
        val result = cardStatementImportUseCase.importStatement(reading.statement)
        return CardImportResponse.of(file.originalFilename, reading, result)
    }
}
