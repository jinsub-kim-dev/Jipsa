import { QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useState, type ReactNode } from 'react'
import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { createQueryClient } from '@/lib/queryClient'
import { PlaceholderPage } from '@/pages/PlaceholderPage'

/**
 * 페이지는 라우트 단위로 지연 로딩한다. 차트 라이브러리(Recharts)가 무거워 화면별로 쪼갠다.
 */
const DashboardPage = lazy(() =>
  import('@/pages/DashboardPage').then((module) => ({ default: module.DashboardPage })),
)
const CalendarPage = lazy(() =>
  import('@/pages/CalendarPage').then((module) => ({ default: module.CalendarPage })),
)
const TransactionsPage = lazy(() =>
  import('@/pages/TransactionsPage').then((module) => ({ default: module.TransactionsPage })),
)
const RecurringPage = lazy(() =>
  import('@/pages/RecurringPage').then((module) => ({ default: module.RecurringPage })),
)

export function App() {
  // QueryClient 는 렌더마다 새로 만들면 캐시가 초기화되므로 상태로 한 번만 만든다.
  const [queryClient] = useState(createQueryClient)

  return (
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route
            index
            element={
              <PageBoundary>
                <DashboardPage />
              </PageBoundary>
            }
          />
          <Route
            path="calendar"
            element={
              <PageBoundary>
                <CalendarPage />
              </PageBoundary>
            }
          />
          <Route
            path="transactions"
            element={
              <PageBoundary>
                <TransactionsPage />
              </PageBoundary>
            }
          />
          <Route
            path="recurring"
            element={
              <PageBoundary>
                <RecurringPage />
              </PageBoundary>
            }
          />
          <Route path="tags" element={<PlaceholderPage title="태그 관리" />} />
          <Route path="settings" element={<PlaceholderPage title="설정" />} />
          <Route path="*" element={<PlaceholderPage title="페이지를 찾을 수 없습니다" />} />
        </Route>
      </Routes>
    </QueryClientProvider>
  )
}

function PageBoundary({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={<p className="text-muted-foreground py-16 text-center text-sm">불러오는 중…</p>}
    >
      {children}
    </Suspense>
  )
}
