import { useCallback } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { useAsync } from '../../lib/useAsync'
import { ApiError } from '../../services/apiClient'
import { ListingGrid, ListingGridSkeleton } from './ListingGrid'
import { listingsApi } from './listingsApi'

const LATEST_COUNT = 6

/** The most recently published listings (the backend's default order), for the home page. */
export function LatestListings() {
  const load = useCallback((signal: AbortSignal) => listingsApi.discover(`page=0&size=${LATEST_COUNT}`, signal), [])
  const result = useAsync(load)

  return (
    <section className="latest" aria-labelledby="latest-heading">
      <div className="section-heading">
        <h2 id="latest-heading">Latest listings</h2>
        <Link to={paths.listings} className="section-link">
          View all listings <span aria-hidden="true">→</span>
        </Link>
      </div>

      {result.status === 'loading' ? (
        <>
          <p className="visually-hidden" role="status">
            Loading the latest listings…
          </p>
          <ListingGridSkeleton count={3} />
        </>
      ) : result.status === 'error' ? (
        <ErrorMessage
          title="We couldn't load the latest listings"
          message={result.error instanceof ApiError ? result.error.message : 'Something went wrong. Please try again.'}
        >
          <div className="button-row">
            <Button variant="secondary" onClick={result.retry}>
              Try again
            </Button>
          </div>
        </ErrorMessage>
      ) : result.data.content.length === 0 ? (
        <div className="empty-state">
          <span className="empty-state-mark" aria-hidden="true" />
          <h3 className="empty-state-title">No listings yet</h3>
          <p className="empty-state-text">Published projects will appear here. Check back soon.</p>
        </div>
      ) : (
        <ListingGrid listings={result.data.content} />
      )}
    </section>
  )
}
