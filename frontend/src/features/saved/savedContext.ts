import { createContext } from 'react'

export interface SavedListingsContextValue {
  /**
   * Whether the signed-in user has saved the listing: `true`/`false` when known,
   * `undefined` when it is not (anonymous, not looked up yet, still looking, or the
   * lookup failed).
   */
  isSaved: (listingId: number) => boolean | undefined
  /** True for a signed-in user until the lookup of their saved listings has settled. */
  checking: boolean
  /** Looks up the signed-in user's saved listings, once per session. Later calls do nothing. */
  ensureLoaded: () => void
  /**
   * Saves (`true`) or unsaves (`false`) a listing. Resolves once the API has confirmed it,
   * and only then is the new state reflected by {@link isSaved}; rejects with the
   * {@link ApiError} otherwise. Calls for the same listing never overlap: a repeat of the
   * request in flight joins it, a different one waits for it.
   */
  setSaved: (listingId: number, saved: boolean) => Promise<void>
}

export const SavedListingsContext = createContext<SavedListingsContextValue | null>(null)
