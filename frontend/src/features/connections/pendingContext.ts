import { createContext } from 'react'

export interface PendingRequestsContextValue {
  /** Received requests awaiting the user's answer; `null` when unknown (signed out, loading or failed). */
  count: number | null
  /** Reads the count again, e.g. after the user answered a request. */
  refresh: () => void
}

export const PendingRequestsContext = createContext<PendingRequestsContextValue | null>(null)
