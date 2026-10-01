import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../test/api'
import { ADMIN, listingReport, messageReport, OPEN_QUEUE, reportSummary, userReport } from '../test/admin'
import { pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'
import type { ReportDetail } from '../features/admin/types'

const LEAKY_DETAIL = 'org.hibernate.exception.LockAcquisitionException at ReportService.java:142'

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ADMIN), ...handlers })
}

const noContent = () => new Response(null, { status: 204 })
const count = (requests: RecordedRequest[], key: string) =>
  requests.filter((request) => `${request.method} ${request.path}` === key).length

const queue = () => within(screen.getByRole('region', { name: /reports$/ }))
const panel = () => within(screen.getByRole('article'))
const section = (name: string) => within(panel().getByRole('region', { name }))
const dialog = () => within(screen.getByRole('dialog'))

/** Opens report 101 from the default queue, with the given detail handlers. */
async function openListingReport(handlers: Parameters<typeof mockApi>[0] = {}, detail: ReportDetail = listingReport()) {
  const api = signedIn({
    [OPEN_QUEUE]: () => json(pageOf([reportSummary()], { size: 20 })),
    'GET /admin/reports/101': () => json(detail),
    ...handlers,
  })
  const router = renderApp('/admin/reports/101')
  expect(await screen.findByRole('heading', { level: 2, name: 'Scam or fraud' })).toBeTruthy()
  return { ...api, router }
}

describe('Report queue', () => {
  it('shows a placeholder while loading, then each report’s summary fields', async () => {
    const response = deferred<Response>()
    signedIn({ [OPEN_QUEUE]: () => response.promise })
    renderApp('/admin/reports')

    expect(await screen.findByText('Loading reports…')).toBeTruthy()
    expect(document.querySelector('.report-row-skeleton')).toBeTruthy()

    response.resolve(
      json(
        pageOf(
          [
            reportSummary(),
            reportSummary({ id: 99, targetType: 'MESSAGE', targetId: 501, reason: 'SPAM', createdAt: '2026-03-01T08:00:00Z' }),
          ],
          { size: 20 },
        ),
      ),
    )

    const row = await screen.findByRole('link', { name: 'Report #101, Scam or fraud, Listing #5, Open' })
    expect(row.getAttribute('href')).toBe('/admin/reports/101')
    expect(row.textContent).toContain('#101')
    expect(row.textContent).toContain('Listing')
    expect(row.textContent).toContain('#5')
    expect(row.textContent).toContain('Scam or fraud')
    expect(row.textContent).toContain('Open')
    expect(row.querySelector('time')?.getAttribute('datetime')).toBe('2026-03-05T10:00:00Z')
    expect(screen.getByRole('link', { name: 'Report #99, Spam, Message #501, Open' })).toBeTruthy()
    expect(queue().getByRole('heading', { level: 2, name: 'Open reports' })).toBeTruthy()
    expect(queue().getByText('2 reports · Newest first')).toBeTruthy()
    expect(document.title).toBe('Moderation · Conflux')
  })

  it('marks open reports with text and an icon, not only colour', async () => {
    signedIn({
      'GET /admin/reports?status=RESOLVED&page=0&size=20': () =>
        json(pageOf([reportSummary({ status: 'RESOLVED', reviewedAt: '2026-03-06T09:00:00Z' })], { size: 20 })),
      [OPEN_QUEUE]: () => json(pageOf([reportSummary()], { size: 20 })),
    })
    renderApp('/admin/reports')

    const open = await screen.findByRole('link', { name: /Report #101.*Open$/ })
    const openBadge = open.querySelector('.status-badge')
    expect(openBadge?.textContent).toBe('Open')
    expect(openBadge?.querySelector('svg')).toBeTruthy()
    expect(open.getAttribute('data-status')).toBe('open')

    fireEvent.click(queue().getByRole('link', { name: 'Resolved' }))

    const resolved = await screen.findByRole('link', { name: /Report #101.*Resolved$/ })
    expect(resolved.getAttribute('data-status')).toBe('resolved')
    expect(resolved.textContent).toContain('Reviewed')
    expect(resolved.querySelector('.status-badge')?.textContent).toBe('Resolved')
  })

  it('shows a clear queue when there are no open reports', async () => {
    signedIn({ [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })) })
    renderApp('/admin/reports')

    expect(await screen.findByRole('heading', { name: 'Queue clear' })).toBeTruthy()
    expect(screen.getByText('There are no open reports. New reports from members will appear here.')).toBeTruthy()
  })

  it('filters by status through the URL and offers a way back from an empty filter', async () => {
    const { requests } = signedIn({
      [OPEN_QUEUE]: () => json(pageOf([reportSummary()], { size: 20 })),
      'GET /admin/reports?status=DISMISSED&page=0&size=20': () => json(pageOf([], { size: 20 })),
    })
    const router = renderApp('/admin/reports')
    await screen.findByRole('link', { name: /Report #101/ })
    expect(queue().getByRole('link', { name: 'Open' }).getAttribute('aria-current')).toBe('true')

    fireEvent.click(queue().getByRole('link', { name: 'Dismissed' }))

    expect(await screen.findByRole('heading', { name: 'No dismissed reports' })).toBeTruthy()
    expect(router.state.location.search).toBe('?status=DISMISSED')
    expect(queue().getByRole('link', { name: 'Dismissed' }).getAttribute('aria-current')).toBe('true')
    expect(count(requests, 'GET /admin/reports?status=DISMISSED&page=0&size=20')).toBe(1)

    fireEvent.click(screen.getByRole('link', { name: 'Show open reports' }))

    expect(await screen.findByRole('link', { name: /Report #101/ })).toBeTruthy()
    expect(router.state.location.search).toBe('')
  })

  it('pages through the queue with the backend’s zero-based pages and keeps the filter', async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) => reportSummary({ id: 200 - index, status: 'RESOLVED' }))
    const { requests } = signedIn({
      'GET /admin/reports?status=RESOLVED&page=0&size=20': () =>
        json(pageOf(firstPage, { size: 20, totalElements: 45 })),
      'GET /admin/reports?status=RESOLVED&page=1&size=20': () =>
        json(pageOf([reportSummary({ id: 7, status: 'RESOLVED' })], { page: 1, size: 20, totalElements: 45 })),
    })
    const router = renderApp('/admin/reports?status=RESOLVED')

    expect(await screen.findByText('Showing 1–20 of 45 reports · Newest first')).toBeTruthy()
    const pagination = within(screen.getByRole('navigation', { name: 'Pagination' }))
    expect(pagination.getByText('Page 1 of 3')).toBeTruthy()

    fireEvent.click(pagination.getByRole('link', { name: /Next/ }))

    expect(await screen.findByRole('link', { name: /Report #7,/ })).toBeTruthy()
    expect(router.state.location.search).toBe('?status=RESOLVED&page=2')
    expect(count(requests, 'GET /admin/reports?status=RESOLVED&page=1&size=20')).toBe(1)
    expect(document.activeElement).toBe(queue().getByRole('heading', { level: 2, name: 'Resolved reports' }))
  })

  it('changes the page size within the backend’s limits', async () => {
    const { requests } = signedIn({
      [OPEN_QUEUE]: () => json(pageOf([reportSummary()], { size: 20 })),
      'GET /admin/reports?status=OPEN&page=0&size=50': () => json(pageOf([reportSummary()], { size: 50 })),
    })
    const router = renderApp('/admin/reports?page=3')
    const select = await screen.findByLabelText('Page size')
    expect((select as HTMLSelectElement).value).toBe('20')

    fireEvent.change(select, { target: { value: '50' } })

    await waitFor(() => expect(count(requests, 'GET /admin/reports?status=OPEN&page=0&size=50')).toBe(1))
    // A new page size starts from the first page.
    expect(router.state.location.search).toBe('?size=50')
  })

  it('never sends a page size the backend would reject', async () => {
    const { requests } = signedIn({ [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })) })
    renderApp('/admin/reports?size=500')

    expect(await screen.findByRole('heading', { name: 'Queue clear' })).toBeTruthy()
    expect(requests.filter((request) => request.path.startsWith('/admin')).map((request) => request.path)).toEqual([
      '/admin/reports?status=OPEN&page=0&size=20',
    ])
  })

  it('offers the last page when the requested one is past the end', async () => {
    signedIn({
      'GET /admin/reports?status=OPEN&page=8&size=20': () =>
        json(pageOf([], { page: 8, size: 20, totalElements: 30 })),
    })
    renderApp('/admin/reports?page=9')

    expect(await screen.findByRole('heading', { name: 'There is no page 9' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Go to page 2' }).getAttribute('href')).toBe('/admin/reports?page=2')
  })

  it('explains invalid filters (400) without backend wording', async () => {
    signedIn({ 'GET /admin/reports?status=PENDING&page=0&size=20': () => problem(400, LEAKY_DETAIL) })
    renderApp('/admin/reports?status=PENDING')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('These filters aren’t valid')
    expect(within(alert).getByRole('link', { name: 'Show open reports' }).getAttribute('href')).toBe('/admin/reports')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it.each([
    [500, 'Something went wrong on our side. Please try again.'],
    [503, 'Something went wrong on our side. Please try again.'],
    [429, 'Too many requests in a short time. Wait a moment, then try again.'],
  ])('retries after a %i', async (status, message) => {
    const { handlers } = signedIn({ [OPEN_QUEUE]: () => problem(status, LEAKY_DETAIL) })
    renderApp('/admin/reports')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load the report queue')
    expect(alert.textContent).toContain(message)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[OPEN_QUEUE] = () => json(pageOf([reportSummary()], { size: 20 }))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('link', { name: /Report #101/ })).toBeTruthy()
  })

  it('retries after a network failure', async () => {
    const { handlers } = signedIn({
      [OPEN_QUEUE]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderApp('/admin/reports')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Unable to reach the server.')

    handlers[OPEN_QUEUE] = () => json(pageOf([], { size: 20 }))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Queue clear' })).toBeTruthy()
  })

  it('treats a 403 as an access problem, not a queue problem', async () => {
    const { requests } = signedIn({ [OPEN_QUEUE]: () => problem(403, LEAKY_DETAIL) })
    renderApp('/admin/reports')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Administrator access required')
    expect(alert.textContent).not.toContain('report queue')
    expect(within(alert).queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    // The account is read again, in case it lost its admin role.
    await waitFor(() => expect(count(requests, 'GET /auth/me')).toBe(2))
  })

  it('closes the admin area when the re-read account is no longer an admin', async () => {
    let meCalls = 0
    signedIn({
      'GET /auth/me': () => json(++meCalls === 1 ? ADMIN : ACCOUNT),
      [OPEN_QUEUE]: () => problem(403, 'You do not have permission to access this resource.'),
    })
    renderApp('/admin/reports')

    expect(await screen.findByRole('heading', { level: 1, name: 'You don’t have access to this page' })).toBeTruthy()
    expect(within(screen.getByRole('navigation', { name: 'Main' })).queryByRole('link', { name: /Moderation/ })).toBeNull()
  })

  it('sends the user to log in when the session expires (401)', async () => {
    signedIn({ [OPEN_QUEUE]: () => problem(401, 'A valid access token is required.') })
    const router = renderApp('/admin/reports')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/admin/reports' } })
  })
})

describe('Report detail', () => {
  it('opens from the queue, keeps the queue’s filters, and moves focus to the report', async () => {
    const { requests } = signedIn({
      'GET /admin/reports?status=OPEN&page=2&size=20': () =>
        json(pageOf([reportSummary()], { page: 2, size: 20, totalElements: 41 })),
      'GET /admin/reports/101': () => json(listingReport()),
    })
    const router = renderApp('/admin/reports?page=3')
    expect(screen.queryByRole('article')).toBeNull()

    await screen.findByRole('link', { name: /Report #101/ })
    expect(document.querySelector('.admin-workspace')?.getAttribute('data-pane')).toBe('queue')
    expect(screen.getByText('Select a report')).toBeTruthy()

    fireEvent.click(screen.getByRole('link', { name: /Report #101/ }))

    const heading = await screen.findByRole('heading', { level: 2, name: 'Scam or fraud' })
    expect(router.state.location.pathname).toBe('/admin/reports/101')
    expect(router.state.location.search).toBe('?page=3')
    await waitFor(() => expect(document.activeElement).toBe(heading))
    expect(document.querySelector('.admin-workspace')?.getAttribute('data-pane')).toBe('report')
    expect(screen.getByRole('link', { name: /Report #101/ }).getAttribute('aria-current')).toBe('true')
    expect(panel().getByRole('link', { name: /Back to queue/ }).getAttribute('href')).toBe('/admin/reports?page=3')
    expect(count(requests, 'GET /admin/reports/101')).toBe(1)
    expect(document.title).toBe('Report #101 · Moderation · Conflux')
  })

  it('shows everything the backend returned about the report and a listing target', async () => {
    await openListingReport()

    expect(panel().getByText('Listing report ·', { exact: false })).toBeTruthy()
    const report = section('Report')
    expect(report.getByText('Bob Brown')).toBeTruthy()
    expect(report.getByRole('link', { name: '@bob' }).getAttribute('href')).toBe('/users/bob')
    expect(report.getByText('Asks for payment outside the platform.')).toBeTruthy()
    expect(report.getByText('#5')).toBeTruthy()

    const target = section('Reported listing')
    expect(target.getByText('Pairwise')).toBeTruthy()
    expect(target.getByText('/pairwise')).toBeTruthy()
    expect(target.getByText('Find a technical co-founder.')).toBeTruthy()
    expect(target.getByText('Matching for founders.')).toBeTruthy()
    expect(target.getByText('Published')).toBeTruthy()
    expect(target.getByText('Alice Anders')).toBeTruthy()
    expect(target.getByRole('link', { name: /View on the marketplace/ }).getAttribute('href')).toBe('/listings/pairwise')
    expect(target.getByRole('button', { name: 'Suspend listing' })).toBeTruthy()
    expect(target.queryByRole('button', { name: 'Restore listing' })).toBeNull()

    expect(section('Review').getByRole('button', { name: 'Resolve' })).toBeTruthy()
    expect(section('Review').getByRole('button', { name: 'Dismiss' })).toBeTruthy()
  })

  it('shows a reported user, and no fields beyond the response contract', async () => {
    const leaky = { ...userReport(), target: { ...userReport().target, email: 'alice@example.com', passwordHash: 'x' } }
    signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      'GET /admin/reports/102': () => json(leaky),
    })
    renderApp('/admin/reports/102')

    expect(await screen.findByRole('heading', { level: 2, name: 'Harassment' })).toBeTruthy()
    const target = section('Reported user')
    expect(target.getByText('Alice Anders')).toBeTruthy()
    expect(target.getByText('@alice')).toBeTruthy()
    expect(target.getByText('Second-time founder.')).toBeTruthy()
    expect(target.getByText('Berlin, Germany')).toBeTruthy()
    expect(target.getByRole('link', { name: /alice\.example\.com/ }).getAttribute('rel')).toContain('noopener')
    expect(target.getByText('Active')).toBeTruthy()
    expect(target.getByRole('button', { name: 'Suspend account' })).toBeTruthy()
    expect(section('Report').getByText('No details given.')).toBeTruthy()

    const text = document.body.textContent ?? ''
    expect(text).not.toContain('alice@example.com')
    expect(text).not.toContain(ADMIN.email)
    expect(text).not.toContain('passwordHash')
  })

  it('shows a reported message with its sender and conversation, without moderation actions', async () => {
    signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      'GET /admin/reports/103': () => json(messageReport()),
    })
    renderApp('/admin/reports/103')

    expect(await screen.findByRole('heading', { level: 2, name: 'Spam' })).toBeTruthy()
    const target = section('Reported message')
    expect(target.getByText('Buy followers at example.test!')).toBeTruthy()
    expect(target.getByText('Alice Anders')).toBeTruthy()
    expect(target.getByText('#31')).toBeTruthy()
    expect(target.getByText('Pairwise', { exact: false })).toBeTruthy()
    expect(target.queryByRole('button', { name: /Suspend|Restore/ })).toBeNull()
  })

  it('explains a target that no longer exists', async () => {
    signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      'GET /admin/reports/101': () => json(listingReport({ target: null })),
    })
    renderApp('/admin/reports/101')

    expect(await screen.findByText('This listing no longer exists. The report is kept for the record.')).toBeTruthy()
    expect(section('Reported listing').queryByRole('button')).toBeNull()
  })

  it('shows the review of a closed report instead of decision buttons', async () => {
    signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      'GET /admin/reports/101': () =>
        json(
          listingReport({
            status: 'DISMISSED',
            reviewedAt: '2026-03-06T12:00:00Z',
            reviewer: { id: 9, username: 'mod', displayName: 'Mo Derator' },
            resolutionNote: 'Payment was on-platform.',
          }),
        ),
    })
    renderApp('/admin/reports/101')

    const review = within(await screen.findByRole('region', { name: 'Review' }))
    expect(review.getByText('Mo Derator')).toBeTruthy()
    expect(review.getByText('Payment was on-platform.')).toBeTruthy()
    expect(review.getByText('Dismissed')).toBeTruthy()
    expect(review.queryByRole('button', { name: 'Resolve' })).toBeNull()
    expect(review.queryByRole('button', { name: 'Dismiss' })).toBeNull()
  })

  it.each([
    ['a missing report (404)', '/admin/reports/404', () => problem(404, LEAKY_DETAIL)],
    ['a malformed id', '/admin/reports/abc', undefined],
  ])('shows not found for %s', async (_, path, handler) => {
    const { requests } = signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      ...(handler && { 'GET /admin/reports/404': handler }),
    })
    renderApp(path)

    expect(await screen.findByRole('heading', { name: 'Report not found' })).toBeTruthy()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    if (!handler) {
      expect(requests.some((request) => request.path.startsWith('/admin/reports/'))).toBe(false)
    }
  })

  it('retries a report that failed to load', async () => {
    const { handlers } = signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      'GET /admin/reports/101': () => problem(502, LEAKY_DETAIL),
    })
    renderApp('/admin/reports/101')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load this report')

    handlers['GET /admin/reports/101'] = () => json(listingReport())
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Scam or fraud' })).toBeTruthy()
  })
})

describe('Resolving and dismissing', () => {
  it('confirms, sends the trimmed note, blocks repeats, then shows the result and reloads the queue', async () => {
    const response = deferred<Response>()
    const { requests, handlers } = await openListingReport({ 'POST /admin/reports/101/resolve': () => response.promise })
    const resolveButton = section('Review').getByRole('button', { name: 'Resolve' })

    fireEvent.click(resolveButton)

    expect(dialog().getByRole('heading', { name: 'Resolve report #101?' })).toBeTruthy()
    const note = dialog().getByLabelText('Resolution note (optional)')
    expect(document.activeElement).toBe(note)
    fireEvent.change(note, { target: { value: '  Listing suspended for off-platform payments.  ' } })
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    const running = dialog().getByRole('button', { name: 'Resolving…' })
    expect((running as HTMLButtonElement).disabled).toBe(true)
    expect((dialog().getByRole('button', { name: 'Cancel' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.submit(running.closest('form') as HTMLFormElement)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(count(requests, 'POST /admin/reports/101/resolve')).toBe(1)

    handlers[OPEN_QUEUE] = () => json(pageOf([], { size: 20 }))
    response.resolve(
      json(
        listingReport({
          status: 'RESOLVED',
          reviewedAt: '2026-03-06T12:00:00Z',
          reviewer: { id: ADMIN.id, username: ADMIN.username, displayName: ADMIN.displayName },
          resolutionNote: 'Listing suspended for off-platform payments.',
        }),
      ),
    )

    expect(await panel().findByText('Report resolved.')).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(requests.find((request) => request.path === '/admin/reports/101/resolve')?.body).toEqual({
      resolutionNote: 'Listing suspended for off-platform payments.',
    })
    const review = section('Review')
    expect(review.getByText('Resolved')).toBeTruthy()
    expect(review.getByText('Listing suspended for off-platform payments.')).toBeTruthy()
    expect(review.getByText('You')).toBeTruthy()
    expect(review.queryByRole('button', { name: 'Resolve' })).toBeNull()
    // The queue reloads (the report has left the OPEN filter); the detail needs no second read.
    expect(await screen.findByRole('heading', { name: 'Queue clear' })).toBeTruthy()
    expect(count(requests, OPEN_QUEUE)).toBe(2)
    expect(count(requests, 'GET /admin/reports/101')).toBe(1)
    expect(document.activeElement).toBe(panel().getByText('Report resolved.'))
  })

  it('dismisses without a note as null', async () => {
    const { requests } = await openListingReport({
      'POST /admin/reports/101/dismiss': () => json(listingReport({ status: 'DISMISSED', reviewedAt: '2026-03-06T12:00:00Z' })),
    })

    fireEvent.click(section('Review').getByRole('button', { name: 'Dismiss' }))
    fireEvent.change(dialog().getByLabelText('Resolution note (optional)'), { target: { value: '   ' } })
    fireEvent.click(dialog().getByRole('button', { name: 'Dismiss report' }))

    expect(await panel().findByText('Report dismissed.')).toBeTruthy()
    expect(requests.find((request) => request.path === '/admin/reports/101/dismiss')?.body).toEqual({ resolutionNote: null })
    expect(section('Review').getByText('No note.')).toBeTruthy()
  })

  it('can be cancelled without sending anything, returning focus to the button', async () => {
    const { requests } = await openListingReport()
    const button = section('Review').getByRole('button', { name: 'Dismiss' })
    button.focus()

    fireEvent.click(button)
    fireEvent.click(dialog().getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(button)
    expect(requests.some((request) => request.method === 'POST')).toBe(false)
  })

  it('checks the note length before sending', async () => {
    const { requests } = await openListingReport()

    fireEvent.click(section('Review').getByRole('button', { name: 'Resolve' }))
    const note = dialog().getByLabelText('Resolution note (optional)')
    fireEvent.change(note, { target: { value: 'n'.repeat(1001) } })
    expect(dialog().getByText('1 characters over the 1,000-character limit.')).toBeTruthy()
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    expect(dialog().getByText('The note can be at most 1,000 characters (1 too many).')).toBeTruthy()
    expect(note.getAttribute('aria-invalid')).toBe('true')
    expect(requests.some((request) => request.method === 'POST')).toBe(false)
  })

  it('shows a backend validation error on the note field', async () => {
    await openListingReport({
      'POST /admin/reports/101/resolve': () =>
        problem(400, LEAKY_DETAIL, [{ field: 'resolutionNote', message: 'size must be between 0 and 1000' }]),
    })

    fireEvent.click(section('Review').getByRole('button', { name: 'Resolve' }))
    fireEvent.change(dialog().getByLabelText('Resolution note (optional)'), { target: { value: 'Fine' } })
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    expect(await dialog().findByText('This note wasn’t accepted. Shorten or rephrase it.')).toBeTruthy()
    expect(dialog().getByLabelText('Resolution note (optional)').getAttribute('aria-invalid')).toBe('true')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('explains a report decided elsewhere (409) and shows its real state', async () => {
    const { handlers, requests } = await openListingReport({
      'POST /admin/reports/101/resolve': () => problem(409, LEAKY_DETAIL),
    })
    handlers['GET /admin/reports/101'] = () =>
      json(listingReport({ status: 'DISMISSED', reviewer: { id: 9, username: 'mod', displayName: 'Mo Derator' } }))

    fireEvent.click(section('Review').getByRole('button', { name: 'Resolve' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    const alert = await dialog().findByRole('alert')
    expect(alert.textContent).toContain('This report has already been reviewed')
    expect(document.activeElement).toBe(dialog().getByRole('button', { name: 'Close' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Close' }))

    expect(await section('Review').findByText('Mo Derator')).toBeTruthy()
    expect(count(requests, 'GET /admin/reports/101')).toBe(2)
    expect(count(requests, OPEN_QUEUE)).toBe(2)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('explains a report that no longer exists (404)', async () => {
    const { handlers } = await openListingReport({ 'POST /admin/reports/101/dismiss': () => problem(404, LEAKY_DETAIL) })
    handlers['GET /admin/reports/101'] = () => problem(404, LEAKY_DETAIL)

    fireEvent.click(section('Review').getByRole('button', { name: 'Dismiss' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Dismiss report' }))

    expect((await dialog().findByRole('alert')).textContent).toContain('This report no longer exists.')
    fireEvent.click(dialog().getByRole('button', { name: 'Close' }))
    expect(await screen.findByRole('heading', { name: 'Report not found' })).toBeTruthy()
  })

  it('keeps the form after a rate limit (429) so it can be sent again', async () => {
    const { handlers, requests } = await openListingReport({
      'POST /admin/reports/101/resolve': () => problem(429, 'Too many requests. Please try again later.'),
    })

    fireEvent.click(section('Review').getByRole('button', { name: 'Resolve' }))
    fireEvent.change(dialog().getByLabelText('Resolution note (optional)'), { target: { value: 'Valid.' } })
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    const alert = await dialog().findByRole('alert')
    expect(alert.textContent).toContain('Wait a moment, then try again.')
    expect((dialog().getByLabelText('Resolution note (optional)') as HTMLTextAreaElement).value).toBe('Valid.')

    handlers['POST /admin/reports/101/resolve'] = () => json(listingReport({ status: 'RESOLVED' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    expect(await panel().findByText('Report resolved.')).toBeTruthy()
    expect(count(requests, 'POST /admin/reports/101/resolve')).toBe(2)
  })

  it.each([
    ['a server error', () => problem(500, LEAKY_DETAIL), 'Something went wrong, so this may not have been saved.'],
    [
      'a network failure',
      () => {
        throw new TypeError('Failed to fetch')
      },
      'Unable to reach the server.',
    ],
  ])('keeps the form after %s', async (_, handler, message) => {
    await openListingReport({ 'POST /admin/reports/101/dismiss': handler })

    fireEvent.click(section('Review').getByRole('button', { name: 'Dismiss' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Dismiss report' }))

    expect((await dialog().findByRole('alert')).textContent).toContain(message)
    expect(dialog().getByRole('button', { name: 'Dismiss report' })).toBeTruthy()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('reports a 403 as lost admin access, not a report error, and re-reads the account', async () => {
    const { requests } = await openListingReport({
      'POST /admin/reports/101/resolve': () => problem(403, 'You do not have permission to access this resource.'),
    })

    fireEvent.click(section('Review').getByRole('button', { name: 'Resolve' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    const alert = await dialog().findByRole('alert')
    expect(alert.textContent).toContain('Administrator access required')
    expect(alert.textContent).toContain('Nothing was changed.')
    expect(alert.textContent).not.toContain('You do not have permission')
    await waitFor(() => expect(count(requests, 'GET /auth/me')).toBe(2))
    // The report itself is untouched.
    expect(section('Review').getByRole('button', { name: 'Resolve' })).toBeTruthy()
  })

  it('closes the admin area after a 403 when the account is no longer an admin', async () => {
    const { handlers } = await openListingReport({
      'POST /admin/reports/101/dismiss': () => problem(403, 'You do not have permission to access this resource.'),
    })
    handlers['GET /auth/me'] = () => json(ACCOUNT)

    fireEvent.click(section('Review').getByRole('button', { name: 'Dismiss' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Dismiss report' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'You don’t have access to this page' })).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('sends the user to log in when the session expires (401)', async () => {
    const { router } = await openListingReport({
      'POST /admin/reports/101/resolve': () => problem(401, 'A valid access token is required.'),
    })

    fireEvent.click(section('Review').getByRole('button', { name: 'Resolve' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Resolve report' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/admin/reports/101' } })
  })
})

describe('User moderation', () => {
  function openUserReport(handlers: Parameters<typeof mockApi>[0], detail = userReport()) {
    const api = signedIn({
      [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })),
      'GET /admin/reports/102': () => json(detail),
      ...handlers,
    })
    renderApp('/admin/reports/102')
    return api
  }

  it('suspends after confirmation, then reads the report again to show the new state', async () => {
    const suspended = userReport({ target: { ...(userReport().target as object), status: 'SUSPENDED' } } as Partial<ReportDetail>)
    const response = deferred<Response>()
    const { handlers, requests } = openUserReport({ 'POST /admin/users/7/suspend': () => response.promise })
    const button = await screen.findByRole('button', { name: 'Suspend account' })

    fireEvent.click(button)
    expect(dialog().getByRole('heading', { name: 'Suspend @alice?' })).toBeTruthy()
    expect(dialog().queryByLabelText(/note/)).toBeNull()
    // The safe choice comes first.
    expect(document.activeElement).toBe(dialog().getByRole('button', { name: 'Cancel' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend account' }))
    expect((dialog().getByRole('button', { name: 'Suspending…' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(dialog().getByRole('button', { name: 'Suspending…' }))
    expect(count(requests, 'POST /admin/users/7/suspend')).toBe(1)

    handlers['GET /admin/reports/102'] = () => json(suspended)
    response.resolve(noContent())

    expect(await panel().findByText('Account suspended.')).toBeTruthy()
    const target = section('Reported user')
    expect(await target.findByRole('button', { name: 'Restore account' })).toBeTruthy()
    expect(target.getByText('Suspended')).toBeTruthy()
    expect(target.queryByRole('button', { name: 'Suspend account' })).toBeNull()
    expect(count(requests, 'GET /admin/reports/102')).toBe(2)
    expect(requests.find((request) => request.path === '/admin/users/7/suspend')?.body).toBeUndefined()
    // Suspending doesn't decide the report.
    expect(section('Review').getByRole('button', { name: 'Resolve' })).toBeTruthy()
  })

  it('restores a suspended account', async () => {
    const suspended = userReport({ target: { ...(userReport().target as object), status: 'SUSPENDED' } } as Partial<ReportDetail>)
    const { handlers, requests } = openUserReport({ 'POST /admin/users/7/restore': () => noContent() }, suspended)

    fireEvent.click(await screen.findByRole('button', { name: 'Restore account' }))
    expect(dialog().getByRole('heading', { name: 'Restore @alice?' })).toBeTruthy()
    handlers['GET /admin/reports/102'] = () => json(userReport())
    fireEvent.click(dialog().getByRole('button', { name: 'Restore account' }))

    expect(await panel().findByText('Account restored.')).toBeTruthy()
    expect(await section('Reported user').findByRole('button', { name: 'Suspend account' })).toBeTruthy()
    expect(count(requests, 'POST /admin/users/7/restore')).toBe(1)
  })

  it('treats a repeated (idempotent) action as a normal success', async () => {
    // Someone else suspended the account first: the backend still answers 204.
    const suspended = userReport({ target: { ...(userReport().target as object), status: 'SUSPENDED' } } as Partial<ReportDetail>)
    const { handlers } = openUserReport({ 'POST /admin/users/7/suspend': () => noContent() })

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' }))
    handlers['GET /admin/reports/102'] = () => json(suspended)
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend account' }))

    expect(await panel().findByText('Account suspended.')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(await section('Reported user').findByRole('button', { name: 'Restore account' })).toBeTruthy()
  })

  it('shows the backend’s refusal to suspend your own account (409)', async () => {
    const self = userReport({
      targetId: ADMIN.id,
      target: { ...(userReport().target as object), id: ADMIN.id, username: 'ada', displayName: 'Ada Lovelace' },
    } as Partial<ReportDetail>)
    const { requests } = openUserReport(
      { [`POST /admin/users/${ADMIN.id}/suspend`]: () => problem(409, LEAKY_DETAIL) },
      { ...self, id: 102 },
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' }))
    expect(section('Reported user').getByText('You')).toBeTruthy()
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend account' }))

    expect((await dialog().findByRole('alert')).textContent).toContain('You can’t suspend your own account.')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    // Nothing changed, so nothing is read again.
    expect(count(requests, 'GET /admin/reports/102')).toBe(1)
  })

  it('explains an account that no longer exists (404) and reads the report again', async () => {
    const { handlers, requests } = openUserReport({ 'POST /admin/users/7/suspend': () => problem(404, LEAKY_DETAIL) })

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' }))
    handlers['GET /admin/reports/102'] = () => json(userReport({ target: null }))
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend account' }))

    expect((await dialog().findByRole('alert')).textContent).toContain('This account no longer exists.')
    fireEvent.click(dialog().getByRole('button', { name: 'Close' }))
    expect(await screen.findByText('This user no longer exists. The report is kept for the record.')).toBeTruthy()
    expect(count(requests, 'GET /admin/reports/102')).toBe(2)
  })

  it('reports a 403 as lost admin access, not as a problem with the account', async () => {
    openUserReport({ 'POST /admin/users/7/suspend': () => problem(403, 'This account is suspended.') })

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend account' }))

    const alert = await dialog().findByRole('alert')
    expect(alert.textContent).toContain('Administrator access required')
    expect(alert.textContent).not.toContain('This account is suspended.')
  })

  it('keeps the dialog open after a rate limit (429)', async () => {
    openUserReport({ 'POST /admin/users/7/suspend': () => problem(429, 'Too many requests. Please try again later.') })

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' }))
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend account' }))

    expect((await dialog().findByRole('alert')).textContent).toContain(
      'You’ve made a lot of moderation changes in a short time.',
    )
    expect((dialog().getByRole('button', { name: 'Suspend account' }) as HTMLButtonElement).disabled).toBe(false)
  })
})

describe('Listing moderation', () => {
  const withListingStatus = (status: string) =>
    listingReport({ target: { ...(listingReport().target as object), status } } as Partial<ReportDetail>)

  it('suspends a published listing, then shows it as suspended with a restore action', async () => {
    const { handlers, requests } = await openListingReport({ 'POST /admin/listings/5/suspend': () => noContent() })

    fireEvent.click(section('Reported listing').getByRole('button', { name: 'Suspend listing' }))
    expect(dialog().getByRole('heading', { name: 'Suspend “Pairwise”?' })).toBeTruthy()
    handlers['GET /admin/reports/101'] = () => json(withListingStatus('SUSPENDED'))
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend listing' }))

    expect(await panel().findByText('Listing suspended.')).toBeTruthy()
    const target = section('Reported listing')
    expect(await target.findByRole('button', { name: 'Restore listing' })).toBeTruthy()
    expect(target.getByText('Suspended')).toBeTruthy()
    // A suspended listing isn't on the marketplace, so there is no link to it.
    expect(target.queryByRole('link', { name: /View on the marketplace/ })).toBeNull()
    expect(count(requests, 'POST /admin/listings/5/suspend')).toBe(1)
    expect(count(requests, 'GET /admin/reports/101')).toBe(2)
  })

  it('restores a suspended listing', async () => {
    const { handlers } = await openListingReport(
      { 'POST /admin/listings/5/restore': () => noContent() },
      withListingStatus('SUSPENDED'),
    )

    fireEvent.click(section('Reported listing').getByRole('button', { name: 'Restore listing' }))
    handlers['GET /admin/reports/101'] = () => json(listingReport())
    fireEvent.click(dialog().getByRole('button', { name: 'Restore listing' }))

    expect(await panel().findByText('Listing restored.')).toBeTruthy()
    expect(await section('Reported listing').findByRole('button', { name: 'Suspend listing' })).toBeTruthy()
  })

  it.each(['DRAFT', 'ARCHIVED'])('offers no suspend or restore for a %s listing', async (status) => {
    await openListingReport({}, withListingStatus(status))

    const target = section('Reported listing')
    expect(target.getByText(status === 'DRAFT' ? 'Draft' : 'Archived')).toBeTruthy()
    expect(target.queryByRole('button', { name: /Suspend|Restore/ })).toBeNull()
    expect(target.getByText(/can’t be suspended or restored/)).toBeTruthy()
  })

  it('explains a lifecycle conflict (409) and shows the listing’s real state', async () => {
    const { handlers } = await openListingReport({ 'POST /admin/listings/5/suspend': () => problem(409, LEAKY_DETAIL) })

    fireEvent.click(section('Reported listing').getByRole('button', { name: 'Suspend listing' }))
    // The owner archived it meanwhile.
    handlers['GET /admin/reports/101'] = () => json(withListingStatus('ARCHIVED'))
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend listing' }))

    expect((await dialog().findByRole('alert')).textContent).toContain('Only published listings can be suspended')
    fireEvent.click(dialog().getByRole('button', { name: 'Close' }))
    expect(await section('Reported listing').findByText('Archived')).toBeTruthy()
    expect(section('Reported listing').queryByRole('button', { name: /Suspend|Restore/ })).toBeNull()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('keeps the last state and offers a retry when reading the report again fails', async () => {
    const { handlers } = await openListingReport({ 'POST /admin/listings/5/suspend': () => noContent() })

    fireEvent.click(section('Reported listing').getByRole('button', { name: 'Suspend listing' }))
    handlers['GET /admin/reports/101'] = () => problem(500, LEAKY_DETAIL)
    fireEvent.click(dialog().getByRole('button', { name: 'Suspend listing' }))

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t refresh this report')
    expect(section('Reported listing').getByText('Pairwise')).toBeTruthy()

    handlers['GET /admin/reports/101'] = () => json(withListingStatus('SUSPENDED'))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await section('Reported listing').findByRole('button', { name: 'Restore listing' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('Moderation layout', () => {
  it('has one h1, labelled regions and a keyboard-reachable dialog trigger', async () => {
    await openListingReport()

    expect(screen.getAllByRole('heading', { level: 1 }).map((heading) => heading.textContent)).toEqual(['Moderation'])
    expect(screen.getByRole('navigation', { name: 'Filter reports by status' })).toBeTruthy()
    expect(screen.getByRole('region', { name: 'Open reports' })).toBeTruthy()
    for (const name of ['Report', 'Reported listing', 'Review']) {
      expect(panel().getByRole('region', { name })).toBeTruthy()
    }
    const resolve = section('Review').getByRole('button', { name: 'Resolve' })
    expect(resolve.getAttribute('aria-haspopup')).toBe('dialog')

    fireEvent.click(resolve)
    expect(screen.getByRole('dialog').getAttribute('aria-modal')).toBe('true')
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
