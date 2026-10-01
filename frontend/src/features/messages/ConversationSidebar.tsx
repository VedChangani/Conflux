import { useId } from 'react'
import { Link, useLocation } from 'react-router'
import { paths } from '../../app/paths'
import { LoadError } from '../../components/LoadError'
import { formatActivity } from '../../lib/dates'
import type { AsyncState } from '../../lib/useAsync'
import type { PageResponse } from '../../types/api'
import { initialOf } from '../listings/format'
import { Pagination } from '../listings/Pagination'
import type { Conversation } from './types'

const numberFormat = new Intl.NumberFormat('en-US')

interface ConversationSidebarProps {
  result: AsyncState<PageResponse<Conversation>> & { retry: () => void }
  /** 1-based. */
  page: number
  hrefFor: (page: number) => string
  /** The conversation open next to the list, if any. */
  activeId: number | null
}

/**
 * The user's conversations exactly in the backend's order (most recent activity first):
 * the other person, the listing, the latest message preview and when it happened.
 */
export function ConversationSidebar({ result, page, hrefFor, activeId }: ConversationSidebarProps) {
  const data = result.data ?? result.previousData
  const loading = result.status === 'loading'

  let body
  if (result.status === 'error') {
    body = <LoadError title="We couldn’t load your conversations" error={result.error} onRetry={result.retry} />
  } else if (data === undefined) {
    body = (
      <div className="conversation-items" aria-hidden="true">
        {Array.from({ length: 3 }, (_, index) => (
          <span key={index} className="skeleton conversation-skeleton" />
        ))}
      </div>
    )
  } else if (data.totalElements === 0) {
    body = (
      <div className="thread-empty">
        <p className="thread-empty-title">No conversations yet</p>
        <p>A conversation opens when a connection request is accepted.</p>
        <p>
          <Link to={paths.connections}>Go to connections</Link>
        </p>
      </div>
    )
  } else if (data.content.length === 0) {
    const lastPage = Math.max(data.totalPages, 1)
    body = (
      <div className="thread-empty">
        <p className="thread-empty-title">There is no page {page}</p>
        <p>
          <Link to={hrefFor(lastPage)}>Go to page {lastPage}</Link>
        </p>
      </div>
    )
  } else {
    body = (
      <>
        <ul className={loading ? 'conversation-items is-stale' : 'conversation-items'}>
          {data.content.map((conversation) => (
            <ConversationLink key={conversation.id} conversation={conversation} active={conversation.id === activeId} />
          ))}
        </ul>
        {data.totalPages > 1 && <Pagination page={page} totalPages={data.totalPages} hrefFor={hrefFor} />}
      </>
    )
  }

  return (
    <section className="messages-sidebar" aria-labelledby="conversations-heading" aria-busy={loading}>
      <div className="messages-sidebar-head">
        <h2 id="conversations-heading" className="messages-sidebar-title">
          Conversations
        </h2>
        <p className="results-count" role="status">
          {data === undefined
            ? result.status === 'error'
              ? ''
              : 'Loading conversations…'
            : `${numberFormat.format(data.totalElements)} ${data.totalElements === 1 ? 'conversation' : 'conversations'}`}
        </p>
      </div>
      {body}
    </section>
  )
}

function ConversationLink({ conversation, active }: { conversation: Conversation; active: boolean }) {
  const location = useLocation()
  const previewId = useId()
  const other = conversation.otherParticipant
  const activityAt = conversation.lastMessageAt ?? conversation.createdAt

  return (
    <li>
      <Link
        // Keeps the list's page, so going back returns to the same place.
        to={{ pathname: paths.conversation(conversation.id), search: location.search }}
        className="conversation-link"
        aria-current={active ? 'page' : undefined}
        aria-label={`${other.displayName}, ${conversation.listing.title}`}
        aria-describedby={previewId}
      >
        <span className="avatar" aria-hidden="true">
          {initialOf(other.displayName)}
        </span>
        <span className="conversation-link-body">
          <span className="conversation-link-top">
            <span className="conversation-link-name">{other.displayName}</span>
            <time className="conversation-link-time" dateTime={activityAt}>
              {formatActivity(activityAt)}
            </time>
          </span>
          <span className="conversation-link-listing">{conversation.listing.title}</span>
          <span id={previewId} className="conversation-link-preview" data-empty={!conversation.lastMessagePreview}>
            {conversation.lastMessagePreview ?? 'No messages yet'}
          </span>
        </span>
      </Link>
    </li>
  )
}
