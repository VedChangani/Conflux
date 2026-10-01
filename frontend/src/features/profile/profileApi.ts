import { apiClient } from '../../services/apiClient'
import type { ProfileUpdate, UserProfile } from './types'

export const profileApi = {
  publicProfile: (username: string, signal?: AbortSignal) =>
    apiClient.get<UserProfile>(`/users/${encodeURIComponent(username)}`, { auth: false, signal }),
  ownProfile: (signal?: AbortSignal) => apiClient.get<UserProfile>('/profile', { signal }),
  update: (update: ProfileUpdate) => apiClient.put<UserProfile>('/profile', update),
}
