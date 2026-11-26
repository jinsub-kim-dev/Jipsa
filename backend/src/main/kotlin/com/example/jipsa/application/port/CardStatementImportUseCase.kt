package com.example.jipsa.application.port

import com.example.jipsa.domain.model.CardPurchase
import com.example.jipsa.domain.model.CardStatement
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.service.UnprocessedPurchase

/** 카드 이용내역 가져오기 인바운드 포트 (PRD 3.5). */
interface CardStatementImportUseCase {

    /**
     * 이용내역을 거래·할부 계획으로 옮긴다. 이미 있는 것은 건너뛰고, 가져올 수 없는 구매는 사유와 함께 뺀다.
     * 저장은 전부 성공하거나 전부 실패한다.
     */
    fun importStatement(statement: CardStatement): CardImportResult
}

data class CardImportResult(
    /** 새로 만든 일반 지출 수. 고정비 거래는 [fixedExpenseMatches] 에 따로 센다. */
    val importedTransactionCount: Int,
    /** 등록된 고정비의 그 달 거래가 된 구매. 새로 만들었거나 자동 기록을 실제값으로 고쳤다. */
    val fixedExpenseMatches: List<CardPurchase>,
    /** 새로 등록한 할부 계획 수. 회차 거래는 세지 않는다. */
    val importedInstallmentPlanCount: Int,
    /** 이미 있어서 건너뛴 구매. */
    val duplicates: List<CardPurchase>,
    /** 새로 만든 것 중 미분류 카테고리로 들어간 구매. */
    val uncategorized: List<CardPurchase>,
    /** 미분류 카테고리. 화면이 "미분류 거래 보기" 링크를 만들 때 쓴다. */
    val uncategorizedCategoryId: CategoryId,
    /** 가져오지 않은 구매와 사유. */
    val unprocessed: List<UnprocessedPurchase>,
)
