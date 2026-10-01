import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { Button } from '../../components/Button'
import type { ConnectionAction } from './connectionErrors'
import { availableActions, type ConnectionRole } from './perspective'
import type { Connection } from './types'
import { useConnectionAction } from './useConnectionAction'

type ConfirmableAction = Exclude<ConnectionAction, 'accept'>

const CONFIRM: Record<ConfirmableAction, { question: string; confirm: string; running: string }> = {
  reject: {
    question: 'Reject this request? This can’t be undone, and they can’t send interest in this listing again.',
    confirm: 'Yes, reject',
    running: 'Rejecting…',
  },
  withdraw: {
    question: 'Withdraw this request? This can’t be undone, and you can’t express interest in this listing again.',
    confirm: 'Yes, withdraw',
    running: 'Withdrawing…',
  },
}

interface ConnectionActionsProps {
  connection: Connection
  role: ConnectionRole
  onUpdated: (connection: Connection) => void
  labelFor?: (action: ConnectionAction) => string
  compact?: boolean
}

export function ConnectionActions({ connection, role, onUpdated, labelFor, compact = false }: ConnectionActionsProps) {
  const { run, running, error, announcement } = useConnectionAction(connection, onUpdated)
  const [confirming, setConfirming] = useState<ConfirmableAction | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const confirmRef = useRef<HTMLButtonElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const restoreFocus = useRef(false)

  useEffect(() => {
    if (confirming) {
      confirmRef.current?.focus()
    } else if (restoreFocus.current) {
      restoreFocus.current = false
      triggerRef.current?.focus()
    }
  }, [confirming])

  const actions = availableActions(connection, role)
  const blocked = error !== null && !error.retryable
  const busy = running !== null

  async function perform(action: ConnectionAction) {
    await run(action)
    setConfirming(null)
    containerRef.current?.focus()
  }

  function cancel() {
    restoreFocus.current = true
    setConfirming(null)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape' && confirming && !busy) {
      cancel()
    }
  }

  const label = (action: ConnectionAction) => (running === action ? undefined : labelFor?.(action))

  let controls = null
  if (confirming && !blocked) {
    const copy = CONFIRM[confirming]
    controls = (
      <div className="action-confirm" role="group" aria-label="Confirm" onKeyDown={handleKeyDown}>
        <p className="action-confirm-question">{copy.question}</p>
        <div className="button-row">
          <Button
            ref={confirmRef}
            className="button-small button-danger"
            loading={running === confirming}
            loadingText={copy.running}
            onClick={() => void perform(confirming)}
          >
            {copy.confirm}
          </Button>
          <Button variant="secondary" className="button-small" disabled={busy} onClick={cancel}>
            Cancel
          </Button>
        </div>
      </div>
    )
  } else if (actions.decide && !blocked) {
    controls = (
      <div className="button-row action-buttons">
        <Button
          className="button-small"
          aria-label={label('accept')}
          loading={running === 'accept'}
          loadingText="Accepting…"
          disabled={busy}
          onClick={() => void perform('accept')}
        >
          Accept
        </Button>
        <Button
          ref={triggerRef}
          variant="secondary"
          className="button-small"
          aria-label={label('reject')}
          disabled={busy}
          onClick={() => setConfirming('reject')}
        >
          Reject
        </Button>
      </div>
    )
  } else if (actions.withdraw && !blocked) {
    controls = (
      <div className="button-row action-buttons">
        <Button
          ref={triggerRef}
          variant="secondary"
          className="button-small"
          aria-label={label('withdraw')}
          disabled={busy}
          onClick={() => setConfirming('withdraw')}
        >
          Withdraw request
        </Button>
      </div>
    )
  } else if (connection.status === 'ACCEPTED' && role !== null) {
    const other = role === 'owner' ? connection.requester : connection.owner
    controls = (
      <Link
        to={paths.conversationForConnection(connection.id)}
        className="button button-secondary button-small"
        aria-label={compact ? `Open conversation with ${other.displayName} about ${connection.listing.title}` : undefined}
      >
        Open conversation
      </Link>
    )
  } else if (connection.status !== 'PENDING') {
    controls = (
      <p className="action-final">{compact ? 'No further action' : 'No further action is available.'}</p>
    )
  }

  return (
    <div ref={containerRef} tabIndex={-1} className="connection-actions">
      {controls}
      {error && (
        <p className="action-error" role="alert">
          {error.message}
        </p>
      )}
      <span className="visually-hidden" aria-live="polite">
        {announcement}
      </span>
    </div>
  )
}
