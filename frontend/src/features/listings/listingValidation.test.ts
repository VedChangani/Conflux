import { describe, expect, it } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { ownerListing } from '../../test/listings'
import {
  EMPTY_LISTING_FORM,
  formValuesOf,
  serverFieldErrors,
  toListingRequest,
  validateCurrency,
  validateListing,
  validatePrice,
  type ListingFormValues,
} from './listingValidation'

const VALID: ListingFormValues = {
  ...EMPTY_LISTING_FORM,
  title: 'Harbor Metrics',
  shortPitch: 'Fleet dashboards for small teams.',
  description: 'Dashboards for container fleets.',
  assetType: 'MVP',
  marketplaceMode: 'ACQUIRE',
  category: 'DEVELOPER_TOOLS',
  stage: 'MVP',
}

describe('listing validation', () => {
  it('accepts a complete form', () => {
    expect(validateListing(VALID)).toEqual({})
  })

  it('requires title, short pitch, description and all four choices, like the backend', () => {
    expect(validateListing({ ...EMPTY_LISTING_FORM, title: '   ' })).toEqual({
      title: 'Enter a title.',
      shortPitch: 'Enter a short pitch.',
      description: 'Enter a description.',
      assetType: 'Choose a type.',
      marketplaceMode: 'Choose an opportunity.',
      category: 'Choose a category.',
      stage: 'Choose a stage.',
    })
  })

  it('rejects enum values the backend does not know', () => {
    expect(validateListing({ ...VALID, assetType: 'COMPANY', stage: 'mvp' })).toEqual({
      assetType: 'Choose a type.',
      stage: 'Choose a stage.',
    })
  })

  it.each([
    ['title', 120],
    ['shortPitch', 240],
    ['description', 10_000],
    ['problem', 10_000],
    ['solution', 10_000],
    ['collaborationDetails', 5_000],
  ] as const)('limits %s to %i characters after trimming', (field, limit) => {
    expect(validateListing({ ...VALID, [field]: `  ${'x'.repeat(limit)}  ` })[field]).toBeUndefined()
    expect(validateListing({ ...VALID, [field]: 'x'.repeat(limit + 1) })[field]).toMatch(/at most .* \(1 too many\)/)
  })

  it('leaves the optional texts optional', () => {
    expect(validateListing({ ...VALID, problem: '', solution: ' ', collaborationDetails: '' })).toEqual({})
  })

  it.each(['', '0', '25000', '1250.5', '1250.50', '9999999999999.99', '0000000000000001', '1.500', ' 42 ', '12.', '.5'])(
    'accepts the price %j',
    (price) => {
      expect(validatePrice(price)).toBeNull()
    },
  )

  it.each(['-1', '1e3', '1,000', '$500', '.', 'abc', '1 000', '1.2.3'])('rejects the price %j as not a plain number', (price) => {
    expect(validatePrice(price)).toMatch(/plain number/)
  })

  it('allows at most 13 integer digits and 2 decimal places, as @Digits(integer = 13, fraction = 2)', () => {
    expect(validatePrice('12345678901234')).toBe('Use at most 13 digits before the decimal point.')
    expect(validatePrice('1.234')).toBe('Use at most 2 decimal places.')
  })

  it('requires a three-letter upper-case currency only when there is a price', () => {
    expect(validateCurrency('', '')).toBeNull()
    expect(validateCurrency('nope', '')).toBeNull()
    expect(validateCurrency('EUR', '10')).toBeNull()
    expect(validateCurrency('', '10')).toMatch(/three-letter currency code/)
    expect(validateCurrency('usd', '10')).toMatch(/three-letter currency code/)
    expect(validateCurrency('US', '10')).toMatch(/three-letter currency code/)
    expect(validateCurrency('US1', '10')).toMatch(/three-letter currency code/)
  })

  it('builds the exact request: trimmed text, null for blank optionals, a numeric price', () => {
    expect(
      toListingRequest({
        ...VALID,
        title: '  Harbor Metrics  ',
        problem: '   ',
        solution: ' Automate it. ',
        askingPrice: ' 1250.50 ',
        currency: 'EUR',
        priceNegotiable: true,
      }),
    ).toEqual({
      title: 'Harbor Metrics',
      shortPitch: 'Fleet dashboards for small teams.',
      description: 'Dashboards for container fleets.',
      problem: null,
      solution: 'Automate it.',
      assetType: 'MVP',
      marketplaceMode: 'ACQUIRE',
      category: 'DEVELOPER_TOOLS',
      stage: 'MVP',
      askingPrice: 1250.5,
      currency: 'EUR',
      priceNegotiable: true,
      collaborationDetails: null,
    })
  })

  it('sends neither price nor currency when no price is given, keeping the negotiable flag', () => {
    const request = toListingRequest({ ...VALID, askingPrice: '', currency: 'USD', priceNegotiable: true })
    expect(request.askingPrice).toBeNull()
    expect(request.currency).toBeNull()
    expect(request.priceNegotiable).toBe(true)
  })

  it('round-trips a stored listing into the form', () => {
    const listing = ownerListing({ askingPrice: 1250.5, currency: 'EUR', problem: 'Slow', collaborationDetails: null })
    const values = formValuesOf(listing)
    expect(values).toMatchObject({ askingPrice: '1250.5', currency: 'EUR', problem: 'Slow', collaborationDetails: '' })
    expect(toListingRequest(values)).toMatchObject({ askingPrice: 1250.5, currency: 'EUR', problem: 'Slow' })
    expect(formValuesOf(ownerListing({ askingPrice: null, currency: null })).askingPrice).toBe('')
  })

  it('maps backend field errors onto the form fields it knows, never echoing backend wording', () => {
    const error = new ApiError(400, {
      status: 400,
      detail: 'Invalid request content.',
      errors: [
        { field: 'currency', message: 'must be three upper-case letters, e.g. USD' },
        { field: 'title', message: 'size must be between 0 and 120' },
        { field: 'ownerId', message: 'unknown' },
      ],
    })
    const errors = serverFieldErrors(error, { ...VALID, askingPrice: '10', currency: 'EUR' })
    expect(Object.keys(errors).sort()).toEqual(['currency', 'title'])
    expect(errors.currency).toBe('This value wasn’t accepted. Check it and try again.')
    expect(JSON.stringify(errors)).not.toContain('size must be')
  })
})
