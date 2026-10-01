import { useCallback, useRef, useState } from 'react'
import { Button } from '../../components/Button'
import { LoadError } from '../../components/LoadError'
import { useAsync } from '../../lib/useAsync'
import { MessageItem } from './MessageItem'
import { MESSAGES_PAGE_SIZE, messagesApi } from './messagesApi'
import type { Message } from './types'

interface MessageThreadProps {
  conversationId: number
  /** The signed-in account, confirmed by `/auth/me`; decides which messages are the user's. */
  accountId: number | null
  recipientName: string
  /** Messages sent from this page, newest first, as the backend returned them. */
  sent: readonly Message[]
}

/** Keeps the first occurrence of each message: pages shift as new messages arrive. */
function uniqueById(messages: readonly Message[]): Message[] {
  const seen = new Set<number>()
  return messages.filter((message) => {
    if (seen.has(message.id)) {
      return false
    }
    seen.add(message.id)
    return true
  })
}

/**
 * The conversation's messages in the backend's order, newest first. Messages just sent are
 * shown on top straight away; older pages are added at the end on request.
 */
export function MessageThread({ conversationId, accountId, recipientName, sent }: MessageThreadProps) {
  const loadNewest = useCallback(
    (signal: AbortSignal) => messagesApi.messages(conversationId, 0, MESSAGES_PAGE_SIZE, signal),
    [conversationId],
  )
  const newest = useAsync(loadNewest)
  const [older, setOlder] = useState<{ messages: Message[]; nextPage: number; last: boolean | null }>({
    messages: [],
    nextPage: 1,
    last: null,
  })
  const [olderStatus, setOlderStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const loadingOlder = useRef(false)

  async function loadOlder() {
    if (loadingOlder.current) {
      return
    }
    loadingOlder.current = true
    setOlderStatus('loading')
    try {
      const page = await messagesApi.messages(conversationId, older.nextPage, MESSAGES_PAGE_SIZE)
      setOlder((current) => ({
        messages: [...current.messages, ...page.content],
        nextPage: current.nextPage + 1,
        last: page.last || page.content.length === 0,
      }))
      setOlderStatus('idle')
    } catch {
      setOlderStatus('error')
    } finally {
      loadingOlder.current = false
    }
  }

  if (newest.status === 'loading' && newest.previousData === undefined) {
    return (
      <div className="message-thread">
        <p className="loading" role="status">
          Loading messages…
        </p>
        <div className="message-list" aria-hidden="true">
          <span className="skeleton message-skeleton" />
          <span className="skeleton message-skeleton message-skeleton-own" />
        </div>
      </div>
    )
  }

  if (newest.status === 'error') {
    return (
      <div className="message-thread">
        <LoadError title="We couldn’t load the messages" error={newest.error} onRetry={newest.retry} />
      </div>
    )
  }

  const firstPage = newest.data ?? newest.previousData
  const messages = uniqueById([...sent, ...(firstPage?.content ?? []), ...older.messages])
  const last = older.last ?? firstPage?.last ?? true

  if (messages.length === 0) {
    return (
      <div className="message-thread">
        <div className="thread-empty">
          <p className="thread-empty-title">No messages yet</p>
          <p>Start the conversation with {recipientName}: introduce yourself and what interests you.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="message-thread">
      <ol className="message-list" aria-label="Messages, newest first">
        {messages.map((message) => (
          <MessageItem key={message.id} message={message} own={accountId !== null && message.sender.id === accountId} />
        ))}
      </ol>
      {olderStatus === 'error' && (
        <p className="action-error" role="alert">
          We couldn’t load older messages. Please try again.
        </p>
      )}
      {!last && (
        <div className="thread-more">
          <Button
            variant="secondary"
            className="button-small"
            loading={olderStatus === 'loading'}
            loadingText="Loading older messages…"
            onClick={() => void loadOlder()}
          >
            {olderStatus === 'error' ? 'Try loading older messages again' : 'Load older messages'}
          </Button>
        </div>
      )}
    </div>
  )
}
