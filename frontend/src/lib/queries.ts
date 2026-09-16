import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Params } from './api'
import type {
  BusinessDetail, BusinessSummary, Dashboard, GridRow, Lookups, NoteDto, PageDto, ProductDto, PurchaseDto, Report, TaskDto, TimelineItem,
  WorkspaceTree,
} from './types'

// Query keys in one place, so a mutation can refresh exactly what it changed.
export const keys = {
  lookups: ['lookups'] as const,
  products: ['products'] as const,
  dashboard: (scope: string) => ['dashboard', scope] as const,
  businesses: (params: Params) => ['businesses', params] as const,
  business: (id: number) => ['business', id] as const,
  timeline: (id: number) => ['timeline', id] as const,
  purchases: (id: number) => ['purchases', id] as const,
  tasks: (params: Params) => ['tasks', params] as const,
  overdue: (userId: number | null) => ['overdue', userId] as const,
  notes: ['notes'] as const,
  report: (params: Params) => ['report', params] as const,
  workspace: ['workspace'] as const,
  grid: (params: Params) => ['grid', params] as const,
}

export const useLookups = () =>
  useQuery({ queryKey: keys.lookups, queryFn: () => api.get<Lookups>('/lookups'), staleTime: 5 * 60_000 })

export const useProducts = () =>
  useQuery({ queryKey: keys.products, queryFn: () => api.get<ProductDto[]>('/products'), staleTime: 5 * 60_000 })

export const useDashboard = (scope: 'mine' | 'team', enabled = true) =>
  useQuery({ queryKey: keys.dashboard(scope), queryFn: () => api.get<Dashboard>('/dashboard', { scope }), refetchInterval: 5 * 60_000, enabled })

export const useBusinesses = (params: Params) =>
  useQuery({ queryKey: keys.businesses(params), queryFn: () => api.get<PageDto<BusinessSummary>>('/businesses', params), placeholderData: (previous) => previous })

export const useBusiness = (id: number | null) =>
  useQuery({ queryKey: keys.business(id ?? 0), queryFn: () => api.get<BusinessDetail>(`/businesses/${id}`), enabled: Boolean(id) })

export const useTimeline = (id: number | null) =>
  useQuery({ queryKey: keys.timeline(id ?? 0), queryFn: () => api.get<TimelineItem[]>(`/businesses/${id}/timeline`), enabled: Boolean(id) })

export const usePurchases = (id: number) =>
  useQuery({ queryKey: keys.purchases(id), queryFn: () => api.get<PurchaseDto[]>(`/businesses/${id}/purchases`) })

export const useTasks = (params: Params, enabled = true) =>
  useQuery({ queryKey: keys.tasks(params), queryFn: () => api.get<TaskDto[]>('/tasks', params), enabled, placeholderData: (previous) => previous })

export const useOverdue = (userId: number | null) =>
  useQuery({ queryKey: keys.overdue(userId), queryFn: () => api.get<TaskDto[]>('/tasks/overdue', { userId }) })

export const useNotes = () => useQuery({ queryKey: keys.notes, queryFn: () => api.get<NoteDto[]>('/notes') })

export const useReport = (params: Params) =>
  useQuery({ queryKey: keys.report(params), queryFn: () => api.get<Report>('/reports', params), placeholderData: (previous) => previous })

/** Projects, their sheets and how many businesses each holds: the left side of the Main page. */
export const useWorkspace = () =>
  useQuery({ queryKey: keys.workspace, queryFn: () => api.get<WorkspaceTree>('/workbooks/tree'), staleTime: 60_000 })

export const GRID_PAGE = 200

/**
 * The Main page's rows, 200 at a time as the list scrolls. The names list and Excel mode read the same
 * cache, so switching between them costs nothing.
 */
export const useGrid = (params: Params) =>
  useInfiniteQuery({
    queryKey: keys.grid(params),
    queryFn: ({ pageParam }) => api.get<PageDto<GridRow>>('/businesses/grid', { ...params, page: pageParam, size: GRID_PAGE }),
    initialPageParam: 0,
    getNextPageParam: (last) => ((last.page + 1) * last.size < last.total ? last.page + 1 : undefined),
    placeholderData: (previous) => previous,
    staleTime: 60_000,
  })

/** After anything that changes a business, its tasks or its history: refresh every screen that shows them. */
export function useRefreshWork() {
  const client = useQueryClient()
  return (businessId?: number | null) => {
    if (businessId) {
      client.invalidateQueries({ queryKey: keys.business(businessId) })
      client.invalidateQueries({ queryKey: keys.timeline(businessId) })
      client.invalidateQueries({ queryKey: keys.purchases(businessId) })
    }
    client.invalidateQueries({ queryKey: ['businesses'] })
    client.invalidateQueries({ queryKey: ['grid'] })
    client.invalidateQueries({ queryKey: ['workspace'] })
    client.invalidateQueries({ queryKey: ['dashboard'] })
    client.invalidateQueries({ queryKey: ['tasks'] })
    client.invalidateQueries({ queryKey: ['overdue'] })
    client.invalidateQueries({ queryKey: ['report'] })
  }
}
