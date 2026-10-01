import { useEffect, useRef, useState } from 'react'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { ApiError } from '../../services/apiClient'
import { useAuth } from '../auth/useAuth'
import { useLoginRedirect } from '../auth/useLoginRedirect'
import type { ListingDetail } from '../listings/types'
import { interestApi } from './interestApi'
import { interestErrorOf, type InterestError } from './interestErrors'

/** What the backend reported: a new request, an existing pending one, or an accepted one. */
type Outcome = 'sent' | 'pending' | 'accepted'

const OUTCOMES: Record<Outcome, { label: string; title: string; text: string }> = {
  sent: {
    label: 'Pending',
    title: 'Interest sent',
    text: 'Your request is waiting for the owner’s response. You’ll be connected only if they accept it.',
  },
  pending: {
    label: 'Pending',
    title: 'Interest already sent',
    text: 'You’ve already expressed interest in this listing. Your request is still waiting for the owner’s response.',
  },
  accepted: {
    label: 'Connected',
    title: 'Request accepted',
    text: 'The owner accepted your interest in this listing, so you’re connected.',
  },
}

/** A 200 for a request that is neither pending nor accepted is outside the contract; treat it as final. */
const UNEXPECTED_STATE: InterestError = {
  title: 'Interest can’t be sent',
  message: 'Your earlier request for this listing is closed, so interest can’t be sent again.',
  retryable: false,
}

/**
 * The listing page's primary action. It only reports what the backend answered: a sent
 * request stays pending until the owner accepts it. The current request status is not
 * known in advance (the API offers no lookup for one listing), so the button is offered
 * until the user acts; asking again is safe and returns the existing request.
 */
export function ExpressInterest({ listing }: { listing: ListingDetail }) {
  const { status, account } = useAuth()
  const redirectToLogin = useLoginRedirect()
  const [sending, setSending] = useState(false)
  const sendingRef = useRef(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [error, setError] = useState<InterestError | null>(null)
  const resultRef = useRef<HTMLDivElement>(null)

  const final = outcome !== null || (error !== null && !error.retryable)
  // The button disappears once there is a final answer; keep keyboard focus on that answer.
  useEffect(() => {
    if (final) {
      resultRef.current?.focus()
    }
  }, [final])

  if (status !== 'authenticated' || !account) {
    return (
      <div className="interest">
        <Button block onClick={redirectToLogin}>
          Log in to express interest
        </Button>
        <p className="action-hint">You’ll come straight back here after logging in.</p>
      </div>
    )
  }

  // Both ids come from the server: the verified session and the listing response.
  if (account.id === listing.owner.id) {
    return (
      <div className="interest">
        <p className="action-note">
          <strong>This is your listing.</strong> Other members can express interest in it from this page.
        </p>
      </div>
    )
  }

  async function handleExpressInterest() {
    if (sendingRef.current) {
      return
    }
    sendingRef.current = true
    setSending(true)
    setError(null)
    try {
      const { connection, created } = await interestApi.express(listing.id)
      if (created) {
        setOutcome('sent')
      } else if (connection.status === 'PENDING') {
        setOutcome('pending')
      } else if (connection.status === 'ACCEPTED') {
        setOutcome('accepted')
      } else {
        setError(UNEXPECTED_STATE)
      }
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        // The session ended; the API client has already signed the user out.
        redirectToLogin()
      } else {
        setError(interestErrorOf(caught))
      }
    } finally {
      sendingRef.current = false
      setSending(false)
    }
  }

  if (outcome !== null) {
    const { label, title, text } = OUTCOMES[outcome]
    return (
      <div className="interest">
        <div ref={resultRef} tabIndex={-1} className="interest-outcome" data-outcome={outcome} role="status">
          <p className="interest-outcome-label">
            <StatusIcon accepted={outcome === 'accepted'} />
            {label}
          </p>
          <p className="interest-outcome-title">{title}</p>
          <p className="interest-outcome-text">{text}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="interest">
      {error && (
        <div ref={error.retryable ? undefined : resultRef} tabIndex={-1} className="interest-error">
          <ErrorMessage title={error.title} message={error.message} />
        </div>
      )}
      {(error === null || error.retryable) && (
        <>
          <Button block loading={sending} loadingText="Sending interest…" onClick={handleExpressInterest}>
            Express interest
          </Button>
          <p className="action-hint">The owner decides whether to connect with you.</p>
        </>
      )}
    </div>
  )
}

function StatusIcon({ accepted }: { accepted: boolean }) {
  return (
    <svg className="interest-outcome-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      {accepted ? (
        <path d="m3.5 8.5 3 3 6-7" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
      ) : (
        <>
          <circle cx="8" cy="8" r="5.75" fill="none" stroke="currentColor" strokeWidth="1.5" />
          <path d="M8 5v3.25l2 1.25" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </>
      )}
    </svg>
  )
}
