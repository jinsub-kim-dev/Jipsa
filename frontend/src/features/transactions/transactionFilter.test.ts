import { describe, expect, it } from 'vitest'
import {
  hasActiveFilter,
  parseTransactionFilter,
  serializeTransactionFilter,
  transactionsLink,
} from './transactionFilter'

describe('transactionFilter - URL 필터', () => {
  it('주소의 조건을 읽는다', () => {
    const filter = parseTransactionFilter(
      new URLSearchParams('kind=FIXED&categoryId=7&tag=집&keyword=월세'),
    )

    expect(filter).toEqual({ kind: 'FIXED', categoryId: 7, tag: '집', keyword: '월세' })
  })

  it.each([
    ['kind=SALARY'],
    ['categoryId=abc'],
    ['categoryId=0'],
    ['categoryId=1.5'],
    ['tag=%20%20'],
    ['keyword='],
  ])('잘못된 값 %s 는 무시한다', (query) => {
    expect(parseTransactionFilter(new URLSearchParams(query))).toEqual({})
  })

  it('왕복해도 같은 필터다', () => {
    const filter = {
      kind: 'INSTALLMENT' as const,
      categoryId: 11,
      tag: '전자기기',
      keyword: '맥북',
    }

    expect(parseTransactionFilter(serializeTransactionFilter(filter))).toEqual(filter)
  })

  it('값이 없는 조건은 주소에 남기지 않는다', () => {
    expect(serializeTransactionFilter({ tag: '점심' }).toString()).toBe('tag=%EC%A0%90%EC%8B%AC')
    expect(transactionsLink({})).toBe('/transactions')
    expect(transactionsLink({ categoryId: 4 })).toBe('/transactions?categoryId=4')
  })

  it('필터가 걸려 있는지 판단한다', () => {
    expect(hasActiveFilter({})).toBe(false)
    expect(hasActiveFilter({ keyword: '커피' })).toBe(true)
  })
})
