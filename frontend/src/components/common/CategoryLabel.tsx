import type { Category } from '@/types/domain'

export function CategoryLabel({ category }: { category: Category | undefined }) {
  if (!category) return <span className="text-muted-foreground">–</span>
  return (
    <span className="whitespace-nowrap">
      {category.icon} {category.name}
    </span>
  )
}
