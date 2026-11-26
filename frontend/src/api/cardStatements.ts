import { apiClient } from './client'
import type { CardImportResult } from '@/types/api'

/** 카드 이용내역은 수백 줄이라도 한 번에 저장한다. 기본 타임아웃(10초)보다 넉넉히 둔다. */
const UPLOAD_TIMEOUT_MS = 60_000

export const cardStatementApi = {
  /**
   * 카드 이용내역 엑셀을 가져온다. 카드사는 서버가 파일을 보고 알아낸다.
   * 이미 있는 내역은 서버가 건너뛰므로 같은 파일을 다시 올려도 된다.
   */
  async upload(file: File): Promise<CardImportResult> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await apiClient.post<CardImportResult>('/api/card-statements', form, {
      // 기본 헤더(JSON)를 덮어 써야 axios 가 FormData 를 JSON 으로 바꾸지 않는다.
      // 경계(boundary)는 브라우저가 채운다.
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: UPLOAD_TIMEOUT_MS,
    })
    return data
  },
}
