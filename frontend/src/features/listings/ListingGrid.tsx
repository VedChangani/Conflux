import { ListingCard, ListingCardSkeleton, type ListingCardSaveOptions } from './ListingCard'
import type { ListingCardSummary } from './types'

interface ListingGridProps extends ListingCardSaveOptions {
  listings: readonly ListingCardSummary[]
  linkState?: unknown
  headingLevel?: 'h2' | 'h3'
}

export function ListingGrid({ listings, linkState, headingLevel, knownSaved, onSavedChange }: ListingGridProps) {
  return (
    <ul className="listing-grid">
      {listings.map((listing) => (
        <li key={listing.id}>
          <ListingCard
            listing={listing}
            linkState={linkState}
            headingLevel={headingLevel}
            knownSaved={knownSaved}
            onSavedChange={onSavedChange}
          />
        </li>
      ))}
    </ul>
  )
}

export function ListingGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="listing-grid" aria-hidden="true">
      {Array.from({ length: count }, (_, index) => (
        <ListingCardSkeleton key={index} />
      ))}
    </div>
  )
}
