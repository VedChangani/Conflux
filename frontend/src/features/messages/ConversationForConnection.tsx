import { useCallback } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { paths } from '../../app/paths'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Loading } from '../../components/Loading'
import { loadErrorMessage } from '../../lib/errors'
import { parseId } from '../../lib/ids'
import { useAsync } from '../../lib/useAsync'
import { messagesApi } from './messagesApi'

/**
 * `/messages/connection/:connectionId`: opens the conversation of an accepted connection.
 * The connection's status is never assumed: only a conversation the backend lists is opened.
 */
export function ConversationForConnection() {
  const { connectionId: raw } = useParams()
  const connectionId = parseId(raw)

  const load = useCallback(
    (signal: AbortSignal) =>
      connectionId === null ? Promise.resolve(null) : messagesApi.conversationIdForConnection(connectionId, signal),
    [connectionId],
  )
  const result = useAsync(load)

  if (result.status === 'loading') {
    return (
      <div className="conversation">
        <Loading label="Opening conversation…" />
      </div>
    )
  }
  if (result.status === 'error') {
    return (
      <div className="conversation">
        <ErrorMessage title="We couldn’t open this conversation" message={loadErrorMessage(result.error)}>
          <div className="button-row">
            <Button variant="secondary" onClick={result.retry}>
              Try again
            </Button>
          </div>
        </ErrorMessage>
      </div>
    )
  }
  if (result.data !== null) {
    return <Navigate to={paths.conversation(result.data)} replace />
  }
  return (
    <div className="conversation">
      <div className="not-found-panel conversation-missing">
        <h2 className="results-heading">No conversation found</h2>
        <p>There’s no conversation for this connection. Conversations open once a request has been accepted.</p>
        <div className="button-row">
          <Link to={paths.messages} className="button button-secondary">
            All conversations
          </Link>
        </div>
      </div>
    </div>
  )
}
