import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { SaveButton } from '../saved/SaveButton'
import { formatDate, initialOf, priceSummary } from './format'
import { ASSET_TYPE_LABELS, CATEGORY_LABELS, labelOf, MARKETPLACE_MODE_LABELS, STAGE_LABELS } from './labels'
import type { ListingCardSummary } from './types'

export interface ListingCardSaveOptions {
  knownSaved?: boolean
  onSavedChange?: (listing: ListingCardSummary, saved: boolean) => void
}

interface ListingCardProps extends ListingCardSaveOptions {
  listing: ListingCardSummary
  linkState?: unknown
  headingLevel?: 'h2' | 'h3'
}

export function ListingCard({
  listing,
  linkState,
  headingLevel: Heading = 'h3',
  knownSaved,
  onSavedChange,
}: ListingCardProps) {
  const price = priceSummary(listing)
  const mode = listing.marketplaceMode

  return (
    <article className="listing-card" data-mode={mode.toLowerCase()}>
      <div className="listing-card-top">
        <span className="tag tag-strong">{labelOf(ASSET_TYPE_LABELS, listing.assetType)}</span>
        <span className="mode-label">
          <span className="mode-dot" aria-hidden="true" />
          {labelOf(MARKETPLACE_MODE_LABELS, mode)}
        </span>
      </div>

      <div className="listing-card-body">
        <Heading className="listing-card-title">
          <Link to={paths.listing(listing.slug)} state={linkState} className="listing-card-link">
            {listing.title}
          </Link>
        </Heading>
        <p className="listing-card-pitch">{listing.shortPitch}</p>
      </div>

      <dl className="listing-card-facts">
        <div>
          <dt>Category</dt>
          <dd>{labelOf(CATEGORY_LABELS, listing.category)}</dd>
        </div>
        <div>
          <dt>Stage</dt>
          <dd>{labelOf(STAGE_LABELS, listing.stage)}</dd>
        </div>
      </dl>

      <div className="listing-card-footer">
        <div className="listing-card-price-row">
          <p className="listing-card-price">
            <span className="visually-hidden">{price.priced ? 'Asking price: ' : 'Price: '}</span>
            <span className={price.priced ? 'price' : 'price price-muted'}>{price.value}</span>
            {price.negotiable && <span className="price-note">Negotiable</span>}
          </p>
          <SaveButton
            listingId={listing.id}
            listingTitle={listing.title}
            knownSaved={knownSaved}
            onSavedChange={onSavedChange && ((saved) => onSavedChange(listing, saved))}
          />
        </div>
        <p className="listing-card-owner">
          <span className="avatar avatar-small" aria-hidden="true">
            {initialOf(listing.owner.displayName)}
          </span>
          <span className="listing-card-owner-text">
            <span className="visually-hidden">Listed by </span>
            <span className="listing-card-owner-name">{listing.owner.displayName}</span>
            {listing.publishedAt && (
              <time dateTime={listing.publishedAt} className="listing-card-date">
                {formatDate(listing.publishedAt)}
              </time>
            )}
          </span>
        </p>
      </div>
    </article>
  )
}

export function ListingCardSkeleton() {
  return (
    <div className="listing-card listing-card-skeleton" aria-hidden="true">
      <span className="skeleton skeleton-tag" />
      <span className="skeleton skeleton-title" />
      <span className="skeleton skeleton-line" />
      <span className="skeleton skeleton-line skeleton-line-short" />
      <span className="skeleton skeleton-footer" />
    </div>
  )
}
