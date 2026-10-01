import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { Conversation, Message } from './types'

/** The backend's default page sizes. */
export const CONVERSATIONS_PAGE_SIZE = 20
export const MESSAGES_PAGE_SIZE = 50

/** Pages of the largest size scanned when looking a conversation up by its connection. */
const LOOKUP_PAGE_SIZE = 50
const LOOKUP_MAX_PAGES = 5

/**
 * Conversation and message endpoints. The backend acts for the token's user and checks
 * participation on every call; nothing here sends a user id or a sender.
 */
export const messagesApi = {
  /** The user's conversations, most recent activity first (the backend's order). Zero-based `page`. */
  conversations: (page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<Conversation>>(`/conversations?page=${page}&size=${size}`, { signal }),
  /** 404 unless the user participates. */
  conversation: (id: number, signal?: AbortSignal) => apiClient.get<Conversation>(`/conversations/${id}`, { signal }),
  /** Newest first. Zero-based `page`. */
  messages: (id: number, page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<Message>>(`/conversations/${id}/messages?page=${page}&size=${size}`, { signal }),
  /** Resolves with the stored message. Only the content is sent; the sender is the token's user. */
  send: (id: number, content: string) => apiClient.post<Message>(`/conversations/${id}/messages`, { content }),

  /**
   * The id of the conversation belonging to a connection, or `null` if none was found.
   * No endpoint maps one to the other, so this scans the user's conversations, which come
   * most recently active first (a just-accepted connection's conversation is near the top).
   */
  async conversationIdForConnection(connectionId: number, signal?: AbortSignal): Promise<number | null> {
    for (let page = 0; page < LOOKUP_MAX_PAGES; page++) {
      const result = await messagesApi.conversations(page, LOOKUP_PAGE_SIZE, signal)
      const match = result.content.find((conversation) => conversation.connectionId === connectionId)
      if (match) {
        return match.id
      }
      if (result.last || result.content.length === 0) {
        return null
      }
    }
    return null
  },
}
