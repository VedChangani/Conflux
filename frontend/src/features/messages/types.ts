/** The backend's limit on a message, after trimming (`Message.CONTENT_MAX_LENGTH`). */
export const MESSAGE_MAX_LENGTH = 5_000

/** The listing a conversation is about (`ConversationResponse.ListingSummary`). */
export interface ConversationListing {
  id: number
  slug: string
  title: string
  shortPitch: string
}

/** A public user summary, as conversations and messages carry it. */
export interface PublicUser {
  id: number
  username: string
  displayName: string
}

/** `ConversationResponse`: a conversation as seen by one of its two participants. */
export interface Conversation {
  id: number
  connectionId: number
  listing: ConversationListing
  otherParticipant: PublicUser
  /** The start of the latest message (120 characters at most); `null` without messages. */
  lastMessagePreview: string | null
  /** ISO-8601 instants; `lastMessageAt` is `null` without messages. */
  lastMessageAt: string | null
  createdAt: string
  updatedAt: string
}

/** `MessageResponse`. */
export interface Message {
  id: number
  sender: PublicUser
  content: string
  /** ISO-8601 instant. */
  createdAt: string
}
