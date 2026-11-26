import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderWithRouter } from '@/test/renderWithQuery'
import type { CardImportResult } from '@/types/api'
import { CardImportResultCard } from './CardImportResultCard'

const result: CardImportResult = {
  fileName: '내역.xlsx',
  issuer: 'samsung',
  paymentMethod: '삼성카드',
  totalRowCount: 86,
  importedCount: 82,
  importedTransactionCount: 81,
  importedInstallmentPlanCount: 1,
  duplicateCount: 0,
  uncategorizedCategoryId: 13,
  uncategorizedCount: 1,
  uncategorized: [{ spentDate: '2026-11-07', merchant: '라마바상점', amount: 23_000 }],
  unprocessedCount: 3,
  unprocessed: [
    {
      reason: '승인금액(원) 은(는) 원 단위 정수여야 합니다.',
      rowNumber: 12,
      content: '2026.11.24 | 맘스터치',
    },
    {
      reason: '승인이 취소되었습니다.',
      spentDate: '2026-09-28',
      merchant: '카카오T택시',
      amount: 12_000,
    },
    {
      reason: '승인이 취소되었습니다.',
      spentDate: '2026-09-28',
      merchant: '카카오_택시',
      amount: 12_000,
    },
  ],
}

describe('CardImportResultCard - 가져오기 결과', () => {
  it('전체 줄·추가·중복·미분류·처리 안 됨 건수를 보여준다', () => {
    renderWithRouter(
      <CardImportResultCard job={{ id: '1', fileName: '내역.xlsx', status: 'done', result }} />,
    )

    expect(screen.getByText('전체 줄').nextSibling).toHaveTextContent('86')
    expect(screen.getByText('추가').nextSibling).toHaveTextContent('82할부 1건 포함')
    expect(screen.getByText('미분류').nextSibling).toHaveTextContent('1')
    expect(screen.getByText('처리 안 됨').nextSibling).toHaveTextContent('3')
    expect(screen.getByText(/확인할 내역 있음/)).toBeInTheDocument()
  })

  it('처리 안 됨은 처음부터 펼쳐 사유별 건수와 항목을 보여준다', () => {
    renderWithRouter(
      <CardImportResultCard job={{ id: '1', fileName: '내역.xlsx', status: 'done', result }} />,
    )

    expect(screen.getByText('2건')).toBeInTheDocument()
    expect(screen.getByText('12행')).toBeInTheDocument()
    expect(screen.getByText('09.28 카카오T택시')).toBeInTheDocument()
  })

  it('미분류는 펼치면 목록과 미분류 거래 링크를 보여준다', async () => {
    renderWithRouter(
      <CardImportResultCard job={{ id: '1', fileName: '내역.xlsx', status: 'done', result }} />,
    )
    expect(screen.queryByText('라마바상점')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /미분류 1건/ }))

    expect(screen.getByText('라마바상점')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /미분류 거래 보기/ })).toHaveAttribute(
      'href',
      '/transactions?categoryId=13',
    )
  })

  it('실패한 파일은 서버가 준 사유를 보여준다', () => {
    renderWithRouter(
      <CardImportResultCard
        job={{
          id: '1',
          fileName: 'a.xlsx',
          status: 'failed',
          message: '지원하는 카드사의 이용내역 형식이 아닙니다.',
        }}
      />,
    )

    expect(screen.getByText('지원하는 카드사의 이용내역 형식이 아닙니다.')).toBeInTheDocument()
    expect(screen.getByText('가져오지 못함')).toBeInTheDocument()
  })
})
