/**
 * 태그 입력 처리. 사용자가 `#점심` 이나 `점심` 처럼 자유롭게 입력한 값을 다듬는다.
 *
 * 최종 정규화(앞의 `#` 제거, 중복 합치기, 개수 제한)는 서버 도메인이 한다. 여기서는 입력 칩이
 * 겹쳐 보이지 않게 하는 정도만 한다. 규칙을 서버와 다르게 만들지 않도록 같은 방향으로만 다듬는다.
 */

/** 입력 하나를 칩으로 쓸 값으로 바꾼다. 비면 `null`. */
export function toTagChip(raw: string): string | null {
  const trimmed = raw.trim().replace(/^#/, '').trim()
  return trimmed === '' ? null : trimmed
}

/** 칩 목록에 새 입력을 더한다. 이미 있거나 비어 있으면 그대로 둔다. */
export function addTagChip(chips: readonly string[], raw: string): string[] {
  const chip = toTagChip(raw)
  if (chip === null || chips.includes(chip)) return [...chips]
  return [...chips, chip]
}

/** 칩 목록에서 하나를 뺀다. */
export function removeTagChip(chips: readonly string[], chip: string): string[] {
  return chips.filter((value) => value !== chip)
}
