import { apiClient } from '../../services/apiClient'
import type { ProfileUpdate, UserProfile } from './types'

export const profileApi = {
  /**
   * `GET /users/{username}`: an active user's public profile; 404 for unknown and suspended
   * accounts alike. Sent without the stored token: it is public, and a stale token must not
   * stop anyone from viewing it.
   */
  publicProfile: (username: string, signal?: AbortSignal) =>
    apiClient.get<UserProfile>(`/users/${encodeURIComponent(username)}`, { auth: false, signal }),
  /** `GET /profile`: the signed-in user's own profile, whatever the account status. */
  ownProfile: (signal?: AbortSignal) => apiClient.get<UserProfile>('/profile', { signal }),
  /** `PUT /profile`: replaces all editable fields; resolves with the stored profile. */
  update: (update: ProfileUpdate) => apiClient.put<UserProfile>('/profile', update),
}
