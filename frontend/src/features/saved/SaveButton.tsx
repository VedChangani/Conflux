import { useEffect, useRef, useState } from 'react'
import { Button } from '../../components/Button'
import { ApiError } from '../../services/apiClient'
import { useAuth } from '../auth/useAuth'
import { useLoginRedirect } from '../auth/useLoginRedirect'
import { saveErrorMessage } from './saveErrors'
import { useSavedListings } from './useSavedListings'

interface SaveButtonProps {
  listingId: number
  listingTitle?: string
  knownSaved?: boolean
  onSavedChange?: (saved: boolean) => void
  block?: boolean
  className?: string
}

export function SaveButton({ listingId, listingTitle, knownSaved, onSavedChange, block, className }: SaveButtonProps) {
  const { status } = useAuth()
  const { isSaved, checking, ensureLoaded, setSaved } = useSavedListings()
  const redirectToLogin = useLoginRedirect()
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')

  const authenticated = status === 'authenticated'
  useEffect(() => {
    if (authenticated && knownSaved === undefined) {
      ensureLoaded()
    }
  }, [authenticated, knownSaved, ensureLoaded])

  const confirmed = authenticated ? isSaved(listingId) : undefined
  const saved = authenticated && (confirmed ?? knownSaved ?? false)
  const unknown = authenticated && confirmed === undefined && knownSaved === undefined && checking

  async function handleClick() {
    if (!authenticated) {
      redirectToLogin()
      return
    }
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    setBusy(true)
    setError(null)
    setAnnouncement('')
    const next = !saved
    try {
      await setSaved(listingId, next)
      setAnnouncement(next ? 'Saved to your list.' : 'Removed from your saved listings.')
      onSavedChange?.(next)
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        redirectToLogin()
      } else {
        setError(saveErrorMessage(caught, next))
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  const label = busy ? (saved ? 'Removing…' : 'Saving…') : saved ? 'Saved' : 'Save'
  const classes = ['save-button', 'button-small', saved && 'is-saved', className].filter(Boolean).join(' ')

  return (
    <div className={block ? 'save-control save-control-block' : 'save-control'}>
      <Button
        variant="secondary"
        className={classes}
        block={block}
        aria-label={listingTitle ? `${label} ${listingTitle}` : undefined}
        aria-pressed={authenticated ? saved : undefined}
        loading={busy}
        disabled={unknown}
        onClick={handleClick}
      >
        <BookmarkIcon filled={saved} />
        <span>{label}</span>
      </Button>
      <span className="visually-hidden" aria-live="polite">
        {announcement}
      </span>
      {error && (
        <p className="save-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

function BookmarkIcon({ filled }: { filled: boolean }) {
  return (
    <svg className="save-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path
        d="M4 2.75h8v10.5l-4-2.75-4 2.75z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
    </svg>
  )
}
