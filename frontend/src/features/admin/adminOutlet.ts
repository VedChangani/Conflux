import { useOutletContext } from 'react-router'

/** What the moderation layout gives the report it shows. */
export interface AdminOutletContext {
  /** Reloads the queue, e.g. after a decision moved a report out of the current filter. */
  reloadQueue: () => void
  /** The queue's URL query (`?status=…&page=…`), so "back" returns to the same page of it. */
  queueSearch: string
}

export function useAdminOutlet(): AdminOutletContext {
  return useOutletContext<AdminOutletContext>()
}
