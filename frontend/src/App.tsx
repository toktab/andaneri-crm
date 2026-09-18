import { lazy, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './lib/auth'
import { Layout } from './components/Layout'
import { Spinner } from './components/ui'
import { LoginPage } from './pages/LoginPage'

// Each screen is its own download, fetched the first time it is opened, so the first load stays small.
// The routes are few enough that naming each import is clearer than a helper.
const MainPage = lazy(() => import('./pages/MainPage').then((m) => ({ default: m.MainPage })))
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const CallModePage = lazy(() => import('./pages/CallModePage').then((m) => ({ default: m.CallModePage })))
const BusinessesPage = lazy(() => import('./pages/BusinessesPage').then((m) => ({ default: m.BusinessesPage })))
const BusinessPage = lazy(() => import('./pages/BusinessPage').then((m) => ({ default: m.BusinessPage })))
const PipelinePage = lazy(() => import('./pages/PipelinePage').then((m) => ({ default: m.PipelinePage })))
const CalendarPage = lazy(() => import('./pages/CalendarPage').then((m) => ({ default: m.CalendarPage })))
const TasksPage = lazy(() => import('./pages/TasksPage').then((m) => ({ default: m.TasksPage })))
const NotesPage = lazy(() => import('./pages/NotesPage').then((m) => ({ default: m.NotesPage })))
const ProductsPage = lazy(() => import('./pages/ProductsPage').then((m) => ({ default: m.ProductsPage })))
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage })))
const ImportPage = lazy(() => import('./pages/ImportPage').then((m) => ({ default: m.ImportPage })))
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })))
const SecurityPage = lazy(() => import('./pages/SecurityPage').then((m) => ({ default: m.SecurityPage })))
const HistoryPage = lazy(() => import('./pages/HistoryPage').then((m) => ({ default: m.HistoryPage })))
const FlavorPage = lazy(() => import('./pages/FlavorPage').then((m) => ({ default: m.FlavorPage })))

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, checking } = useAuth()
  const location = useLocation()
  if (checking) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <Spinner className="size-8 text-brand-600" />
      </div>
    )
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <>{children}</>
}

export function App() {
  const { isAdmin, isRoot } = useAuth()
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="main" element={<MainPage />} />
        <Route path="calls" element={<CallModePage />} />
        <Route path="calls/:id" element={<CallModePage />} />
        <Route path="businesses" element={<BusinessesPage />} />
        <Route path="businesses/:id" element={<BusinessPage />} />
        <Route path="pipeline" element={<PipelinePage />} />
        <Route path="flavors" element={<FlavorPage />} />
        <Route path="flavors/:id" element={<FlavorPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="notes" element={<NotesPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="import" element={<ImportPage />} />
        <Route path="history" element={<HistoryPage />} />
        {isAdmin && <Route path="admin" element={<AdminPage />} />}
        {isRoot && <Route path="security" element={<SecurityPage />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
