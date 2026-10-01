import { readPageParam } from '../../lib/pageParam'

/** The backend's default page size for connection lists. */
export const CONNECTIONS_PAGE_SIZE = 12

export interface ConnectionsQuery {
  /** Raw `status` URL value, passed through for the backend to validate; `null` means all. */
  status: string | null
  /** 1-based. */
  page: number
}

/** Reads `?status=…&page=…` from the URL. The page is 1-based there, as on the marketplace. */
export function readConnectionsQuery(params: URLSearchParams): ConnectionsQuery {
  const status = params.get('status')?.trim()
  return { status: status ? status : null, page: readPageParam(params) }
}

/** The list endpoint's query string, in a stable order, with the zero-based page. */
export function toConnectionsApiSearch(query: ConnectionsQuery): string {
  const api = new URLSearchParams()
  if (query.status !== null) {
    api.set('status', query.status)
  }
  api.set('page', String(query.page - 1))
  api.set('size', String(CONNECTIONS_PAGE_SIZE))
  return api.toString()
}

export interface ConnectionsQueryChanges {
  /** `null` shows all statuses. */
  status?: string | null
  /** 1-based; omitted or 1 means the first page. */
  page?: number
}

/**
 * The URL query string with `changes` applied. Any change returns to the first page unless
 * it sets the page itself; unrelated parameters are kept.
 */
export function withConnectionsQuery(params: URLSearchParams, changes: ConnectionsQueryChanges): string {
  const next = new URLSearchParams(params)
  if (changes.status !== undefined) {
    if (changes.status) {
      next.set('status', changes.status)
    } else {
      next.delete('status')
    }
  }
  if (changes.page !== undefined && changes.page > 1) {
    next.set('page', String(changes.page))
  } else {
    next.delete('page')
  }
  return next.toString()
}
