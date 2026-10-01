import { useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Button } from '../../components/Button'
import { ApiError } from '../../services/apiClient'
import { messagesApi } from './messagesApi'
import { sendErrorOf, type SendError } from './sendErrors'
import { MESSAGE_MAX_LENGTH, type Message } from './types'

const COUNTER_FROM = MESSAGE_MAX_LENGTH - 500
const numberFormat = new Intl.NumberFormat('en-US')

function enterAddsLine(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches
}

interface ComposerProps {
  conversationId: number
  recipientName: string
  onSent: (message: Message) => void
}

export function Composer({ conversationId, recipientName, onSent }: ComposerProps) {
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const sendingRef = useRef(false)
  const [error, setError] = useState<SendError | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const id = useId()
  const hintId = `${id}-hint`
  const counterId = `${id}-counter`
  const errorId = `${id}-error`

  const length = draft.trim().length
  const over = length - MESSAGE_MAX_LENGTH

  async function send() {
    if (sendingRef.current) {
      return
    }
    const content = draft.trim()
    if (content === '') {
      setError({ message: 'Write a message before sending.', retryable: false, closed: false })
      textareaRef.current?.focus()
      return
    }
    if (content.length > MESSAGE_MAX_LENGTH) {
      setError({
        message: `Messages can be at most ${numberFormat.format(MESSAGE_MAX_LENGTH)} characters. Shorten yours by ${numberFormat.format(content.length - MESSAGE_MAX_LENGTH)}.`,
        retryable: false,
        closed: false,
      })
      textareaRef.current?.focus()
      return
    }
    sendingRef.current = true
    setSending(true)
    setError(null)
    setAnnouncement('')
    try {
      const message = await messagesApi.send(conversationId, content)
      setDraft('')
      setAnnouncement('Message sent.')
      onSent(message)
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        return
      }
      setError(sendErrorOf(caught))
    } finally {
      sendingRef.current = false
      setSending(false)
    }
    textareaRef.current?.focus()
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void send()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && !enterAddsLine()) {
      event.preventDefault()
      void send()
    }
  }

  if (error?.closed) {
    return (
      <div className="composer-closed" role="alert">
        <strong>You can’t send messages here</strong>
        <p>{error.message}</p>
      </div>
    )
  }

  const describedBy = [error ? errorId : null, length >= COUNTER_FROM ? counterId : null, hintId].filter(Boolean).join(' ')

  return (
    <form className="composer" onSubmit={handleSubmit} noValidate aria-busy={sending}>
      <label className="visually-hidden" htmlFor={id}>
        Message to {recipientName}
      </label>
      <textarea
        ref={textareaRef}
        id={id}
        className="field-input composer-input"
        name="content"
        rows={3}
        placeholder={`Write to ${recipientName}…`}
        value={draft}
        readOnly={sending}
        aria-invalid={error !== null && !error.retryable ? true : undefined}
        aria-describedby={describedBy}
        onChange={(event) => {
          setDraft(event.target.value)
          if (error && !error.retryable) {
            setError(null)
          }
        }}
        onKeyDown={handleKeyDown}
      />
      {error && (
        <div id={errorId} className="composer-error" role="alert">
          <p>{error.message}</p>
          {error.retryable && (
            <Button variant="secondary" className="button-small" onClick={() => void send()} disabled={sending}>
              Try again
            </Button>
          )}
        </div>
      )}
      <div className="composer-footer">
        <p id={hintId} className="composer-hint">
          {enterAddsLine() ? 'Use Send to send your message' : 'Enter to send · Shift+Enter for a new line'}
        </p>
        {length >= COUNTER_FROM && (
          <p id={counterId} className={over > 0 ? 'composer-counter is-over' : 'composer-counter'}>
            {over > 0
              ? `${numberFormat.format(over)} characters over the limit`
              : `${numberFormat.format(MESSAGE_MAX_LENGTH - length)} characters left`}
          </p>
        )}
        <Button type="submit" className="composer-send" loading={sending} loadingText="Sending…">
          Send
        </Button>
      </div>
      <span className="visually-hidden" aria-live="polite">
        {announcement}
      </span>
    </form>
  )
}
