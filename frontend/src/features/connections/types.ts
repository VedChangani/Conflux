export const CONNECTION_STATUSES = ['PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'] as const
export type ConnectionStatus = (typeof CONNECTION_STATUSES)[number]

export interface ConnectionParticipant {
  id: number
  username: string
  displayName: string
}

export interface ConnectionListing {
  id: number
  slug: string
  title: string
  shortPitch: string
  status: string
}

export interface Connection {
  id: number
  status: ConnectionStatus
  createdAt: string
  updatedAt: string
  listing: ConnectionListing
  requester: ConnectionParticipant
  owner: ConnectionParticipant
}

export type ConnectionBox = 'sent' | 'received'
