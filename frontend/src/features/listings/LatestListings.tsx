import { useCallback } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { EmptyState } from '../../components/EmptyState'
import { LoadError } from '../../components/LoadError'
import { useAsync } from '../../lib/useAsync'
import { ListingGrid, ListingGridSkeleton } from './ListingGrid'
import { listingsApi } from './listingsApi'

const LATEST_COUNT = 6

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
        <LoadError title="We couldn't load the latest listings" error={result.error} onRetry={result.retry} />
      ) : result.data.content.length === 0 ? (
        <EmptyState title="No listings yet">Published projects will appear here. Check back soon.</EmptyState>
      ) : (
        <ListingGrid listings={result.data.content} />
      )}
    </section>
  )
}
