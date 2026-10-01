import type { Connection, ConnectionParticipant } from '../features/connections/types'
import { ACCOUNT } from './api'

export const ME: ConnectionParticipant = { id: ACCOUNT.id, username: ACCOUNT.username, displayName: ACCOUNT.displayName }
export const ALICE: ConnectionParticipant = { id: 7, username: 'alice', displayName: 'Alice Anders' }
export const BOB: ConnectionParticipant = { id: 8, username: 'bob', displayName: 'Bob Brown' }

export const PENDING_COUNT = 'GET /connections/received?status=PENDING&page=0&size=1'

export function sentConnection(overrides: Partial<Connection> = {}): Connection {
  return {
    id: 11,
    status: 'PENDING',
    createdAt: '2026-03-01T12:00:00Z',
    updatedAt: '2026-03-01T12:00:00Z',
    listing: { id: 1, slug: 'ledgerly', title: 'Ledgerly', shortPitch: 'Close the books faster.', status: 'PUBLISHED' },
    requester: ME,
    owner: ALICE,
    ...overrides,
  }
}

export function receivedConnection(overrides: Partial<Connection> = {}): Connection {
  return {
    id: 21,
    status: 'PENDING',
    createdAt: '2026-03-02T09:30:00Z',
    updatedAt: '2026-03-02T09:30:00Z',
    listing: { id: 5, slug: 'pairwise', title: 'Pairwise', shortPitch: 'Find a technical co-founder.', status: 'PUBLISHED' },
    requester: BOB,
    owner: ME,
    ...overrides,
  }
}
