/**
 * `UserProfileResponse`: the public profile, used for both someone else's page and the
 * user's own. It never carries email, role, status or credentials. `id` is not displayed.
 */
export interface UserProfile {
  id: number
  username: string
  displayName: string
  bio: string | null
  location: string | null
  websiteUrl: string | null
  githubUrl: string | null
  linkedinUrl: string | null
  /** ISO-8601 instant. */
  createdAt: string | null
}

/**
 * `PUT /profile` body: all six editable fields, replacing the stored ones. Identity and
 * account fields (id, username, email, role, status, password) are deliberately absent.
 * Optional fields are `null` when blank.
 */
export interface ProfileUpdate {
  displayName: string
  bio: string | null
  location: string | null
  websiteUrl: string | null
  githubUrl: string | null
  linkedinUrl: string | null
}

export type ProfileField = keyof ProfileUpdate

/** The editable fields, in form order. */
export const PROFILE_FIELDS: readonly ProfileField[] = [
  'displayName',
  'location',
  'bio',
  'websiteUrl',
  'githubUrl',
  'linkedinUrl',
]

/** The backend's limits (`User.*_MAX_LENGTH`), after trimming. */
export const PROFILE_LIMITS: Record<ProfileField, number> = {
  displayName: 100,
  bio: 500,
  location: 120,
  websiteUrl: 255,
  githubUrl: 255,
  linkedinUrl: 255,
}

/** The form's values: always strings, exactly as typed. */
export type ProfileFormValues = Record<ProfileField, string>
