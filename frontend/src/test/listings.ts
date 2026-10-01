import type { ListingCard, ListingDetail } from '../features/listings/types'
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
