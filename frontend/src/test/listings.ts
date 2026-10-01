import type { ListingCard, ListingDetail, MyListingSummary } from '../features/listings/types'
import type { SavedListing } from '../features/saved/types'
import type { PageResponse } from '../types/api'

export function listingCard(overrides: Partial<ListingCard> = {}): ListingCard {
  return {
    id: 1,
    slug: 'ledgerly',
    title: 'Ledgerly',
    shortPitch: 'Close the books faster.',
    assetType: 'MVP',
    marketplaceMode: 'ACQUIRE',
    category: 'FINTECH',
    stage: 'PROTOTYPE',
    askingPrice: 25000,
    currency: 'USD',
    priceNegotiable: false,
    publishedAt: '2026-01-15T12:00:00Z',
    owner: { id: 7, username: 'alice', displayName: 'Alice Anders' },
    ...overrides,
  }
}

export function listingDetail(overrides: Partial<ListingDetail> = {}): ListingDetail {
  return {
    ...listingCard(),
    description: 'Automated reconciliation for small finance teams.\n\nBuilt on open banking APIs.',
    problem: 'Month-end close takes days.',
    solution: 'Match transactions automatically.',
    status: 'PUBLISHED',
    collaborationDetails: null,
    createdAt: '2026-01-10T12:00:00Z',
    updatedAt: '2026-02-01T12:00:00Z',
    ...overrides,
  }
}

export function savedListing(overrides: Partial<SavedListing> = {}): SavedListing {
  const { owner, ...card } = listingCard()
  return {
    ...card,
    owner: { username: owner.username, displayName: owner.displayName },
    savedAt: '2026-03-01T12:00:00Z',
    ...overrides,
  }
}

export function pageOf<T>(content: T[], { page = 0, size = 12, totalElements = content.length } = {}): PageResponse<T> {
  const totalPages = Math.ceil(totalElements / size)
  return {
    content,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  }
}

export const OWNER = { id: 42, username: 'ada', displayName: 'Ada Lovelace' }

export function ownerListing(overrides: Partial<ListingDetail> = {}): ListingDetail {
  return listingDetail({
    id: 5,
    slug: 'harbor-metrics-1a2b3c4d',
    title: 'Harbor Metrics',
    shortPitch: 'Fleet dashboards for small teams.',
    description: 'Dashboards for container fleets.\n\nBuilt for teams without an SRE.',
    problem: null,
    solution: null,
    assetType: 'MVP',
    marketplaceMode: 'ACQUIRE',
    category: 'DEVELOPER_TOOLS',
    stage: 'MVP',
    askingPrice: 12000,
    currency: 'USD',
    priceNegotiable: true,
    collaborationDetails: null,
    status: 'DRAFT',
    publishedAt: null,
    createdAt: '2026-03-01T09:00:00Z',
    updatedAt: '2026-03-02T10:30:00Z',
    owner: OWNER,
    ...overrides,
  })
}

export function myListingSummary(overrides: Partial<MyListingSummary> = {}): MyListingSummary {
  const listing = ownerListing()
  return {
    id: listing.id,
    slug: listing.slug,
    title: listing.title,
    shortPitch: listing.shortPitch,
    assetType: listing.assetType,
    marketplaceMode: listing.marketplaceMode,
    category: listing.category,
    stage: listing.stage,
    status: 'DRAFT',
    askingPrice: listing.askingPrice,
    currency: listing.currency,
    priceNegotiable: listing.priceNegotiable,
    publishedAt: listing.publishedAt,
    createdAt: listing.createdAt,
    updatedAt: listing.updatedAt,
    ...overrides,
  }
}
