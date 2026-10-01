import type { UserProfile } from '../features/profile/types'
import { ACCOUNT } from './api'

export function ownProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: ACCOUNT.id,
    username: ACCOUNT.username,
    displayName: ACCOUNT.displayName,
    bio: 'Building tools for small finance teams.',
    location: 'London',
    websiteUrl: 'https://ada.dev',
    githubUrl: null,
    linkedinUrl: null,
    createdAt: '2025-11-04T09:00:00Z',
    ...overrides,
  }
}

export function aliceProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: 7,
    username: 'alice',
    displayName: 'Alice Anders',
    bio: 'Second-time founder.\nI build fintech products.',
    location: 'Berlin, Germany',
    websiteUrl: 'https://alice.example.com/',
    githubUrl: 'https://github.com/alice-anders',
    linkedinUrl: 'https://www.linkedin.com/in/alice-anders',
    createdAt: '2026-01-10T12:00:00Z',
    ...overrides,
  }
}
