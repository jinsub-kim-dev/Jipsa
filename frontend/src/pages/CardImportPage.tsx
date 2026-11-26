import { Upload } from 'lucide-react'
import { useState, type DragEvent } from 'react'
import { ApiError } from '@/api/client'
import { Section } from '@/components/common/Section'
import { buttonVariants } from '@/components/ui/button-variants'
import { CardImportResultCard } from '@/features/cardImport/CardImportResultCard'
import { useImportCardStatement } from '@/features/cardImport/queries'
import {
  ACCEPTED_STATEMENT_TYPES,
  completeJob,
  enqueueJobs,
  failJob,
  type ImportJob,
  uploadingJobs,
} from '@/lib/cardImport'
import { cn } from '@/lib/utils'

let nextJobId = 0

/**
 * 카드 이용내역 가져오기 (PRD 3.5).
 *
 * 카드사에서 내려받은 엑셀을 올리면 서버가 카드사를 알아보고 반영한다. 이미 있는 내역은 서버가
 * 무시하므로 기간이 겹치는 파일을 다시 올려도 된다. 파일은 하나씩 차례로 보낸다. 동시에 보내면
 * 두 파일이 같은 구매를 담고 있을 때 둘 다 "아직 없음" 으로 보고 중복이 생길 수 있다.
 */
export function CardImportPage() {
  const [jobs, setJobs] = useState<ImportJob[]>([])
  const [dragging, setDragging] = useState(false)
  const importStatement = useImportCardStatement()

  async function upload(files: File[]) {
    if (files.length === 0) return
    const pending = files.map((file) => ({ id: `job-${nextJobId++}`, name: file.name, file }))
    const queued = enqueueJobs([], pending)
    setJobs((current) => [...queued, ...current])

    const toUpload = new Set(uploadingJobs(queued).map((job) => job.id))
    for (const { id, file } of pending) {
      if (!toUpload.has(id)) continue
      try {
        const result = await importStatement.mutateAsync(file)
        setJobs((current) => completeJob(current, id, result))
      } catch (error) {
        const message = error instanceof ApiError ? error.message : '파일을 가져오지 못했어요.'
        setJobs((current) => failJob(current, id, message))
      }
    }
  }

  function onDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    setDragging(false)
    void upload([...event.dataTransfer.files])
  }

  return (
    <div className="space-y-4">
      <Section title="카드 내역 가져오기" hint="삼성카드(xlsx) · 우리카드(xls) · 현대카드(xls)">
        <label
          htmlFor="card-statement-file"
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={cn(
            'flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed px-6 py-10 text-center transition-colors',
            dragging ? 'border-primary bg-primary/5' : 'hover:bg-muted/40',
          )}
        >
          <Upload className="text-muted-foreground size-8" />
          <b className="text-sm">
            카드사에서 내려받은 이용내역 엑셀을 끌어다 놓거나 눌러서 고르세요
          </b>
          <span className="text-muted-foreground text-xs">
            여러 파일을 한 번에 올릴 수 있어요. 카드사는 파일을 보고 알아내고, 이미 있는 내역은 다시
            넣지 않아요.
          </span>
          {/* 라벨 안에 버튼을 두면 파일 창이 두 번 열린다. 모양만 버튼인 span 을 둔다 */}
          <span className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'mt-2')}>
            파일 고르기
          </span>
        </label>
        <input
          id="card-statement-file"
          type="file"
          multiple
          accept={ACCEPTED_STATEMENT_TYPES}
          className="sr-only"
          aria-label="카드 이용내역 파일"
          onChange={(event) => {
            void upload([...(event.target.files ?? [])])
            // 같은 파일을 다시 고를 수 있게 비운다
            event.target.value = ''
          }}
        />
      </Section>

      {jobs.map((job) => (
        <CardImportResultCard key={job.id} job={job} />
      ))}
    </div>
  )
}
