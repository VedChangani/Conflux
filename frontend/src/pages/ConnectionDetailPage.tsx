import { useCallback, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../app/paths'
import { LoadError } from '../components/LoadError'
import { Loading } from '../components/Loading'
import { StatusPanel } from '../components/StatusPanel'
import { useAuth } from '../features/auth/useAuth'
import { ConnectionDetailView } from '../features/connections/ConnectionDetailView'
import { connectionsApi } from '../features/connections/connectionsApi'
import { roleOf } from '../features/connections/perspective'
import type { Connection } from '../features/connections/types'
import { parseId } from '../lib/ids'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

class InvalidId extends Error {}

function backTarget(state: unknown, connection: Connection | undefined, accountId: number | null) {
  const from = (state as { from?: unknown } | null)?.from
  if (typeof from === 'string' && from.startsWith(`${paths.connections}/`)) {
    return from
  }
  if (connection && roleOf(connection, accountId) === 'requester') {
    return paths.connectionsSent
  }
  return paths.connectionsReceived
}

export function ConnectionDetailPage() {
  const { id: rawId } = useParams()
  const id = parseId(rawId)
  const location = useLocation()
  const { account } = useAuth()

  const load = useCallback(
    (signal: AbortSignal) => (id === null ? Promise.reject(new InvalidId()) : connectionsApi.detail(id, signal)),
    [id],
  )
  const result = useAsync(load)
  const [updated, setUpdated] = useState<Connection | null>(null)
  const connection = updated !== null && updated.id === id ? updated : result.data
  const back = backTarget(location.state, connection, account?.id ?? null)

  const notFound =
    result.status === 'error' &&
    (result.error instanceof InvalidId || (result.error instanceof ApiError && result.error.status === 404))

  return (
    <div className="connection-page">
      <nav aria-label="Breadcrumb" className="back-nav">
        <Link to={back} className="back-link">
          <span aria-hidden="true">←</span> Back to connections
        </Link>
      </nav>

      {connection ? (
        <ConnectionDetailView connection={connection} onUpdated={setUpdated} />
      ) : result.status === 'loading' ? (
        <div className="listing-detail-loading">
          <title>Loading request… · Conflux</title>
          <Loading label="Loading request…" />
          <div className="listing-detail-skeleton" aria-hidden="true">
            <span className="skeleton skeleton-tag" />
            <span className="skeleton skeleton-heading" />
            <span className="skeleton skeleton-line" />
          </div>
        </div>
      ) : notFound ? (
        <StatusPanel
          code="404"
          title="Request not found"
          documentTitle="Request not found"
          actions={
            <Link to={paths.connections} className="button button-primary">
              Go to connections
            </Link>
          }
        >
          This request doesn’t exist, or it isn’t one of yours.
        </StatusPanel>
      ) : (
        <div className="listing-error">
          <title>Request unavailable · Conflux</title>
          <h1 className="visually-hidden">Request unavailable</h1>
          <LoadError title="We couldn’t load this request" error={result.error} onRetry={result.retry} />
        </div>
      )}
    </div>
  )
}
