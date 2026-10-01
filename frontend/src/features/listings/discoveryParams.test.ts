import { describe, expect, it } from 'vitest'
import { hasActiveFilters, readDiscoveryQuery, toApiSearch, withChanges, withoutFilters } from './discoveryParams'
import { formatPrice, priceSummary } from './format'

const read = (search: string) => readDiscoveryQuery(new URLSearchParams(search))

describe('discovery query', () => {
  it('translates the 1-based URL page to the zero-based API page', () => {
    expect(toApiSearch(read(''))).toBe('page=0&size=12')
    expect(toApiSearch(read('page=3'))).toBe('page=2&size=12')
  })

  it.each(['page=0', 'page=-2', 'page=abc', 'page=1.5', 'page='])('treats %s as the first page', (search) => {
    expect(read(search).page).toBe(1)
  })

  it('builds the API query in a stable order and skips empty values', () => {
    const query = read('size=24&stage=LIVE&sort=OLDEST&category=&search=%20ai%20&assetType=MVP&page=2')

    expect(toApiSearch(query)).toBe('search=ai&assetType=MVP&stage=LIVE&sort=OLDEST&page=1&size=24')
  })

  it('passes unknown values through for the backend to validate', () => {
    expect(toApiSearch(read('assetType=APP&sort=newest&size=500'))).toBe('assetType=APP&sort=newest&page=0&size=500')
  })

  it('returns to the first page on any change except the page itself', () => {
    const params = new URLSearchParams('category=AI&page=4&ref=mail')

    expect(withChanges(params, { stage: 'LIVE' }).toString()).toBe('category=AI&ref=mail&stage=LIVE')
    expect(withChanges(params, { page: 5 }).toString()).toBe('category=AI&page=5&ref=mail')
    expect(withChanges(params, { page: 1 }).toString()).toBe('category=AI&ref=mail')
    expect(withChanges(params, { category: null }).toString()).toBe('ref=mail')
  })

  it('clears search and filters but keeps sort, size and unrelated parameters', () => {
    const params = new URLSearchParams('search=x&assetType=MVP&marketplaceMode=ACQUIRE&category=AI&stage=LIVE&sort=OLDEST&size=24&page=2&ref=a')

    expect(withoutFilters(params).toString()).toBe('sort=OLDEST&size=24&ref=a')
  })

  it('knows when results are narrowed', () => {
    expect(hasActiveFilters(read('sort=OLDEST&page=2'))).toBe(false)
    expect(hasActiveFilters(read('search=x'))).toBe(true)
    expect(hasActiveFilters(read('stage=LIVE'))).toBe(true)
  })
})

describe('price formatting', () => {
  it('formats amounts in their currency', () => {
    expect(formatPrice(25000, 'USD')).toBe('$25,000')
    expect(formatPrice(1250.5, 'EUR')).toBe('€1,250.50')
    expect(formatPrice(99, 'ZZZ')).toMatch(/99/)
  })

  it('describes unpriced listings by marketplace mode', () => {
    const base = { priceNegotiable: false, askingPrice: null, currency: null } as const
    expect(priceSummary({ ...base, marketplaceMode: 'ACQUIRE' }).value).toBe('Price on request')
    expect(priceSummary({ ...base, marketplaceMode: 'COLLABORATE' }).value).toBe('Open to collaborate')
    expect(priceSummary({ ...base, askingPrice: 0, currency: 'USD', marketplaceMode: 'ACQUIRE' }).value).toBe('Free')
  })
})
