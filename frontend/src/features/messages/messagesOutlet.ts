import { useOutletContext } from 'react-router'

export interface MessagesOutletContext {
  refreshConversations: () => void
}

export function useMessagesOutlet(): MessagesOutletContext {
  return useOutletContext<MessagesOutletContext>()
}
