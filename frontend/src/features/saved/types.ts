import type { ListingCardSummary } from '../listings/types'

export interface SavedListing extends ListingCardSummary {
  savedAt: string
}
