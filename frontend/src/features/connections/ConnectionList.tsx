import { useCallback, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { paths } from '../../app/paths'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { LoadError } from '../../components/LoadError'
import { useAsync } from '../../lib/useAsync'
import { ApiError } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../listings/Pagination'
import {
  readConnectionsQuery,
  toConnectionsApiSearch,
  withConnectionsQuery,
  type ConnectionsQueryChanges,
} from './connectionQuery'
import { connectionsApi } from './connectionsApi'
import { ConnectionItem } from './ConnectionItem'
import { CONNECTION_STATUS_LABELS, statusLabel } from './labels'
import { CONNECTION_STATUSES, type Connection, type ConnectionBox } from './types'

const numberFormat = new Intl.NumberFormat('en-US')

const FILTERS: { status: string | null; label: string }[] = [
  { status: null, label: 'All' },
  ...CONNECTION_STATUSES.map((status) => ({ status, label: CONNECTION_STATUS_LABELS[status] })),
]

const COPY: Record<ConnectionBox, { title: string; heading: string; emptyTitle: string; emptyText: string }> = {
  received: {
    title: 'Received requests',
    heading: 'Requests for your listings',
    emptyTitle: 'No requests yet',
    emptyText: 'When someone expresses interest in one of your listings, their request will appear here.',
  },
  sent: {
    title: 'Sent requests',
    heading: 'Requests you’ve sent',
    emptyTitle: 'No requests sent yet',
    emptyText: 'Express interest in a listing and you can follow the owner’s answer here.',
  },
}

function countSummary(page: PageResponse<Connection>): string {
  const total = page.totalElements
  const noun = total === 1 ? 'request' : 'requests'
  if (page.content.length === 0 || page.content.length === total) {
    return `${numberFormat.format(total)} ${noun}`
  }
  const from = page.page * page.size + 1
  const to = from + page.content.length - 1
  return `Showing ${numberFormat.format(from)}–${numberFormat.format(to)} of ${numberFormat.format(total)} ${noun}`
}

export function ConnectionList({ box }: { box: ConnectionBox }) {
  const [params] = useSearchParams()
  const location = useLocation()
  const { account } = useAuth()
  const query = readConnectionsQuery(params)
  const apiSearch = toConnectionsApiSearch(readConnectionsQuery(params))
  const copy = COPY[box]

  const load = useCallback((signal: AbortSignal) => connectionsApi.list(box, apiSearch, signal), [box, apiSearch])
  const result = useAsync(load)
  const [updates, setUpdates] = useState<ReadonlyMap<number, Connection>>(() => new Map())
  const headingRef = useRef<HTMLHeadingElement>(null)

  const handleUpdated = useCallback(
    (connection: Connection) =>
      setUpdates((current) => {
        const next = new Map(current)
        next.set(connection.id, connection)
        return next
      }),
    [],
  )

  const hrefWith = (changes: ConnectionsQueryChanges) => {
    const search = withConnectionsQuery(params, changes)
    return search ? `${location.pathname}?${search}` : location.pathname
  }
  const focusResults = () => headingRef.current?.focus()

  const page = result.data ?? result.previousData
  const loading = result.status === 'loading'
  const connections = page?.content.map((connection) => newest(connection, updates.get(connection.id))) ?? []
  const showAll = (
    <Link to={hrefWith({ status: null })} className="button button-secondary">
      Show all requests
    </Link>
  )

  let body
  if (result.status === 'error') {
    body =
      result.error instanceof ApiError && result.error.status === 400 ? (
        <ErrorMessage title="This filter isn’t valid" message="Choose one of the filters above, or show all requests.">
          <div className="button-row">{showAll}</div>
        </ErrorMessage>
      ) : (
        <LoadError title="We couldn’t load your requests" error={result.error} onRetry={result.retry} />
      )
  } else if (page === undefined) {
    body = <ConnectionListSkeleton />
  } else if (page.totalElements === 0) {
    body =
      query.status === null ? (
        <EmptyState
          title={copy.emptyTitle}
          action={
            box === 'sent' && (
              <Link to={paths.listings} className="button button-primary">
                Browse listings
              </Link>
            )
          }
        >
          {copy.emptyText}
        </EmptyState>
      ) : (
        <EmptyState title={`No ${statusLabel(query.status).toLowerCase()} requests`} action={showAll}>
          None of your {box} requests have this status.
        </EmptyState>
      )
  } else if (page.content.length === 0) {
    const lastPage = Math.max(page.totalPages, 1)
    body = (
      <EmptyState
        title={`There is no page ${query.page}`}
        action={
          <Link to={hrefWith({ page: lastPage })} className="button button-primary" onClick={focusResults}>
            Go to page {lastPage}
          </Link>
        }
      >
        These requests end on page {lastPage}.
      </EmptyState>
    )
  } else {
    body = (
      <>
        <ul className={loading ? 'connection-list results-body is-stale' : 'connection-list results-body'}>
          {connections.map((connection) => (
            <li key={connection.id}>
              <ConnectionItem
                connection={connection}
                box={box}
                accountId={account?.id ?? null}
                linkState={{ from: `${location.pathname}${location.search}` }}
                onUpdated={handleUpdated}
              />
            </li>
          ))}
        </ul>
        {page.totalPages > 1 && (
          <Pagination page={query.page} totalPages={page.totalPages} hrefFor={(target) => hrefWith({ page: target })} onNavigate={focusResults} />
        )}
      </>
    )
  }

  return (
    <div className="connections-panel">
      <title>{`${copy.title} · Conflux`}</title>
      <nav className="status-filters" aria-label="Filter by status">
        <ul>
          {FILTERS.map((filter) => {
            const active = filter.status === query.status
            return (
              <li key={filter.label}>
                <Link
                  to={hrefWith({ status: filter.status })}
                  className="status-filter"
                  data-status={filter.status?.toLowerCase()}
                  aria-current={active ? 'true' : undefined}
                >
                  {filter.label}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>

      <section className="results" aria-labelledby={`${box}-heading`} aria-busy={loading}>
        <div className="results-toolbar">
          <div className="results-heading-group">
            <h2 id={`${box}-heading`} ref={headingRef} tabIndex={-1} className="results-heading">
              {copy.heading}
            </h2>
            <p className="results-count" role="status">
              {loading ? 'Loading requests…' : result.status === 'success' ? countSummary(result.data) : ''}
            </p>
          </div>
        </div>
        {body}
      </section>
    </div>
  )
}

function newest(fetched: Connection, updated: Connection | undefined): Connection {
  return updated && Date.parse(updated.updatedAt) >= Date.parse(fetched.updatedAt) ? updated : fetched
}

function ConnectionListSkeleton() {
  return (
    <div className="connection-list" aria-hidden="true">
      {Array.from({ length: 3 }, (_, index) => (
        <div key={index} className="connection-item connection-item-skeleton">
          <span className="skeleton skeleton-tag" />
          <span className="skeleton skeleton-title" />
          <span className="skeleton skeleton-line skeleton-line-short" />
        </div>
      ))}
    </div>
  )
}
