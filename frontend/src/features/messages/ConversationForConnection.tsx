import { useCallback } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { paths } from '../../app/paths'
import { LoadError } from '../../components/LoadError'
import { Loading } from '../../components/Loading'
import { StatusPanel } from '../../components/StatusPanel'
import { parseId } from '../../lib/ids'
import { useAsync } from '../../lib/useAsync'
import { messagesApi } from './messagesApi'

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
        <LoadError title="We couldn’t open this conversation" error={result.error} onRetry={result.retry} />
      </div>
    )
  }
  if (result.data !== null) {
    return <Navigate to={paths.conversation(result.data)} replace />
  }
  return (
    <div className="conversation">
      <StatusPanel
        code="Not available"
        title="No conversation found"
        documentTitle="No conversation found"
        headingLevel="h2"
        className="conversation-missing"
        actions={
          <Link to={paths.messages} className="button button-secondary">
            All conversations
          </Link>
        }
      >
        There’s no conversation for this connection. Conversations open once a request has been accepted.
      </StatusPanel>
    </div>
  )
}
