/**
 * 히트맵 셀의 색 강도. **표시 전용 계산**이다.
 *
 * 카테고리마다 쓰는 금액의 크기가 크게 달라(주거비 수십만 원, 커피 몇만 원) 표 전체로
 * 정규화하면 작은 카테고리는 모두 흐려진다. 그래서 **행(카테고리) 안에서** 가장 많이 쓴 달을
 * 1.0 으로 둔다. 증감률처럼 의미를 갖는 계산은 서버가 하고, 여기서는 색만 정한다.
 */

/** 행의 각 값을 0~1 로 정규화한다. 행이 모두 0원이면 모두 0이다. */
export function normalizeRow(amounts: readonly number[]): number[] {
  const max = Math.max(0, ...amounts)
  if (max === 0) return amounts.map(() => 0)
  return amounts.map((amount) => amount / max)
}

/** 셀 배경의 불투명도. 지출이 없는 칸도 격자가 보이도록 최소값을 둔다. */
export function cellOpacity(intensity: number): number {
  const clamped = Math.min(1, Math.max(0, intensity))
  return clamped === 0 ? EMPTY_OPACITY : MIN_OPACITY + clamped * (MAX_OPACITY - MIN_OPACITY)
}

/** 배경이 진한 칸은 글자를 밝게 바꾼다. */
export function isDarkCell(intensity: number): boolean {
  return intensity > DARK_THRESHOLD
}

const EMPTY_OPACITY = 0.02
const MIN_OPACITY = 0.06
const MAX_OPACITY = 0.66
const DARK_THRESHOLD = 0.62
