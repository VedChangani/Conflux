import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { Button } from '../../components/Button'
import { ApiError } from '../../services/apiClient'
import { canArchive, canEdit, canPublish, manageErrorMessage, type ManageAction } from './listingManagement'
import { listingsApi } from './listingsApi'
import type { ListingDetail } from './types'

type Action = Extract<ManageAction, 'publish' | 'archive'>

interface OwnerListingActionsProps {
  listing: ListingDetail
  onUpdated: (listing: ListingDetail, action: Action) => void
  onStale: () => void
}

export function OwnerListingActions({ listing, onUpdated, onStale }: OwnerListingActionsProps) {
  const [running, setRunning] = useState<Action | null>(null)
  const runningRef = useRef(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const confirmRef = useRef<HTMLButtonElement>(null)
  const archiveRef = useRef<HTMLButtonElement>(null)
  const restoreFocus = useRef(false)

  useEffect(() => {
    if (confirming) {
      confirmRef.current?.focus()
    } else if (restoreFocus.current) {
      restoreFocus.current = false
      archiveRef.current?.focus()
    }
  }, [confirming])

  async function reread(action: Action) {
    try {
      onUpdated(await listingsApi.myListing(listing.id), action)
    } catch {
      onStale()
    }
  }

  async function run(action: Action) {
    if (runningRef.current) {
      return
    }
    runningRef.current = true
    setRunning(action)
    setError(null)
    try {
      if (action === 'publish') {
        onUpdated(await listingsApi.publish(listing.id), action)
      } else {
        await listingsApi.archive(listing.id)
        await reread(action)
      }
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        return
      }
      setError(manageErrorMessage(caught, action))
      if (caught instanceof ApiError && caught.status === 409) {
        await reread(action)
      }
    } finally {
      runningRef.current = false
      setRunning(null)
      setConfirming(false)
      containerRef.current?.focus()
    }
  }

  function cancel() {
    restoreFocus.current = true
    setConfirming(false)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape' && running === null) {
      cancel()
    }
  }

  const busy = running !== null
  const { status } = listing

  let controls = null
  if (confirming && canArchive(status)) {
    controls = (
      <div className="action-confirm" role="group" aria-label="Confirm" onKeyDown={handleKeyDown}>
        <p className="action-confirm-question">
          Archive this listing? It comes off the marketplace and can’t be edited, published or restored afterwards.
        </p>
        <div className="button-row">
          <Button
            ref={confirmRef}
            className="button-small button-danger"
            loading={running === 'archive'}
            loadingText="Archiving…"
            onClick={() => void run('archive')}
          >
            Yes, archive
          </Button>
          <Button variant="secondary" className="button-small" disabled={busy} onClick={cancel}>
            Cancel
          </Button>
        </div>
      </div>
    )
  } else if (canEdit(status)) {
    controls = (
      <div className="button-row action-buttons">
        {canPublish(status) && (
          <Button className="button-small" loading={running === 'publish'} loadingText="Publishing…" onClick={() => void run('publish')}>
            Publish
          </Button>
        )}
        <Link
          to={paths.editListing(listing.id)}
          className="button button-secondary button-small"
          aria-disabled={busy || undefined}
          onClick={(event) => busy && event.preventDefault()}
        >
          Edit
        </Link>
        {canArchive(status) && (
          <Button ref={archiveRef} variant="secondary" className="button-small" disabled={busy} onClick={() => setConfirming(true)}>
            Archive
          </Button>
        )}
      </div>
    )
  }

  return (
    <div ref={containerRef} tabIndex={-1} className="connection-actions owner-listing-actions">
      {controls}
      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
