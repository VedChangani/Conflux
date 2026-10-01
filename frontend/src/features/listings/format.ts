import type { ListingCard } from './types'

const LOCALE = 'en-US'

export function formatPrice(amount: number, currency: string): string {
  const fractionDigits = Number.isInteger(amount) ? 0 : 2
  try {
    return new Intl.NumberFormat(LOCALE, {
      style: 'currency',
      currency,
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(amount)
  } catch {
    const number = new Intl.NumberFormat(LOCALE, {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(amount)
    return `${number} ${currency}`
  }
}

export interface PriceSummary {
  value: string
  priced: boolean
  negotiable: boolean
}

export function priceSummary(listing: Pick<ListingCard, 'askingPrice' | 'currency' | 'priceNegotiable' | 'marketplaceMode'>): PriceSummary {
  const { askingPrice, currency, priceNegotiable, marketplaceMode } = listing
  if (askingPrice !== null && currency !== null) {
    return {
      value: askingPrice === 0 ? 'Free' : formatPrice(askingPrice, currency),
      priced: true,
      negotiable: priceNegotiable,
    }
  }
  return {
    value: marketplaceMode === 'COLLABORATE' ? 'Open to collaborate' : 'Price on request',
    priced: false,
    negotiable: priceNegotiable,
  }
}

export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium' }).format(date)
}

export function initialOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || '?'
}
