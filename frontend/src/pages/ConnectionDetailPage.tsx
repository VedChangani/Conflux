import { useCallback, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../app/paths'
import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'
import { Loading } from '../components/Loading'
import { useAuth } from '../features/auth/useAuth'
import { loadErrorMessage } from '../features/connections/connectionErrors'
import { ConnectionDetailView } from '../features/connections/ConnectionDetailView'
import { connectionsApi } from '../features/connections/connectionsApi'
import { roleOf } from '../features/connections/perspective'
import type { Connection } from '../features/connections/types'
import { parseId } from '../lib/ids'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

/** Not found, or not the user's: the backend answers both with 404, and so does this page. */
class InvalidId extends Error {}

/**
 * Where "back" goes: the list the user came from (`state.from`, a `/connections/…` path),
 * or the list this request belongs in for them.
 */
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

/** `/connections/:id`. Rendered behind {@link ProtectedRoute}. */
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
  // The connection as an action on this page left it.
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
          <Loading label="Loading request…" />
          <div className="listing-detail-skeleton" aria-hidden="true">
            <span className="skeleton skeleton-tag" />
            <span className="skeleton skeleton-heading" />
            <span className="skeleton skeleton-line" />
          </div>
        </div>
      ) : notFound ? (
        <section className="not-found-panel">
          <title>Request not found · Conflux</title>
          <p className="eyebrow">404</p>
          <h1 className="page-title">Request not found</h1>
          <p className="lead">This request doesn’t exist, or it isn’t one of yours.</p>
          <div className="button-row">
            <Link to={paths.connections} className="button button-primary">
              Go to connections
            </Link>
          </div>
        </section>
      ) : (
        <div className="listing-error">
          <h1 className="visually-hidden">Request unavailable</h1>
          <ErrorMessage title="We couldn’t load this request" message={loadErrorMessage(result.error)}>
            <div className="button-row">
              <Button variant="secondary" onClick={result.retry}>
                Try again
              </Button>
            </div>
          </ErrorMessage>
        </div>
      )}
    </div>
  )
}
