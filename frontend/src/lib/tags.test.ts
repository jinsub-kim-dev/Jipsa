import { describe, expect, it } from 'vitest'
import { addTagChip, removeTagChip, toTagChip } from './tags'

describe('tags - 태그 입력', () => {
  it.each([
    ['점심', '점심'],
    ['#점심', '점심'],
    ['  # 점심 ', '점심'],
  ])('%s 를 칩 값 %s 로 다듬는다', (raw, expected) => {
    expect(toTagChip(raw)).toBe(expected)
  })

  it('비어 있으면 칩을 만들지 않는다', () => {
    expect(toTagChip(' # ')).toBeNull()
    expect(addTagChip(['점심'], '  ')).toEqual(['점심'])
  })

  it('이미 있는 태그는 다시 더하지 않는다', () => {
    expect(addTagChip(['점심'], '#점심')).toEqual(['점심'])
    expect(addTagChip(['점심'], '혼밥')).toEqual(['점심', '혼밥'])
  })

  it('칩을 뺀다', () => {
    expect(removeTagChip(['점심', '혼밥'], '점심')).toEqual(['혼밥'])
  })
})
