import { describe, expect, it } from 'vitest'
import { matchesHighlight } from './highlight'

describe('calendar highlight - 하이라이트 조건', () => {
  const lunch = { kind: 'GENERAL' as const, tags: ['점심', '혼밥'] }

  it('조건이 없으면 모두 해당한다', () => {
    expect(matchesHighlight(lunch, null)).toBe(true)
  })

  it('지출 종류로 고른다', () => {
    expect(matchesHighlight(lunch, { type: 'kind', value: 'GENERAL' })).toBe(true)
    expect(matchesHighlight(lunch, { type: 'kind', value: 'FIXED' })).toBe(false)
  })

  it('태그로 고른다', () => {
    expect(matchesHighlight(lunch, { type: 'tag', value: '혼밥' })).toBe(true)
    expect(matchesHighlight(lunch, { type: 'tag', value: '회식' })).toBe(false)
  })
})
