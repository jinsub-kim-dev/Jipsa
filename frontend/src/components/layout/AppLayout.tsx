import { CalendarDays, LayoutDashboard, Plus, Receipt, Repeat, Settings, Tags } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { APP_ENVIRONMENT, IS_DEV_ENVIRONMENT } from '@/config/env'
import { ExpenseFormDialog } from '@/features/transactions/ExpenseFormDialog'
import { cn } from '@/lib/utils'
import { useUiStore } from '@/store/uiStore'
import { MonthNavigator } from './MonthNavigator'

const NAV_ITEMS = [
  { to: '/', label: '대시보드', icon: LayoutDashboard },
  { to: '/calendar', label: '캘린더', icon: CalendarDays },
  { to: '/transactions', label: '거래 내역', icon: Receipt },
  { to: '/recurring', label: '고정비·할부', icon: Repeat },
  { to: '/tags', label: '태그 관리', icon: Tags },
  { to: '/settings', label: '설정', icon: Settings },
] as const

export function AppLayout() {
  const openExpenseForm = useUiStore((state) => state.openExpenseForm)

  return (
    <div className="flex min-h-screen">
      <aside className="bg-sidebar text-sidebar-foreground sticky top-0 hidden h-screen w-56 shrink-0 flex-col px-4 py-6 md:flex">
        <div className="mx-2 mb-7 flex items-center gap-2">
          <span className="text-xl font-extrabold text-white">
            Jipsa<span className="text-blue-400">.</span>
          </span>
          {/* 로컬 화면을 배포 화면으로 착각하고 데이터를 넣는 일을 막는다 */}
          {IS_DEV_ENVIRONMENT && (
            <span
              className="rounded border border-amber-400/50 px-1.5 py-0.5 text-[10px] font-semibold text-amber-300"
              title={`실행 환경: ${APP_ENVIRONMENT}`}
            >
              LOCAL
            </span>
          )}
        </div>
        <nav className="flex flex-col gap-0.5" aria-label="주요 화면">
          {NAV_ITEMS.map(({ to, label, icon: Icon }, index) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2.5 rounded-lg px-3 py-2.5 text-sm transition-colors',
                  index === 4 && 'mt-3',
                  isActive
                    ? 'bg-sidebar-accent font-semibold text-white'
                    : 'hover:bg-sidebar-accent/60',
                )
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <main className="min-w-0 flex-1 px-4 py-6 md:px-8">
        <header className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <MonthNavigator />
          <Button onClick={() => openExpenseForm()}>
            <Plus />
            지출 추가
          </Button>
        </header>
        <Outlet />
      </main>

      {/* 모달은 레이아웃에 한 번만 마운트해 모든 화면에서 같은 상태를 공유한다 */}
      <ExpenseFormDialog />
    </div>
  )
}
