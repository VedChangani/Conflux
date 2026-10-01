import { describe, expect, it } from 'vitest'
import { ApiError } from '../../services/apiClient'
import { reportErrorOf } from './reportErrors'
import {
  isReportableTarget,
  isReportReason,
  serverReportErrors,
  toReportRequest,
  validateReport,
} from './reportValidation'
import type { ReportTarget } from './types'

const LISTING: ReportTarget = { type: 'LISTING', id: 1, description: 'Listing “Ledgerly”' }

describe('report targets', () => {
  it.each([
    [{ type: 'USER', id: 7 }, true],
    [{ type: 'LISTING', id: 1 }, true],
    [{ type: 'MESSAGE', id: 501 }, true],
    [{ type: 'CONVERSATION', id: 1 }, false],
    [{ type: 'user', id: 1 }, false],
    [{ type: 'USER', id: 0 }, false],
    [{ type: 'USER', id: -4 }, false],
    [{ type: 'USER', id: 1.5 }, false],
    [{ type: 'USER', id: Number.MAX_SAFE_INTEGER + 1 }, false],
    [{ type: 'USER', id: Number.NaN }, false],
  ])('%j is reportable: %s', (target, expected) => {
    expect(isReportableTarget({ description: 'x', ...target } as ReportTarget)).toBe(expected)
  })

  it('accepts only the backend’s reasons', () => {
    expect(isReportReason('SCAM_OR_FRAUD')).toBe(true)
    expect(isReportReason('scam_or_fraud')).toBe(false)
    expect(isReportReason('ABUSE')).toBe(false)
    expect(isReportReason('')).toBe(false)
  })
})

describe('report validation', () => {
  it('requires a reason', () => {
    expect(validateReport({ reason: '', details: '' })).toEqual({ reason: 'Choose a reason.' })
    expect(validateReport({ reason: 'SPAM', details: '' })).toEqual({})
  })

  it('limits details to 1,000 characters after trimming', () => {
    expect(validateReport({ reason: 'SPAM', details: `  ${'d'.repeat(1000)}  ` })).toEqual({})
    expect(validateReport({ reason: 'SPAM', details: 'd'.repeat(1003) })).toEqual({
      details: 'Details can be at most 1,000 characters (3 too many).',
    })
  })
})

describe('the POST body', () => {
  it('has exactly the four fields, with details trimmed', () => {
    const request = toReportRequest(LISTING, 'MISLEADING_INFORMATION', '  Revenue claims look made up.  ')

    expect(request).toEqual({
      targetType: 'LISTING',
      targetId: 1,
      reason: 'MISLEADING_INFORMATION',
      details: 'Revenue claims look made up.',
    })
    expect(Object.keys(request).sort()).toEqual(['details', 'reason', 'targetId', 'targetType'])
  })

  it.each(['', '   ', '\n\t '])('sends blank details (%j) as null', (details) => {
    expect(toReportRequest(LISTING, 'SPAM', details).details).toBeNull()
  })

  it('never carries reporter, status or review fields, whatever the target object holds', () => {
    const tainted = { ...LISTING, reporterId: 1, status: 'RESOLVED', reviewer: 2, reviewedAt: 'x', resolutionNote: 'y' }

    const request = toReportRequest(tainted, 'SPAM', '')

    for (const key of ['reporterId', 'reporter', 'status', 'reviewer', 'reviewerId', 'reviewedAt', 'resolutionNote', 'description']) {
      expect(request).not.toHaveProperty(key)
    }
  })
})

describe('backend errors', () => {
  const problem = (status: number, errors?: { field: string; message: string }[]) =>
    new ApiError(status, { status, detail: 'Raw backend detail', errors })

  it('maps 400 field errors to the dialog’s own wording', () => {
    const error = problem(400, [
      { field: 'details', message: 'size must be between 0 and 1000' },
      { field: 'reason', message: 'must not be null' },
    ])

    expect(serverReportErrors(error, { reason: 'SPAM', details: 'short' })).toEqual({
      reason: 'Choose a reason.',
      details: 'These details weren’t accepted. Shorten or rephrase them.',
    })
  })

  it.each([
    [403, 'Your account is suspended, so you can’t submit reports.', true, 'error'],
    [404, 'This listing isn’t available any more, so it can’t be reported.', true, 'error'],
    [409, 'You have already reported this.', true, 'info'],
    [429, 'You’ve submitted several reports recently. Please wait a while before sending another.', false, 'error'],
    [500, 'Your report wasn’t sent because something went wrong. Please try again.', false, 'error'],
    [0, 'Unable to reach the server. Your report wasn’t sent; check your connection and try again.', false, 'error'],
  ] as const)('maps %i without backend wording', (status, message, final, tone) => {
    const result = reportErrorOf(problem(status), 'LISTING', false)

    expect(result).toEqual({ message, final, tone })
    expect(result.message).not.toContain('Raw backend detail')
  })

  it('names the target in a 404', () => {
    expect(reportErrorOf(problem(404), 'USER', false).message).toBe(
      'This profile isn’t available any more, so it can’t be reported.',
    )
    expect(reportErrorOf(problem(404), 'MESSAGE', false).message).toBe(
      'This message isn’t available any more, so it can’t be reported.',
    )
  })

  it('treats a 400 about the target itself as final', () => {
    expect(reportErrorOf(problem(400, [{ field: 'targetId', message: 'must be greater than 0' }]), 'USER', false)).toEqual({
      message: 'This profile can’t be reported.',
      final: true,
      tone: 'error',
    })
  })
})
