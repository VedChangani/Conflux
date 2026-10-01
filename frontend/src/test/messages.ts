import type { Conversation, Message } from '../features/messages/types'
import { BOB, ME } from './connections'

/** The conversation of Bob's accepted request for the signed-in user's listing, as `ConversationResponse`. */
export function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 31,
    connectionId: 21,
    listing: { id: 5, slug: 'pairwise', title: 'Pairwise', shortPitch: 'Find a technical co-founder.' },
    otherParticipant: BOB,
    lastMessagePreview: 'Happy to talk on Thursday.',
    lastMessageAt: '2026-03-03T10:00:00Z',
    createdAt: '2026-03-02T10:00:00Z',
    updatedAt: '2026-03-02T10:00:00Z',
    ...overrides,
  }
}

/** A message from Bob, as `MessageResponse`. */
export function messageFromBob(overrides: Partial<Message> = {}): Message {
  return { id: 501, sender: BOB, content: 'Happy to talk on Thursday.', createdAt: '2026-03-03T10:00:00Z', ...overrides }
}

/** A message from the signed-in user, as `MessageResponse`. */
export function messageFromMe(overrides: Partial<Message> = {}): Message {
  return { id: 502, sender: ME, content: 'Thanks for accepting!', createdAt: '2026-03-02T11:00:00Z', ...overrides }
}
