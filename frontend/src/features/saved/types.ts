import type { ListingCardSummary } from '../listings/types'

/**
 * A saved listing (`SavedListingResponse`): a published listing card plus when it was
 * saved. `id` is the listing id, which is what save/unsave take.
 */
export interface SavedListing extends ListingCardSummary {
  /** ISO-8601 instant. */
  savedAt: string
}
