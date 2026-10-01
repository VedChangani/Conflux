import { apiClient } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import type { Conversation, Message } from './types'

export const CONVERSATIONS_PAGE_SIZE = 20
export const MESSAGES_PAGE_SIZE = 50

const LOOKUP_PAGE_SIZE = 50
const LOOKUP_MAX_PAGES = 5

export const messagesApi = {
  conversations: (page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<Conversation>>(`/conversations?page=${page}&size=${size}`, { signal }),
  conversation: (id: number, signal?: AbortSignal) => apiClient.get<Conversation>(`/conversations/${id}`, { signal }),
  messages: (id: number, page: number, size: number, signal?: AbortSignal) =>
    apiClient.get<PageResponse<Message>>(`/conversations/${id}/messages?page=${page}&size=${size}`, { signal }),
  send: (id: number, content: string) => apiClient.post<Message>(`/conversations/${id}/messages`, { content }),

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
