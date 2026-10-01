import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { paths } from '../app/paths'
import { ErrorMessage } from '../components/ErrorMessage'
import { LoadError } from '../components/LoadError'
import { Loading } from '../components/Loading'
import { StatusPanel } from '../components/StatusPanel'
import { ListingForm } from '../features/listings/ListingForm'
import { canEdit, ownerStatusSummary } from '../features/listings/listingManagement'
import { listingsApi } from '../features/listings/listingsApi'
import { parseId } from '../lib/ids'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

class InvalidId extends Error {}

export function EditListingPage() {
  const { id: rawId } = useParams()
  const id = parseId(rawId)
  const navigate = useNavigate()
  const [conflict, setConflict] = useState(false)

  const load = useCallback(
    (signal: AbortSignal) => (id === null ? Promise.reject(new InvalidId()) : listingsApi.myListing(id, signal)),
    [id],
  )
  const result = useAsync(load)
  const back = id === null ? paths.myListings : paths.myListing(id)

  let content
  if (result.status === 'loading') {
    content = (
      <>
        <title>Edit listing · Conflux</title>
        <Loading label="Loading your listing…" />
      </>
    )
  } else if (
    result.status === 'error' &&
    (result.error instanceof InvalidId || (result.error instanceof ApiError && result.error.status === 404))
  ) {
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
  } else if (result.status === 'error') {
    content = (
      <div className="listing-error">
        <title>Edit listing · Conflux</title>
        <h1 className="visually-hidden">Edit listing</h1>
        <LoadError title="We couldn’t load this listing" error={result.error} onRetry={result.retry} />
      </div>
    )
  } else if (!canEdit(result.data.status)) {
    const listing = result.data
    content = (
      <StatusPanel
        code="Not editable"
        title="This listing can’t be edited"
        documentTitle="Listing can’t be edited"
        actions={
          <Link to={paths.myListing(listing.id)} className="button button-primary">
            View the listing
          </Link>
        }
      >
        {conflict && 'Your changes weren’t saved. '}
        {ownerStatusSummary(listing.status)}
      </StatusPanel>
    )
  } else {
    const listing = result.data
    const live = listing.status === 'PUBLISHED'
    content = (
      <section className="listing-editor" aria-labelledby="edit-listing-heading">
        <title>{`Edit ${listing.title} · Conflux`}</title>
        <header className="listing-editor-header">
          <p className="eyebrow">My listings</p>
          <h1 id="edit-listing-heading" className="page-title">
            Edit listing
          </h1>
          <p className="lead">
            {live
              ? 'This listing is live. Saved changes appear on the marketplace right away, and it stays published.'
              : 'This is a private draft. Saving keeps it as a draft until you publish it.'}{' '}
            Its public address, <code>/listings/{listing.slug}</code>, stays the same.
          </p>
        </header>
        {conflict && (
          <ErrorMessage
            title="Your changes weren’t saved"
            message="This listing changed in the meantime. Its latest version is shown below; review it and save again."
          />
        )}
        <ListingForm
          key={listing.updatedAt}
          mode="edit"
          listing={listing}
          onSaved={(saved) => void navigate(paths.myListing(saved.id), { state: { listing: saved, notice: 'saved' } })}
          onCancel={() => void navigate(paths.myListing(listing.id))}
          onConflict={() => {
            setConflict(true)
            result.retry()
          }}
        />
      </section>
    )
  }

  return (
    <div className="listing-editor-page">
      <nav aria-label="Breadcrumb" className="back-nav">
        <Link to={back} className="back-link">
          <span aria-hidden="true">←</span> Back to the listing
        </Link>
      </nav>
      {content}
    </div>
  )
}
