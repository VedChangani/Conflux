import { readPageParam } from '../../lib/pageParam'
import { DEFAULT_REPORT_PAGE_SIZE, DEFAULT_REPORT_STATUS, MAX_REPORT_PAGE_SIZE } from './types'

export interface ReportsQuery {
  /** Raw `status` URL value, passed through for the backend to validate; OPEN when absent. */
  status: string
  /** 1-based. */
  page: number
  size: number
}

/** A `size` the backend accepts (1–50); anything else means the default. */
function readSizeParam(params: URLSearchParams): number {
  const raw = params.get('size') ?? ''
  if (!/^\d{1,3}$/.test(raw)) {
    return DEFAULT_REPORT_PAGE_SIZE
  }
  const size = Number(raw)
  return size >= 1 && size <= MAX_REPORT_PAGE_SIZE ? size : DEFAULT_REPORT_PAGE_SIZE
}

/** Reads `?status=…&page=…&size=…` from the URL. The page is 1-based there, as elsewhere in the app. */
export function readReportsQuery(params: URLSearchParams): ReportsQuery {
  const status = params.get('status')?.trim()
  return { status: status ? status : DEFAULT_REPORT_STATUS, page: readPageParam(params), size: readSizeParam(params) }
}

/** The queue endpoint's query string, in a stable order, with the zero-based page. */
export function toReportsApiSearch(query: ReportsQuery): string {
  const api = new URLSearchParams()
  api.set('status', query.status)
  api.set('page', String(query.page - 1))
  api.set('size', String(query.size))
  return api.toString()
}

export interface ReportsQueryChanges {
  status?: string
  /** 1-based; omitted or 1 means the first page. */
  page?: number
  size?: number
}

/**
 * The URL query string with `changes` applied. Defaults (OPEN, page 1, size 20) are left out.
 * Any change returns to the first page unless it sets the page itself; unrelated parameters
 * are kept.
 */
export function withReportsQuery(params: URLSearchParams, changes: ReportsQueryChanges): string {
  const next = new URLSearchParams(params)
  if (changes.status !== undefined) {
    if (changes.status === DEFAULT_REPORT_STATUS) {
      next.delete('status')
    } else {
      next.set('status', changes.status)
    }
  }
  if (changes.size !== undefined) {
    if (changes.size === DEFAULT_REPORT_PAGE_SIZE) {
      next.delete('size')
    } else {
      next.set('size', String(changes.size))
    }
  }
  if (changes.page !== undefined && changes.page > 1) {
    next.set('page', String(changes.page))
  } else {
    next.delete('page')
  }
  return next.toString()
}
