import { describe, expect, it } from 'vitest'
import { cellOpacity, isDarkCell, normalizeRow } from './heatmap'

describe('heatmap - 히트맵 셀 강도', () => {
  it('행에서 가장 많이 쓴 달을 1로 정규화한다', () => {
    expect(normalizeRow([500, 250, 0, 1000])).toEqual([0.5, 0.25, 0, 1])
  })

  it('행이 모두 0원이면 모두 0이다', () => {
    expect(normalizeRow([0, 0])).toEqual([0, 0])
    expect(normalizeRow([])).toEqual([])
  })

  it('지출이 없는 칸은 최소 불투명도로 격자만 보인다', () => {
    expect(cellOpacity(0)).toBe(0.02)
  })

  it('강도에 따라 불투명도가 커지고 범위를 벗어나지 않는다', () => {
    expect(cellOpacity(1)).toBeCloseTo(0.66)
    expect(cellOpacity(0.5)).toBeCloseTo(0.36)
    expect(cellOpacity(2)).toBeCloseTo(0.66)
    expect(cellOpacity(-1)).toBe(0.02)
  })

  it('진한 칸은 글자를 밝게 쓴다', () => {
    expect(isDarkCell(0.7)).toBe(true)
    expect(isDarkCell(0.62)).toBe(false)
  })
})
