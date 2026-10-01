export const FILTER_KEYS = ['assetType', 'marketplaceMode', 'category', 'stage'] as const
export type FilterKey = (typeof FILTER_KEYS)[number]

export type DiscoveryParam = 'search' | FilterKey | 'sort' | 'page' | 'size'

export const DEFAULT_PAGE_SIZE = 12
export const PAGE_SIZE_OPTIONS = [12, 24, 48] as const
export const SEARCH_MAX_LENGTH = 100

export interface DiscoveryQuery {
  search: string
  assetType: string | null
  marketplaceMode: string | null
  category: string | null
  stage: string | null
  sort: string | null
  page: number
  size: string | null
}

function nonEmpty(value: string | null): string | null {
  return value === null || value.trim() === '' ? null : value
}

export function readDiscoveryQuery(params: URLSearchParams): DiscoveryQuery {
  const rawPage = params.get('page') ?? ''
  const page = /^\d+$/.test(rawPage) ? Number(rawPage) : 1
  return {
    search: params.get('search')?.trim() ?? '',
    assetType: nonEmpty(params.get('assetType')),
    marketplaceMode: nonEmpty(params.get('marketplaceMode')),
    category: nonEmpty(params.get('category')),
    stage: nonEmpty(params.get('stage')),
    sort: nonEmpty(params.get('sort')),
    page: Math.max(page, 1),
    size: nonEmpty(params.get('size')),
  }
}

export function toApiSearch(query: DiscoveryQuery): string {
  const api = new URLSearchParams()
  if (query.search) {
    api.set('search', query.search)
  }
  for (const key of FILTER_KEYS) {
    const value = query[key]
    if (value !== null) {
      api.set(key, value)
    }
  }
  if (query.sort !== null) {
    api.set('sort', query.sort)
  }
  api.set('page', String(query.page - 1))
  api.set('size', query.size ?? String(DEFAULT_PAGE_SIZE))
  return api.toString()
}

export function withChanges(
  params: URLSearchParams,
  changes: Partial<Record<DiscoveryParam, string | number | null>>,
): URLSearchParams {
  const next = new URLSearchParams(params)
  for (const [key, value] of Object.entries(changes)) {
    if (value === null || value === undefined || value === '') {
      next.delete(key)
    } else {
      next.set(key, String(value))
    }
  }
  if (!('page' in changes) || changes.page === 1) {
    next.delete('page')
  }
  return next
}

export function hasActiveFilters(query: DiscoveryQuery): boolean {
  return query.search !== '' || FILTER_KEYS.some((key) => query[key] !== null)
}

export function withoutFilters(params: URLSearchParams): URLSearchParams {
  return withChanges(params, {
    search: null,
    assetType: null,
    marketplaceMode: null,
    category: null,
    stage: null,
  })
}
