import { useCallback } from 'react'
import { Outlet, useLocation, useParams, useSearchParams } from 'react-router'
import { ConversationSidebar } from '../features/messages/ConversationSidebar'
import { CONVERSATIONS_PAGE_SIZE, messagesApi } from '../features/messages/messagesApi'
import type { MessagesOutletContext } from '../features/messages/messagesOutlet'
import { parseId } from '../lib/ids'
import { readPageParam } from '../lib/pageParam'
import { useAsync } from '../lib/useAsync'

export function MessagesPage() {
  const params = useParams()
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const page = readPageParam(searchParams)
  const showsConversation = params.id !== undefined || params.connectionId !== undefined

  const load = useCallback(
    (signal: AbortSignal) => messagesApi.conversations(page - 1, CONVERSATIONS_PAGE_SIZE, signal),
    [page],
  )
  const result = useAsync(load)

  const hrefFor = (target: number) => {
    const next = new URLSearchParams(searchParams)
    if (target > 1) {
      next.set('page', String(target))
    } else {
      next.delete('page')
    }
    const search = next.toString()
    return search ? `${location.pathname}?${search}` : location.pathname
  }

  const outletContext: MessagesOutletContext = { refreshConversations: result.retry }

  return (
    <div className="messages-page">
      <title>Messages · Conflux</title>
      <header className="messages-page-header">
        <p className="eyebrow">Your account</p>
        <h1 className="messages-page-title">Messages</h1>
      </header>
      <div className="messages-layout" data-pane={showsConversation ? 'conversation' : 'list'}>
        <ConversationSidebar result={result} page={page} hrefFor={hrefFor} activeId={parseId(params.id)} />
        <div className="messages-main">
          <Outlet context={outletContext} />
        </div>
      </div>
    </div>
  )
}

export function MessagesIndex() {
  return (
    <div className="messages-placeholder">
      <span className="empty-state-mark" aria-hidden="true" />
      <p className="thread-empty-title">Select a conversation</p>
      <p>Choose a conversation to read it and reply.</p>
    </div>
  )
}
