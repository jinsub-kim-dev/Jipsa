import { describe, expect, it } from 'vitest'
import {
  ACCEPTED_STATEMENT_TYPES,
  completeJob,
  countByReason,
  enqueueJobs,
  failJob,
  type ImportJob,
  isStatementFile,
  needsAttention,
  unprocessedLabel,
  uploadingJobs,
} from './cardImport'
import type { CardImportResult } from '@/types/api'

function result(overrides: Partial<CardImportResult> = {}): CardImportResult {
  return {
    fileName: '내역.xlsx',
    issuer: 'samsung',
    paymentMethod: '삼성카드',
    totalRowCount: 86,
    importedCount: 82,
    importedTransactionCount: 81,
    importedInstallmentPlanCount: 1,
    duplicateCount: 0,
    uncategorizedCategoryId: 13,
    uncategorizedCount: 0,
    uncategorized: [],
    unprocessedCount: 0,
    unprocessed: [],
    ...overrides,
  }
}

describe('cardImport - 지원 파일', () => {
  it.each([
    ['일시불+할부_카드이용내역조회.xlsx', true],
    ['wooricard_20261126.xls', true],
    ['대문자.XLSX', true],
    ['내역.csv', false],
    ['내역.pdf', false],
    ['xlsx', false],
    ['', false],
  ])('%s 은(는) 엑셀이다: %s', (name, expected) => {
    expect(isStatementFile(name)).toBe(expected)
  })

  it('파일 선택 창에 xls 와 xlsx 를 건다', () => {
    expect(ACCEPTED_STATEMENT_TYPES).toBe('.xls,.xlsx')
  })
})

describe('cardImport - 작업 상태', () => {
  const existing: ImportJob = { id: 'old', fileName: '지난.xlsx', status: 'done', result: result() }

  it('새 파일을 목록 앞에 업로드 중으로 넣는다', () => {
    const jobs = enqueueJobs(
      [existing],
      [
        { id: 'a', name: 'a.xlsx' },
        { id: 'b', name: 'b.xls' },
      ],
    )

    expect(jobs.map((job) => [job.id, job.status])).toEqual([
      ['a', 'uploading'],
      ['b', 'uploading'],
      ['old', 'done'],
    ])
  })

  it('엑셀이 아닌 파일은 보내지 않고 바로 실패로 둔다', () => {
    const [job] = enqueueJobs([], [{ id: 'a', name: '내역.csv' }])

    expect(job.status).toBe('failed')
    expect(job.status === 'failed' && job.message).toContain('.xls, .xlsx')
  })

  it('업로드할 작업만 넣은 순서대로 고른다', () => {
    const jobs = enqueueJobs(
      [existing],
      [
        { id: 'a', name: 'a.xlsx' },
        { id: 'b', name: 'b.csv' },
        { id: 'c', name: 'c.xls' },
      ],
    )

    expect(uploadingJobs(jobs).map((job) => job.id)).toEqual(['a', 'c'])
  })

  it('완료하면 그 작업만 결과를 갖는다', () => {
    const jobs = enqueueJobs([existing], [{ id: 'a', name: 'a.xlsx' }])

    const completed = completeJob(jobs, 'a', result({ importedCount: 3 }))

    expect(completed[0]).toEqual({
      id: 'a',
      fileName: 'a.xlsx',
      status: 'done',
      result: result({ importedCount: 3 }),
    })
    expect(completed[1]).toBe(existing)
  })

  it('실패하면 그 작업만 메시지를 갖는다', () => {
    const jobs = enqueueJobs([existing], [{ id: 'a', name: 'a.xlsx' }])

    const failed = failJob(jobs, 'a', '지원하는 카드사의 이용내역 형식이 아닙니다.')

    expect(failed[0]).toEqual({
      id: 'a',
      fileName: 'a.xlsx',
      status: 'failed',
      message: '지원하는 카드사의 이용내역 형식이 아닙니다.',
    })
    expect(failed[1]).toBe(existing)
  })

  it('없는 작업을 완료하거나 실패해도 목록은 그대로다', () => {
    expect(completeJob([existing], 'none', result())).toEqual([existing])
    expect(failJob([existing], 'none', 'x')).toEqual([existing])
  })
})

describe('cardImport - 처리 안 됨 묶기', () => {
  it('사유별로 세고 많은 사유를 먼저 둔다', () => {
    const counts = countByReason([
      { reason: '승인이 취소되었습니다.' },
      { reason: '승인금액 이(가) 숫자가 아닙니다.', rowNumber: 3 },
      { reason: '승인금액 이(가) 숫자가 아닙니다.', rowNumber: 4 },
    ])

    expect(counts).toEqual([
      { reason: '승인금액 이(가) 숫자가 아닙니다.', count: 2 },
      { reason: '승인이 취소되었습니다.', count: 1 },
    ])
  })

  it('수가 같으면 먼저 나온 사유가 먼저다', () => {
    const counts = countByReason([{ reason: 'B' }, { reason: 'A' }])

    expect(counts.map((count) => count.reason)).toEqual(['B', 'A'])
  })

  it('처리 안 됨이 없으면 빈 목록이다', () => {
    expect(countByReason([])).toEqual([])
  })

  it.each([
    [{ reason: 'x', rowNumber: 12, content: '1 | 본인' }, '12행'],
    [
      { reason: 'x', spentDate: '2026-09-28', merchant: '카카오T택시', amount: 12_000 },
      '09.28 카카오T택시',
    ],
    [{ reason: 'x', merchant: '가게' }, '가게'],
    [{ reason: 'x' }, '알 수 없는 항목'],
  ])('어느 거래인지 표기한다 (%o → %s)', (item, expected) => {
    expect(unprocessedLabel(item)).toBe(expected)
  })
})

describe('cardImport - 살펴볼 것', () => {
  it('미분류도 처리 안 됨도 없으면 살펴볼 것이 없다', () => {
    expect(needsAttention(result())).toBe(false)
  })

  it.each([
    ['미분류', { uncategorizedCount: 3 }],
    ['처리 안 됨', { unprocessedCount: 2 }],
  ])('%s 이 있으면 살펴볼 것이 있다', (_, overrides) => {
    expect(needsAttention(result(overrides))).toBe(true)
  })
})
