import { useCallback, useId, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../../app/paths'
import { LoadError } from '../../components/LoadError'
import { Loading } from '../../components/Loading'
import { StatusPanel } from '../../components/StatusPanel'
import { formatDateTime } from '../../lib/dates'
import { parseId } from '../../lib/ids'
import { useAsync } from '../../lib/useAsync'
import { ApiError } from '../../services/apiClient'
import { useAuth } from '../auth/useAuth'
import { initialOf } from '../listings/format'
import { UserLink } from '../profile/UserLink'
import { Composer } from './Composer'
import { messagesApi } from './messagesApi'
import { useMessagesOutlet } from './messagesOutlet'
import { MessageThread } from './MessageThread'
import type { Conversation, Message } from './types'

/** Not a valid id: treated like the backend's 404, without asking it. */
class InvalidId extends Error {}

/** `/messages/:id`. Keyed by id so nothing from one conversation carries over to the next. */
export function ConversationView() {
  const { id: rawId } = useParams()
  return <ConversationScreen key={rawId} id={parseId(rawId)} />
}

function ConversationScreen({ id }: { id: number | null }) {
  const { account } = useAuth()
  const location = useLocation()
  const { refreshConversations } = useMessagesOutlet()
  const headingId = useId()

  const load = useCallback(
    (signal: AbortSignal) => (id === null ? Promise.reject(new InvalidId()) : messagesApi.conversation(id, signal)),
    [id],
  )
  const result = useAsync(load)
  // Read again after sending; a failed refresh just keeps what is shown.
  const [refreshed, setRefreshed] = useState<Conversation | null>(null)
  const [sent, setSent] = useState<Message[]>([])

  const backToList = (
    <Link to={{ pathname: paths.messages, search: location.search }} className="back-link conversation-back">
      <span aria-hidden="true">←</span> All conversations
    </Link>
  )

  if (result.status === 'loading') {
    return (
      <div className="conversation">
        {backToList}
        <Loading label="Loading conversation…" />
      </div>
    )
  }

  if (result.status === 'error') {
    const notFound =
      result.error instanceof InvalidId || (result.error instanceof ApiError && result.error.status === 404)
    return (
      <div className="conversation">
        {backToList}
        {notFound ? (
          <StatusPanel
            code="404"
            title="Conversation not found"
            documentTitle="Conversation not found"
            headingLevel="h2"
            className="conversation-missing"
          >
            This conversation doesn’t exist, or you’re not part of it.
          </StatusPanel>
        ) : (
          <LoadError title="We couldn’t load this conversation" error={result.error} onRetry={result.retry} />
        )}
      </div>
    )
  }

  const conversation = refreshed ?? result.data
  const other = conversation.otherParticipant
  const { listing } = conversation

  function handleSent(message: Message) {
    setSent((current) => [message, ...current])
    // The list's order and previews, and this conversation's details, come from the backend.
    refreshConversations()
    messagesApi.conversation(conversation.id).then(setRefreshed, () => undefined)
  }

  return (
    <section className="conversation" aria-labelledby={headingId}>
      <title>{`${other.displayName} · Messages · Conflux`}</title>
      {backToList}

      <header className="conversation-header">
        <span className="avatar" aria-hidden="true">
          {initialOf(other.displayName)}
        </span>
        <div className="conversation-heading">
          <h2 id={headingId} className="conversation-title">
            {other.displayName}
          </h2>
          <p className="conversation-subtitle">
            <UserLink username={other.username} className="conversation-username">
              @{other.username}
            </UserLink>
            <span aria-hidden="true"> · </span>
            <span>
              About <Link to={paths.listing(listing.slug)}>{listing.title}</Link>
            </span>
          </p>
        </div>
      </header>

      <details className="conversation-details">
        <summary>Conversation details</summary>
        <dl className="deal-facts">
          <div>
            <dt>Listing</dt>
            <dd>{listing.title}</dd>
          </div>
          <div>
            <dt>Started</dt>
            <dd>
              <time dateTime={conversation.createdAt}>{formatDateTime(conversation.createdAt)}</time>
            </dd>
          </div>
          <div>
            <dt>Last message</dt>
            <dd>
              {conversation.lastMessageAt ? (
                <time dateTime={conversation.lastMessageAt}>{formatDateTime(conversation.lastMessageAt)}</time>
              ) : (
                'No messages yet'
              )}
            </dd>
          </div>
          {conversation.lastMessagePreview && (
            <div className="conversation-preview">
              <dt>Latest</dt>
              <dd>{conversation.lastMessagePreview}</dd>
            </div>
          )}
        </dl>
      </details>

      <Composer conversationId={conversation.id} recipientName={other.displayName} onSent={handleSent} />
      <MessageThread
        conversationId={conversation.id}
        accountId={account?.id ?? null}
        recipientName={other.displayName}
        sent={sent}
      />
    </section>
  )
}
