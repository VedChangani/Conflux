import { useCallback } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../app/paths'
import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'
import { Loading } from '../components/Loading'
import { useAuth } from '../features/auth/useAuth'
import { ExpressInterest } from '../features/interest/ExpressInterest'
import { ListingDetailView } from '../features/listings/ListingDetailView'
import { listingsApi } from '../features/listings/listingsApi'
import { ReportButton } from '../features/reports/ReportButton'
import { SaveButton } from '../features/saved/SaveButton'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

/**
 * Where "back" goes: the marketplace results the visitor came from (their query string
 * is passed as `state.from` by the listing cards), the saved listings page (`state.savedFrom`,
 * its query string), or the unfiltered marketplace.
 */
function backTarget(state: unknown): { href: string; label: string } {
  const { from, savedFrom } = (state ?? {}) as { from?: unknown; savedFrom?: unknown }
  if (typeof from === 'string' && from.startsWith('?') && from.length > 1) {
    return { href: `${paths.listings}${from}`, label: 'Back to results' }
  }
  if (typeof savedFrom === 'string' && (savedFrom === '' || savedFrom.startsWith('?'))) {
    return { href: `${paths.saved}${savedFrom}`, label: 'Back to saved listings' }
  }
  return { href: paths.listings, label: 'Back to marketplace' }
}

export function ListingDetailPage() {
  const { slug = '' } = useParams()
  const location = useLocation()
  const { account } = useAuth()
  const back = backTarget(location.state)

  const load = useCallback((signal: AbortSignal) => listingsApi.bySlug(slug, signal), [slug])
  const result = useAsync(load)

  return (
    <div className="listing-page">
      <nav aria-label="Breadcrumb" className="back-nav">
        <Link to={back.href} className="back-link">
          <span aria-hidden="true">←</span> {back.label}
        </Link>
      </nav>

      {result.status === 'success' ? (
        <ListingDetailView
          // Keyed so a previous listing's interest outcome never carries over to another one.
          key={result.data.id}
          listing={result.data}
          actions={
            <>
              <ExpressInterest listing={result.data} />
              <SaveButton listingId={result.data.id} block />
              {account?.id !== result.data.owner.id && (
                <ReportButton
                  target={{ type: 'LISTING', id: result.data.id, description: `Listing “${result.data.title}”` }}
                  label="Report listing"
                  className="report-button-quiet"
                />
              )}
            </>
          }
        />
      ) : result.status === 'loading' ? (
        <div className="listing-detail-loading">
          <Loading label="Loading listing…" />
          <div className="listing-detail-skeleton" aria-hidden="true">
            <span className="skeleton skeleton-tag" />
            <span className="skeleton skeleton-heading" />
            <span className="skeleton skeleton-line" />
            <span className="skeleton skeleton-line skeleton-line-short" />
          </div>
        </div>
      ) : result.error instanceof ApiError && result.error.status === 404 ? (
        <section className="not-found-panel">
          <title>Listing not found · Conflux</title>
          <p className="eyebrow">404</p>
          <h1 className="page-title">Listing not found</h1>
          <p className="lead">This listing doesn't exist, or it is no longer available on the marketplace.</p>
          <div className="button-row">
            <Link to={paths.listings} className="button button-primary">
              Browse the marketplace
            </Link>
          </div>
        </section>
      ) : (
        <div className="listing-error">
          <h1 className="visually-hidden">Listing unavailable</h1>
          <ErrorMessage
            title="We couldn't load this listing"
            message={result.error instanceof ApiError ? result.error.message : 'Something went wrong. Please try again.'}
          >
            <div className="button-row">
              <Button variant="secondary" onClick={result.retry}>
                Try again
              </Button>
            </div>
          </ErrorMessage>
        </div>
      )}
    </div>
  )
}
