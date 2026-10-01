export const MESSAGE_MAX_LENGTH = 5_000

export interface ConversationListing {
  id: number
  slug: string
  title: string
  shortPitch: string
}

export interface PublicUser {
  id: number
  username: string
  displayName: string
}

export interface Conversation {
  id: number
  connectionId: number
  listing: ConversationListing
  otherParticipant: PublicUser
  lastMessagePreview: string | null
  lastMessageAt: string | null
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  sender: PublicUser
  content: string
  createdAt: string
}
