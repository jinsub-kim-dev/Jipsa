package com.example.jipsa.presentation.statement

import org.springframework.stereotype.Component
import java.io.InputStream

/**
 * 올라온 파일이 어느 카드사의 이용내역인지 알아보고 읽는다 (PRD 3.5).
 *
 * 사용자가 카드사를 고르지 않아도 되게 한다. 카드사마다 머리글 이름이 달라(`가맹점명`/`가맹점`/`이용가맹점`,
 * `승인금액(원)`/`승인금액`/`이용금액`) 한 파일이 두 형식에 동시에 맞지 않는다.
 */
@Component
class CardStatementFileReader(readers: List<CardStatementReader>) {

    private val readerByIssuer: Map<String, CardStatementReader> = readers.associateBy(CardStatementReader::issuer)

    val supportedIssuers: List<String> get() = readerByIssuer.keys.sorted()

    /**
     * @param issuer 카드사. `null` 이면 머리글을 보고 알아낸다
     * @throws CardStatementFormatException 엑셀·HTML 표가 아니거나, 지원하는 카드사의 형식이 아닌 경우
     */
    fun read(input: InputStream, issuer: String? = null): CardStatementReading {
        val chosen = issuer?.let {
            readerByIssuer[it] ?: throw CardStatementFormatException(
                "지원하지 않는 카드사입니다: $it. 지원: ${supportedIssuers.joinToString()}",
            )
        }
        val document = StatementDocument.open(input)
        val reader = chosen ?: readerByIssuer.values.firstOrNull { it.recognizes(document) }
            ?: throw CardStatementFormatException(
                "지원하는 카드사의 이용내역 형식이 아닙니다. 지원: ${supportedIssuers.joinToString()}",
            )
        return reader.read(document)
    }
}
