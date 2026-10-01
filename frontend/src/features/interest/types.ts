import type { Connection } from '../connections/types'

export type { Connection, ConnectionStatus } from '../connections/types'

export interface InterestResult {
  connection: Connection
  created: boolean
}
