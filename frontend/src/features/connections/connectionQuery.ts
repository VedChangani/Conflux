import { readPageParam } from '../../lib/pageParam'

export const CONNECTIONS_PAGE_SIZE = 12

export interface ConnectionsQuery {
  status: string | null
  page: number
}

export function readConnectionsQuery(params: URLSearchParams): ConnectionsQuery {
  const status = params.get('status')?.trim()
  return { status: status ? status : null, page: readPageParam(params) }
}

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
  status?: string | null
  page?: number
}

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
