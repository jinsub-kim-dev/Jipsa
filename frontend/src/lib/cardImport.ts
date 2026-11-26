import type { CardImportResult, CardImportUnprocessed } from '@/types/api'
import { formatMonthDay } from './format'

/**
 * 카드 이용내역 가져오기 화면의 표시 로직 (PRD 3.5).
 *
 * 무엇이 중복이고 무엇이 미분류인지는 서버가 정한다. 여기서는 올린 파일의 진행 상태를
 * 관리하고, 서버가 준 결과를 읽기 좋게 묶기만 한다.
 */

/** 파일 선택 창에 걸 확장자. 카드사들은 xls(우리카드·현대카드)와 xlsx(삼성카드)로 내려준다. 현대카드의 xls 는 실제로 HTML 표지만 서버가 읽는다. */
export const ACCEPTED_STATEMENT_EXTENSIONS = ['.xls', '.xlsx'] as const

export const ACCEPTED_STATEMENT_TYPES = ACCEPTED_STATEMENT_EXTENSIONS.join(',')

/** 엑셀 파일인지. 형식(어느 카드사인지)은 서버가 판단한다. */
export function isStatementFile(fileName: string): boolean {
  const lower = fileName.toLowerCase()
  return ACCEPTED_STATEMENT_EXTENSIONS.some((extension) => lower.endsWith(extension))
}

/** 올린 파일 하나의 진행 상태. */
export type ImportJob =
  | { id: string; fileName: string; status: 'uploading' }
  | { id: string; fileName: string; status: 'done'; result: CardImportResult }
  | { id: string; fileName: string; status: 'failed'; message: string }

export interface PendingFile {
  id: string
  name: string
}

/**
 * 새로 고른 파일을 작업 목록 **앞에** 넣는다. 방금 올린 것이 위에 보여야 한다.
 * 엑셀이 아닌 파일은 서버에 보내지 않고 바로 실패로 둔다.
 */
export function enqueueJobs(jobs: ImportJob[], files: PendingFile[]): ImportJob[] {
  const added: ImportJob[] = files.map(({ id, name }) =>
    isStatementFile(name)
      ? { id, fileName: name, status: 'uploading' }
      : {
          id,
          fileName: name,
          status: 'failed',
          message: `엑셀 파일(${ACCEPTED_STATEMENT_EXTENSIONS.join(', ')})만 올릴 수 있어요.`,
        },
  )
  return [...added, ...jobs]
}

export function completeJob(jobs: ImportJob[], id: string, result: CardImportResult): ImportJob[] {
  return jobs.map((job) =>
    job.id === id ? { id, fileName: job.fileName, status: 'done', result } : job,
  )
}

export function failJob(jobs: ImportJob[], id: string, message: string): ImportJob[] {
  return jobs.map((job) =>
    job.id === id ? { id, fileName: job.fileName, status: 'failed', message } : job,
  )
}

/** 업로드를 기다리는 작업. [enqueueJobs] 가 넣은 순서대로다. */
export function uploadingJobs(jobs: ImportJob[]): ImportJob[] {
  return jobs.filter((job) => job.status === 'uploading')
}

export interface ReasonCount {
  reason: string
  count: number
}

/**
 * 처리 안 됨을 사유별로 센다. 많은 사유가 먼저 오고, 같으면 먼저 나온 사유가 먼저다.
 * 같은 사유가 수십 줄 반복되면 목록만으로는 원인이 하나라는 것이 보이지 않는다.
 */
export function countByReason(items: CardImportUnprocessed[]): ReasonCount[] {
  const counts = new Map<string, number>()
  for (const { reason } of items) counts.set(reason, (counts.get(reason) ?? 0) + 1)
  // Map 은 넣은 순서를 지키고, sort 는 안정 정렬이므로 같은 수면 먼저 나온 사유가 앞선다.
  return [...counts].map(([reason, count]) => ({ reason, count })).sort((a, b) => b.count - a.count)
}

/**
 * 처리 안 됨 한 건이 어느 거래인지. 읽지 못한 줄은 행 번호로, 읽었지만 들이지 않은 구매는
 * 날짜와 사용처로 가리킨다.
 */
export function unprocessedLabel(item: CardImportUnprocessed): string {
  if (item.rowNumber !== undefined) return `${item.rowNumber}행`
  const parts = [item.spentDate && formatMonthDay(item.spentDate), item.merchant].filter(Boolean)
  return parts.length > 0 ? parts.join(' ') : '알 수 없는 항목'
}

/** 결과가 사람이 볼 것을 남겼는지. 미분류나 처리 안 됨이 있으면 그렇다. */
export function needsAttention(result: CardImportResult): boolean {
  return result.uncategorizedCount > 0 || result.unprocessedCount > 0
}
