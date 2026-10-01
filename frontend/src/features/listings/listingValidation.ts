import { ApiError } from '../../services/apiClient'
import {
  ASSET_TYPES,
  CATEGORIES,
  MARKETPLACE_MODES,
  STAGES,
  type ListingAssetType,
  type ListingCategory,
  type ListingDetail,
  type ListingMarketplaceMode,
  type ListingRequest,
  type ListingStage,
} from './types'

export const LISTING_LIMITS = {
  title: 120,
  shortPitch: 240,
  description: 10_000,
  problem: 10_000,
  solution: 10_000,
  collaborationDetails: 5_000,
} as const

export type ListingTextField = keyof typeof LISTING_LIMITS
export type ListingChoiceField = 'assetType' | 'marketplaceMode' | 'category' | 'stage'
export type ListingFormField = ListingTextField | ListingChoiceField | 'askingPrice' | 'currency' | 'priceNegotiable'

export const PRICE_INTEGER_DIGITS = 13
export const PRICE_FRACTION_DIGITS = 2
export const DEFAULT_CURRENCY = 'USD'

export interface ListingFormValues {
  title: string
  shortPitch: string
  description: string
  problem: string
  solution: string
  assetType: string
  marketplaceMode: string
  category: string
  stage: string
  askingPrice: string
  currency: string
  priceNegotiable: boolean
  collaborationDetails: string
}

export type ListingFieldErrors = Partial<Record<ListingFormField, string>>

export const EMPTY_LISTING_FORM: ListingFormValues = {
  title: '',
  shortPitch: '',
  description: '',
  problem: '',
  solution: '',
  assetType: '',
  marketplaceMode: '',
  category: '',
  stage: '',
  askingPrice: '',
  currency: DEFAULT_CURRENCY,
  priceNegotiable: false,
  collaborationDetails: '',
}

const TEXT_LABELS: Record<ListingTextField, string> = {
  title: 'Title',
  shortPitch: 'Short pitch',
  description: 'Description',
  problem: 'The problem',
  solution: 'The solution',
  collaborationDetails: 'Collaboration details',
}

const REQUIRED_TEXT: Partial<Record<ListingTextField, string>> = {
  title: 'Enter a title.',
  shortPitch: 'Enter a short pitch.',
  description: 'Enter a description.',
}

const CHOICES: Record<ListingChoiceField, { values: readonly string[]; missing: string }> = {
  assetType: { values: ASSET_TYPES, missing: 'Choose a type.' },
  marketplaceMode: { values: MARKETPLACE_MODES, missing: 'Choose an opportunity.' },
  category: { values: CATEGORIES, missing: 'Choose a category.' },
  stage: { values: STAGES, missing: 'Choose a stage.' },
}

const TEXT_FIELDS = Object.keys(LISTING_LIMITS) as ListingTextField[]
const CHOICE_FIELDS = Object.keys(CHOICES) as ListingChoiceField[]
const ALL_FIELDS: readonly string[] = [...TEXT_FIELDS, ...CHOICE_FIELDS, 'askingPrice', 'currency', 'priceNegotiable']

const numberFormat = new Intl.NumberFormat('en-US')

function validateText(field: ListingTextField, raw: string): string | null {
  const value = raw.trim()
  if (value === '') {
    return REQUIRED_TEXT[field] ?? null
  }
  const limit = LISTING_LIMITS[field]
  if (value.length > limit) {
    return `${TEXT_LABELS[field]} can be at most ${numberFormat.format(limit)} characters (${numberFormat.format(value.length - limit)} too many).`
  }
  return null
}

function validateChoice(field: ListingChoiceField, value: string): string | null {
  const { values, missing } = CHOICES[field]
  return values.includes(value) ? null : missing
}

export function validatePrice(raw: string): string | null {
  const value = raw.trim()
  if (value === '') {
    return null
  }
  const match = /^(\d*)(?:\.(\d*))?$/.exec(value)
  if (!match || !/\d/.test(value)) {
    return 'Enter the price as a plain number, e.g. 25000 or 1250.50, without currency symbols or separators.'
  }
  const integerDigits = match[1].replace(/^0+(?=\d)/, '').length
  const fractionDigits = (match[2] ?? '').replace(/0+$/, '').length
  if (integerDigits > PRICE_INTEGER_DIGITS) {
    return `Use at most ${PRICE_INTEGER_DIGITS} digits before the decimal point.`
  }
  if (fractionDigits > PRICE_FRACTION_DIGITS) {
    return `Use at most ${PRICE_FRACTION_DIGITS} decimal places.`
  }
  return null
}

export function validateCurrency(raw: string, askingPrice: string): string | null {
  if (askingPrice.trim() === '') {
    return null
  }
  return /^[A-Z]{3}$/.test(raw.trim()) ? null : 'Enter a three-letter currency code in capitals, e.g. USD.'
}

export function validateField(field: ListingFormField, values: ListingFormValues): string | null {
  if (field === 'askingPrice') {
    return validatePrice(values.askingPrice)
  }
  if (field === 'currency') {
    return validateCurrency(values.currency, values.askingPrice)
  }
  if (field === 'priceNegotiable') {
    return null
  }
  if (field in CHOICES) {
    return validateChoice(field as ListingChoiceField, values[field as ListingChoiceField])
  }
  return validateText(field as ListingTextField, values[field as ListingTextField])
}

export function validateListing(values: ListingFormValues): ListingFieldErrors {
  const errors: ListingFieldErrors = {}
  for (const field of ALL_FIELDS as ListingFormField[]) {
    const error = validateField(field, values)
    if (error) {
      errors[field] = error
    }
  }
  return errors
}

export function remainingCharacters(field: ListingTextField, value: string): string {
  const left = LISTING_LIMITS[field] - value.trim().length
  return left >= 0
    ? `${numberFormat.format(left)} characters left`
    : `${numberFormat.format(-left)} characters over the limit`
}

export function formValuesOf(listing: ListingDetail): ListingFormValues {
  return {
    title: listing.title,
    shortPitch: listing.shortPitch,
    description: listing.description,
    problem: listing.problem ?? '',
    solution: listing.solution ?? '',
    assetType: listing.assetType,
    marketplaceMode: listing.marketplaceMode,
    category: listing.category,
    stage: listing.stage,
    askingPrice: listing.askingPrice === null ? '' : String(listing.askingPrice),
    currency: listing.currency ?? DEFAULT_CURRENCY,
    priceNegotiable: listing.priceNegotiable,
    collaborationDetails: listing.collaborationDetails ?? '',
  }
}

export function toListingRequest(values: ListingFormValues): ListingRequest {
  const optional = (value: string) => value.trim() || null
  const price = values.askingPrice.trim()
  return {
    title: values.title.trim(),
    shortPitch: values.shortPitch.trim(),
    description: values.description.trim(),
    problem: optional(values.problem),
    solution: optional(values.solution),
    assetType: values.assetType as ListingAssetType,
    marketplaceMode: values.marketplaceMode as ListingMarketplaceMode,
    category: values.category as ListingCategory,
    stage: values.stage as ListingStage,
    askingPrice: price === '' ? null : Number(price),
    currency: price === '' ? null : values.currency.trim(),
    priceNegotiable: values.priceNegotiable,
    collaborationDetails: optional(values.collaborationDetails),
  }
}

export function serverFieldErrors(error: unknown, values: ListingFormValues): ListingFieldErrors {
  const errors: ListingFieldErrors = {}
  if (error instanceof ApiError) {
    for (const { field } of error.fieldErrors) {
      if (ALL_FIELDS.includes(field)) {
        const name = field as ListingFormField
        errors[name] = validateField(name, values) ?? 'This value wasn’t accepted. Check it and try again.'
      }
    }
  }
  return errors
}
