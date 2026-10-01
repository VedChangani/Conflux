import { createContext } from 'react'

export interface SavedListingsContextValue {
  isSaved: (listingId: number) => boolean | undefined
  checking: boolean
  ensureLoaded: () => void
  setSaved: (listingId: number, saved: boolean) => Promise<void>
}

export const SavedListingsContext = createContext<SavedListingsContextValue | null>(null)
