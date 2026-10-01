export interface UserProfile {
  id: number
  username: string
  displayName: string
  bio: string | null
  location: string | null
  websiteUrl: string | null
  githubUrl: string | null
  linkedinUrl: string | null
  createdAt: string | null
}

export interface ProfileUpdate {
  displayName: string
  bio: string | null
  location: string | null
  websiteUrl: string | null
  githubUrl: string | null
  linkedinUrl: string | null
}

export type ProfileField = keyof ProfileUpdate

export const PROFILE_FIELDS: readonly ProfileField[] = [
  'displayName',
  'location',
  'bio',
  'websiteUrl',
  'githubUrl',
  'linkedinUrl',
]

export const PROFILE_LIMITS: Record<ProfileField, number> = {
  displayName: 100,
  bio: 500,
  location: 120,
  websiteUrl: 255,
  githubUrl: 255,
  linkedinUrl: 255,
}

export type ProfileFormValues = Record<ProfileField, string>
