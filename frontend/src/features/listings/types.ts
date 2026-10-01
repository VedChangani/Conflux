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
export const SORTS = ['NEWEST', 'OLDEST', 'UPDATED', 'PRICE_LOW', 'PRICE_HIGH'] as const

export type ListingAssetType = (typeof ASSET_TYPES)[number]
export type ListingMarketplaceMode = (typeof MARKETPLACE_MODES)[number]
export type ListingCategory = (typeof CATEGORIES)[number]
export type ListingStage = (typeof STAGES)[number]
export type ListingSort = (typeof SORTS)[number]

export interface ListingOwner {
  id: number
  username: string
  displayName: string
}

export interface ListingCard {
  id: number
  slug: string
  title: string
  shortPitch: string
  assetType: ListingAssetType
  marketplaceMode: ListingMarketplaceMode
  category: ListingCategory
  stage: ListingStage
  askingPrice: number | null
  currency: string | null
  priceNegotiable: boolean
  publishedAt: string | null
  owner: ListingOwner
}

export interface ListingCardSummary extends Omit<ListingCard, 'owner'> {
  owner: Pick<ListingOwner, 'username' | 'displayName'>
}

export interface ListingDetail extends ListingCard {
  description: string
  problem: string | null
  solution: string | null
  status: string
  collaborationDetails: string | null
  createdAt: string
  updatedAt: string
}
