/**
 * The marketplace URL is the single source of truth for search, filters, sort and page.
 * URL parameters use the backend's names and raw enum values, so a shared link means the
 * same query everywhere. The one difference is `page`: 1-based in the URL (what people
 * see), zero-based for the API.
 *
 * Values are passed to the backend as they are; it validates them and rejects anything
 * it does not accept with a 400, which the page shows.
 */

export const FILTER_KEYS = ['assetType', 'marketplaceMode', 'category', 'stage'] as const
export type FilterKey = (typeof FILTER_KEYS)[number]

export type DiscoveryParam = 'search' | FilterKey | 'sort' | 'page' | 'size'

/** The backend's default page size. */
export const DEFAULT_PAGE_SIZE = 12
/** Choices offered in the UI; all within the backend's 1–50 limit. */
export const PAGE_SIZE_OPTIONS = [12, 24, 48] as const
/** Backend limit on `search` length. */
export const SEARCH_MAX_LENGTH = 100
/** Backend limit on the zero-based page number (10,000), as a 1-based page. */
export const MAX_PAGE = 10_001

export interface DiscoveryQuery {
  search: string
  assetType: string | null
  marketplaceMode: string | null
  category: string | null
  stage: string | null
  sort: string | null
  /** 1-based. */
  page: number
  size: string | null
}

function nonEmpty(value: string | null): string | null {
  return value === null || value.trim() === '' ? null : value
}

/** Reads the query from the URL. A missing or malformed page means the first page. */
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

/** The `GET /listings` query string, in a stable parameter order. */
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

/**
 * A copy of `params` with the given changes applied (`null` or `''` removes a parameter).
 * Any change other than to `page` itself returns to the first page, since the old page
 * number means nothing for a different result set. Unrelated parameters are kept.
 */
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

/** Whether any search term or filter narrows the results (sort and paging do not). */
export function hasActiveFilters(query: DiscoveryQuery): boolean {
  return query.search !== '' || FILTER_KEYS.some((key) => query[key] !== null)
}

/** `params` without search and filters; sort and page size are kept. */
export function withoutFilters(params: URLSearchParams): URLSearchParams {
  return withChanges(params, {
    search: null,
    assetType: null,
    marketplaceMode: null,
    category: null,
    stage: null,
  })
}
