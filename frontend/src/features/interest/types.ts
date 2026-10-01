import type { Connection } from '../connections/types'

export type { Connection, ConnectionStatus } from '../connections/types'

/** The answer to `POST /listings/{id}/interest`. */
export interface InterestResult {
  connection: Connection
  /** True for a new request (201); false when an earlier one already existed (200). */
  created: boolean
}
