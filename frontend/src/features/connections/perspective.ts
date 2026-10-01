import type { Connection } from './types'

export type ConnectionRole = 'owner' | 'requester' | null

export function roleOf(connection: Connection, accountId: number | null): ConnectionRole {
  if (accountId === null) {
    return null
  }
  if (connection.owner.id === accountId) {
    return 'owner'
  }
  return connection.requester.id === accountId ? 'requester' : null
}

export interface AvailableActions {
  decide: boolean
  withdraw: boolean
}

export function availableActions(connection: Connection, role: ConnectionRole): AvailableActions {
  const pending = connection.status === 'PENDING'
  return { decide: pending && role === 'owner', withdraw: pending && role === 'requester' }
}

export function statusSummary(connection: Connection, role: ConnectionRole): string {
  switch (connection.status) {
    case 'PENDING':
      return role === 'owner' ? 'Waiting for your answer.' : 'Waiting for the owner’s answer.'
    case 'ACCEPTED':
      return role === 'owner' ? 'You accepted this request. You’re connected.' : 'The owner accepted this request. You’re connected.'
    case 'REJECTED':
      return role === 'owner' ? 'You rejected this request.' : 'The owner rejected this request.'
    case 'WITHDRAWN':
      return role === 'requester' ? 'You withdrew this request.' : 'The requester withdrew this request.'
  }
  return 'This request can no longer change.'
}
