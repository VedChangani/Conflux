import { useCallback, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../app/paths'
import { LoadError } from '../components/LoadError'
import { Loading } from '../components/Loading'
import { StatusPanel } from '../components/StatusPanel'
import { listingsApi } from '../features/listings/listingsApi'
import { OwnerListingActions } from '../features/listings/OwnerListingActions'
import { OwnerListingView } from '../features/listings/OwnerListingView'
import type { ListingDetail } from '../features/listings/types'
import { parseId } from '../lib/ids'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

class InvalidId extends Error {}

type Event = 'created' | 'saved' | 'publish' | 'archive'

interface ListingState {
  listing?: ListingDetail
  notice?: Event
}

function readState(state: unknown, id: number | null): ListingState {
  const { listing, notice } = (state ?? {}) as ListingState
  return listing && listing.id === id ? { listing, notice } : {}
}

export function MyListingPage() {
  const { id: rawId } = useParams()
  const id = parseId(rawId)
  const location = useLocation()
  const passed = readState(location.state, id)

  const load = useCallback(
    (signal: AbortSignal) => (id === null ? Promise.reject(new InvalidId()) : listingsApi.myListing(id, signal)),
    [id],
  )
  const result = useAsync(load)
  const [updated, setUpdated] = useState<{ listing: ListingDetail; event: Event } | null>(null)

  const current = updated !== null && updated.listing.id === id ? updated : null
  const listing = current?.listing ?? result.data ?? passed.listing
  const event = current?.event ?? passed.notice

  const notFound =
    result.status === 'error' &&
    (result.error instanceof InvalidId || (result.error instanceof ApiError && result.error.status === 404))

  const { retry } = result
  const handleStale = useCallback(() => {
    setUpdated(null)
    retry()
  }, [retry])

  let content
  if (notFound) {
    content = (
      <StatusPanel
        code="404"
        title="Listing not found"
        documentTitle="Listing not found"
        actions={
          <Link to={paths.myListings} className="button button-primary">
            Go to my listings
          </Link>
        }
      >
        This listing doesn’t exist, or it isn’t one of yours.
      </StatusPanel>
    )
  } else if (listing) {
    content = (
      <OwnerListingView
        listing={listing}
        notice={event && <ListingNotice event={event} listing={listing} />}
        actions={
          <OwnerListingActions
            listing={listing}
            onUpdated={(next, action) => setUpdated({ listing: next, event: action })}
            onStale={handleStale}
          />
        }
      />
    )
  } else if (result.status === 'loading') {
    content = (
      <div className="listing-detail-loading">
        <title>Loading listing… · Conflux</title>
        <Loading label="Loading your listing…" />
        <div className="listing-detail-skeleton" aria-hidden="true">
          <span className="skeleton skeleton-tag" />
          <span className="skeleton skeleton-heading" />
          <span className="skeleton skeleton-line" />
        </div>
      </div>
    )
  } else {
    content = (
      <div className="listing-error">
        <title>Listing unavailable · Conflux</title>
        <h1 className="visually-hidden">Listing unavailable</h1>
        <LoadError title="We couldn’t load this listing" error={result.error} onRetry={retry} />
      </div>
    )
  }

  return (
    <div className="listing-page owner-listing-page">
      <nav aria-label="Breadcrumb" className="back-nav">
        <Link to={paths.myListings} className="back-link">
          <span aria-hidden="true">←</span> Back to my listings
        </Link>
      </nav>
      {content}
    </div>
  )
}

function ListingNotice({ event, listing }: { event: Event; listing: ListingDetail }) {
  const live = listing.status === 'PUBLISHED'
  let message
  switch (event) {
    case 'created':
      message = (
        <>
          <strong>Draft created.</strong> Only you can see it. Publish it when it’s ready, or keep editing.
        </>
      )
      break
    case 'saved':
      message = (
        <>
          <strong>Changes saved.</strong>{' '}
          {live ? 'They’re live on the marketplace.' : listing.status === 'DRAFT' ? 'It’s still a private draft.' : null}
        </>
      )
      break
    case 'publish':
      if (!live) {
        return null
      }
      message = (
        <>
          <strong>Published.</strong> Your listing is live on the marketplace.{' '}
          <Link to={paths.listing(listing.slug)}>View it as others see it</Link>
        </>
      )
      break
    case 'archive':
      if (listing.status !== 'ARCHIVED') {
        return null
      }
      message = (
        <>
          <strong>Listing archived.</strong> It’s no longer on the marketplace.
        </>
      )
  }
  return (
    <div className="notice notice-success owner-listing-notice" role="status">
      <p>{message}</p>
    </div>
  )
}
