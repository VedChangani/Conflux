import type { Account } from '../features/auth/types'
import type { ReportDetail, ReportSummary } from '../features/admin/types'
import { ACCOUNT } from './api'

export const ADMIN: Account = { ...ACCOUNT, role: 'ADMIN' }

export const OPEN_QUEUE = 'GET /admin/reports?status=OPEN&page=0&size=20'

export function reportSummary(overrides: Partial<ReportSummary> = {}): ReportSummary {
  return {
    id: 101,
    targetType: 'LISTING',
    targetId: 5,
    reason: 'SCAM_OR_FRAUD',
    status: 'OPEN',
    createdAt: '2026-03-05T10:00:00Z',
    reviewedAt: null,
    ...overrides,
  }
}

const BOB = { id: 8, username: 'bob', displayName: 'Bob Brown' }
const ALICE = { id: 7, username: 'alice', displayName: 'Alice Anders' }

export function listingReport(overrides: Partial<ReportDetail> = {}): ReportDetail {
  return {
    id: 101,
    targetType: 'LISTING',
    targetId: 5,
    reason: 'SCAM_OR_FRAUD',
    details: 'Asks for payment outside the platform.',
    status: 'OPEN',
    createdAt: '2026-03-05T10:00:00Z',
    reviewedAt: null,
    resolutionNote: null,
    reporter: BOB,
    reviewer: null,
    target: {
      id: 5,
      slug: 'pairwise',
      title: 'Pairwise',
      shortPitch: 'Find a technical co-founder.',
      description: 'Matching for founders.',
      status: 'PUBLISHED',
      owner: ALICE,
    },
    ...overrides,
  } as ReportDetail
}

export function userReport(overrides: Partial<ReportDetail> = {}): ReportDetail {
  return {
    id: 102,
    targetType: 'USER',
    targetId: 7,
    reason: 'HARASSMENT',
    details: null,
    status: 'OPEN',
    createdAt: '2026-03-06T10:00:00Z',
    reviewedAt: null,
    resolutionNote: null,
    reporter: BOB,
    reviewer: null,
    target: {
      id: 7,
      username: 'alice',
      displayName: 'Alice Anders',
      bio: 'Second-time founder.',
      location: 'Berlin, Germany',
      websiteUrl: 'https://alice.example.com/',
      githubUrl: null,
      linkedinUrl: null,
      status: 'ACTIVE',
    },
    ...overrides,
  } as ReportDetail
}

export function messageReport(overrides: Partial<ReportDetail> = {}): ReportDetail {
  return {
    id: 103,
    targetType: 'MESSAGE',
    targetId: 501,
    reason: 'SPAM',
    details: 'Keeps sending links.',
    status: 'OPEN',
    createdAt: '2026-03-07T10:00:00Z',
    reviewedAt: null,
    resolutionNote: null,
    reporter: BOB,
    reviewer: null,
    target: {
      id: 501,
      content: 'Buy followers at example.test!',
      createdAt: '2026-03-04T09:00:00Z',
      sender: ALICE,
      conversationId: 31,
      listing: { id: 5, slug: 'pairwise', title: 'Pairwise' },
    },
    ...overrides,
  } as ReportDetail
}
