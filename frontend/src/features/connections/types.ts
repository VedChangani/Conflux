/**
 * Lifecycle of a connection request (`ConnectionStatus`). Only PENDING can change:
 * the listing owner accepts or rejects it, the requester withdraws it. The others are final.
 */
export const CONNECTION_STATUSES = ['PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'] as const
export type ConnectionStatus = (typeof CONNECTION_STATUSES)[number]

/** One side of a connection (`ConnectionResponse.Participant`). Public fields only. */
export interface ConnectionParticipant {
  id: number
  username: string
  displayName: string
}

/** The listing a connection is about (`ConnectionResponse.ListingSummary`). */
export interface ConnectionListing {
  id: number
  slug: string
  title: string
  shortPitch: string
  /** The listing's own status; only `PUBLISHED` listings are on the marketplace. */
  status: string
}

/** `ConnectionResponse`: an expression of interest as seen by one of its two participants. */
export interface Connection {
  id: number
  status: ConnectionStatus
  /** ISO-8601 instants. */
  createdAt: string
  updatedAt: string
  listing: ConnectionListing
  requester: ConnectionParticipant
  owner: ConnectionParticipant
}

/** Which side of the user's connections a list shows. */
export type ConnectionBox = 'sent' | 'received'
