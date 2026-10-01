import { useOutletContext } from 'react-router'

/** What the messages layout gives the conversation it shows. */
export interface MessagesOutletContext {
  /** Reloads the conversation list, e.g. after sending changed its latest activity. */
  refreshConversations: () => void
}

export function useMessagesOutlet(): MessagesOutletContext {
  return useOutletContext<MessagesOutletContext>()
}
