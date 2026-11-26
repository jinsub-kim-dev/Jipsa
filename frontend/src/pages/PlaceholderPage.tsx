import { Card } from '@/components/ui/card'

/** 아직 설계하지 않은 메뉴. */
export function PlaceholderPage({ title }: { title: string }) {
  return (
    <Card className="text-muted-foreground px-5 py-20 text-center">
      <div className="mb-3 text-4xl">🚧</div>
      <b className="text-foreground">{title}</b>
      <p className="mt-1 text-sm">이 메뉴는 다음 단계에서 설계해요.</p>
    </Card>
  )
}
