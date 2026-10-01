/** Enum values exactly as the backend serializes them (upper-case names). */
export const ASSET_TYPES = ['IDEA', 'PROJECT', 'MVP', 'STARTUP'] as const
export const MARKETPLACE_MODES = ['ACQUIRE', 'COLLABORATE'] as const
export const CATEGORIES = [
  'AI',
  'SAAS',
  'FINTECH',
  'EDTECH',
  'HEALTHTECH',
  'ECOMMERCE',
  'DEVELOPER_TOOLS',
  'PRODUCTIVITY',
  'SOCIAL',
  'MARKETPLACE',
  'OTHER',
] as const
export const STAGES = ['CONCEPT', 'PROTOTYPE', 'MVP', 'LIVE', 'REVENUE'] as const
/** The only orderings `GET /listings` accepts. `NEWEST` is the backend default. */
export const SORTS = ['NEWEST', 'OLDEST', 'UPDATED', 'PRICE_LOW', 'PRICE_HIGH'] as const

export type ListingAssetType = (typeof ASSET_TYPES)[number]
export type ListingMarketplaceMode = (typeof MARKETPLACE_MODES)[number]
export type ListingCategory = (typeof CATEGORIES)[number]
export type ListingStage = (typeof STAGES)[number]
export type ListingSort = (typeof SORTS)[number]

/** Public owner summary. Never contains email, role or status. */
export interface ListingOwner {
  id: number
  username: string
  displayName: string
}

/** A published listing in marketplace results (`ListingCardResponse`). */
export interface ListingCard {
  id: number
  slug: string
  title: string
  shortPitch: string
  assetType: ListingAssetType
  marketplaceMode: ListingMarketplaceMode
  category: ListingCategory
  stage: ListingStage
  /** Serialized as a JSON number; `null` when no price is set. Always paired with `currency`. */
  askingPrice: number | null
  currency: string | null
  priceNegotiable: boolean
  /** ISO-8601 instant. */
  publishedAt: string | null
  owner: ListingOwner
}

/**
 * What a listing card needs to render. Wider than {@link ListingCard} because saved
 * listings (`SavedListingResponse`) carry an owner without an id.
 */
export interface ListingCardSummary extends Omit<ListingCard, 'owner'> {
  owner: Pick<ListingOwner, 'username' | 'displayName'>
}

/** A published listing on its public detail page (`ListingDetailResponse`). */
export interface ListingDetail extends ListingCard {
  description: string
  problem: string | null
  solution: string | null
  status: string
  collaborationDetails: string | null
  createdAt: string
  updatedAt: string
}
