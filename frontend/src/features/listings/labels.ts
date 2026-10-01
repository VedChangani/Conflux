import type { FilterKey } from './discoveryParams'
import {
  ASSET_TYPES,
  CATEGORIES,
  MARKETPLACE_MODES,
  STAGES,
  type ListingAssetType,
  type ListingCategory,
  type ListingMarketplaceMode,
  type ListingSort,
  type ListingStage,
} from './types'

export const ASSET_TYPE_LABELS: Record<ListingAssetType, string> = {
  IDEA: 'Idea',
  PROJECT: 'Project',
  MVP: 'MVP',
  STARTUP: 'Startup',
}

export const MARKETPLACE_MODE_LABELS: Record<ListingMarketplaceMode, string> = {
  ACQUIRE: 'Acquisition',
  COLLABORATE: 'Collaboration',
}

export const CATEGORY_LABELS: Record<ListingCategory, string> = {
  AI: 'AI',
  SAAS: 'SaaS',
  FINTECH: 'Fintech',
  EDTECH: 'Edtech',
  HEALTHTECH: 'Healthtech',
  ECOMMERCE: 'E-commerce',
  DEVELOPER_TOOLS: 'Developer tools',
  PRODUCTIVITY: 'Productivity',
  SOCIAL: 'Social',
  MARKETPLACE: 'Marketplace',
  OTHER: 'Other',
}

export const STAGE_LABELS: Record<ListingStage, string> = {
  CONCEPT: 'Concept',
  PROTOTYPE: 'Prototype',
  MVP: 'MVP',
  LIVE: 'Live',
  REVENUE: 'Revenue',
}

export const SORT_LABELS: Record<ListingSort, string> = {
  NEWEST: 'Newest',
  OLDEST: 'Oldest',
  UPDATED: 'Recently updated',
  PRICE_LOW: 'Price: low to high',
  PRICE_HIGH: 'Price: high to low',
}

/**
 * The label for an enum value, or the raw value made readable when it is unknown
 * (e.g. a value the backend added later, or a hand-edited URL).
 */
export function labelOf(labels: Record<string, string>, value: string): string {
  if (Object.hasOwn(labels, value)) {
    return labels[value]
  }
  const words = value.replace(/_/g, ' ').toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export interface FilterDefinition {
  key: FilterKey
  label: string
  /** The "no filter" choice. */
  anyLabel: string
  values: readonly string[]
  labels: Record<string, string>
}

/** The enum filters of marketplace discovery, in display order. */
export const FILTERS: readonly FilterDefinition[] = [
  { key: 'assetType', label: 'Type', anyLabel: 'All types', values: ASSET_TYPES, labels: ASSET_TYPE_LABELS },
  {
    key: 'marketplaceMode',
    label: 'Opportunity',
    anyLabel: 'Any opportunity',
    values: MARKETPLACE_MODES,
    labels: MARKETPLACE_MODE_LABELS,
  },
  { key: 'category', label: 'Category', anyLabel: 'All categories', values: CATEGORIES, labels: CATEGORY_LABELS },
  { key: 'stage', label: 'Stage', anyLabel: 'Any stage', values: STAGES, labels: STAGE_LABELS },
]
