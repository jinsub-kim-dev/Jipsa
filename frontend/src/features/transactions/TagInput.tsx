import { useState, type KeyboardEvent } from 'react'
import { TagChip } from '@/components/common/TagChip'
import { addTagChip, removeTagChip } from '@/lib/tags'

interface TagInputProps {
  id?: string
  value: string[]
  onChange: (tags: string[]) => void
  /** 자주 쓰는 태그. 눌러서 바로 붙인다. */
  suggestions?: string[]
}

/** 태그 칩 입력. Enter 나 쉼표로 칩을 만든다. */
export function TagInput({ id, value, onChange, suggestions = [] }: TagInputProps) {
  const [draft, setDraft] = useState('')

  const commit = () => {
    onChange(addTagChip(value, draft))
    setDraft('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      commit()
    } else if (event.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  const remaining = suggestions.filter((tag) => !value.includes(tag)).slice(0, 10)

  return (
    <div>
      <div className="border-input bg-background flex min-h-9 flex-wrap items-center gap-1 rounded-md border px-2 py-1">
        {value.map((tag) => (
          <button
            key={tag}
            type="button"
            onClick={() => onChange(removeTagChip(value, tag))}
            aria-label={`${tag} 태그 빼기`}
          >
            <TagChip tag={tag} removable className="cursor-pointer" />
          </button>
        ))}
        <input
          id={id}
          className="min-w-24 flex-1 bg-transparent px-1 py-1 text-sm outline-none"
          placeholder={value.length === 0 ? '#태그 (Enter 로 추가)' : ''}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={commit}
        />
      </div>
      {remaining.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {remaining.map((tag) => (
            <button
              key={tag}
              type="button"
              className="text-muted-foreground rounded border border-dashed px-1.5 py-0.5 text-xs hover:text-indigo-700"
              onClick={() => onChange(addTagChip(value, tag))}
            >
              #{tag}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
