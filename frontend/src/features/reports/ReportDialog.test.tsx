import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../../test/api'
import { listingDetail } from '../../test/listings'
import { renderApp } from '../../test/renderApp'

const DETAIL = 'GET /listings/ledgerly'
const REPORT = 'POST /reports'
const LEAKY_DETAIL = 'ReportService: duplicate key reporter_id=42 target=LISTING:1'

const created = (body: Record<string, unknown> = {}) =>
  json({ id: 900, targetType: 'LISTING', targetId: 1, reason: 'SPAM', status: 'OPEN', createdAt: '2026-03-05T10:00:00Z', ...body }, 201)
const reports = (requests: RecordedRequest[]) => requests.filter((request) => `${request.method} ${request.path}` === REPORT)
const dialog = () => screen.getByRole('dialog', { name: 'Report listing' })
const inDialog = () => within(dialog())
const reportButton = () => screen.getByRole('button', { name: 'Report listing' })

async function openReport(handlers: Parameters<typeof mockApi>[0] = {}) {
  setAccessToken('stored-token')
  const api = mockApi({ 'GET /auth/me': () => json(ACCOUNT), [DETAIL]: () => json(listingDetail()), ...handlers })
  const router = renderApp('/listings/ledgerly')
  fireEvent.click(await screen.findByRole('button', { name: 'Report listing' }))
  await screen.findByRole('dialog', { name: 'Report listing' })
  return { ...api, router }
}

function choose(label: string) {
  fireEvent.click(inDialog().getByRole('radio', { name: new RegExp(`^${label}`) }))
}

function submit() {
  fireEvent.click(inDialog().getByRole('button', { name: 'Submit report' }))
}

describe('Opening the report dialog', () => {
  it('opens a labelled modal dialog over the page, with focus inside it', async () => {
    const { router } = await openReport()

    const panel = dialog()
    expect(panel.getAttribute('aria-modal')).toBe('true')
    expect(reportButton().getAttribute('aria-haspopup')).toBe('dialog')
    expect(document.querySelector('.app-shell')?.closest('[inert]')).toBeTruthy()
    expect(router.state.location.pathname).toBe('/listings/ledgerly')
    expect(document.body.style.overflow).toBe('hidden')

    expect(inDialog().getByText('Listing “Ledgerly”')).toBeTruthy()
    expect(inDialog().getByRole('group', { name: 'Why are you reporting this?' })).toBeTruthy()
    expect(inDialog().getAllByRole('radio').map((radio) => radio.getAttribute('value'))).toEqual([
      'SPAM',
      'SCAM_OR_FRAUD',
      'HARASSMENT',
      'INAPPROPRIATE_CONTENT',
      'MISLEADING_INFORMATION',
      'OTHER',
    ])
    expect(inDialog().getAllByRole('radio').every((radio) => !(radio as HTMLInputElement).checked)).toBe(true)
    expect(inDialog().getByLabelText('Details (optional)').tagName).toBe('TEXTAREA')
    expect(inDialog().getByText(/1,000 characters left/)).toBeTruthy()
    expect(document.activeElement).toBe(inDialog().getByRole('radio', { name: /^Spam/ }))
  })

  it('closes with Escape and returns focus to the Report button', async () => {
    await openReport()

    fireEvent.keyDown(dialog(), { key: 'Escape' })

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(reportButton())
    expect(document.querySelector('[inert]')).toBeNull()
    expect(document.body.style.overflow).toBe('')
  })

  it('closes with Cancel, the close button and the backdrop, sending nothing', async () => {
    const { requests } = await openReport()

    fireEvent.click(inDialog().getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(reportButton())

    fireEvent.click(reportButton())
    fireEvent.click(inDialog().getByRole('button', { name: 'Close dialog' }))
    expect(screen.queryByRole('dialog')).toBeNull()

    fireEvent.click(reportButton())
    fireEvent.mouseDown(document.querySelector('.dialog-backdrop') as HTMLElement)
    expect(screen.queryByRole('dialog')).toBeNull()

    expect(reports(requests)).toHaveLength(0)
  })

  it('keeps Tab inside the dialog', async () => {
    await openReport()
    const submitButton = inDialog().getByRole('button', { name: 'Submit report' })
    const close = inDialog().getByRole('button', { name: 'Close dialog' })

    submitButton.focus()
    fireEvent.keyDown(submitButton, { key: 'Tab' })
    expect(document.activeElement).toBe(close)

    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(submitButton)
  })

  it('sends anonymous visitors to log in instead, and back to the listing', async () => {
    mockApi({ [DETAIL]: () => json(listingDetail()) })
    const router = renderApp('/listings/ledgerly')

    const button = await screen.findByRole('button', { name: 'Report listing' })
    expect(button.getAttribute('aria-haspopup')).toBeNull()
    fireEvent.click(button)

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings/ledgerly' } })
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('Filling in a report', () => {
  it('requires a reason before sending anything', async () => {
    const { requests } = await openReport()

    submit()

    const error = inDialog().getByText('Choose a reason.')
    expect(error.getAttribute('role')).toBe('alert')
    expect(inDialog().getByRole('group', { name: 'Why are you reporting this?' }).getAttribute('aria-describedby')).toBe(error.id)
    expect(inDialog().getByRole('radio', { name: /^Spam/ }).getAttribute('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(inDialog().getByRole('radio', { name: /^Spam/ }))
    expect(reports(requests)).toHaveLength(0)

    choose('Harassment')
    expect(inDialog().queryByText('Choose a reason.')).toBeNull()
    expect((inDialog().getByRole('radio', { name: /^Harassment/ }) as HTMLInputElement).checked).toBe(true)
  })

  it('counts details and refuses more than 1,000 characters', async () => {
    const { requests } = await openReport()
    const details = inDialog().getByLabelText('Details (optional)')
    choose('Spam')

    fireEvent.change(details, { target: { value: `  ${'d'.repeat(990)}  ` } })
    expect(inDialog().getByText(/10 characters left/)).toBeTruthy()

    fireEvent.change(details, { target: { value: 'd'.repeat(1004) } })
    expect(inDialog().getByText('4 characters over the 1,000-character limit.')).toBeTruthy()
    submit()

    expect(inDialog().getByText('Details can be at most 1,000 characters (4 too many).')).toBeTruthy()
    expect(details.getAttribute('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(details)
    expect(reports(requests)).toHaveLength(0)
  })
})

describe('Submitting a report', () => {
  it('sends exactly the report fields, then confirms without reloading the page', async () => {
    const response = deferred<Response>()
    const { requests } = await openReport({ [REPORT]: () => response.promise })

    choose('Misleading information')
    fireEvent.change(inDialog().getByLabelText('Details (optional)'), { target: { value: '  Revenue claims look invented.  ' } })
    submit()
    fireEvent.click(inDialog().getByRole('button', { name: 'Submitting…' }))
    fireEvent.submit(dialog().querySelector('form') as HTMLFormElement)
    fireEvent.keyDown(dialog(), { key: 'Escape' })

    const submitting = inDialog().getByRole('button', { name: 'Submitting…' })
    expect(submitting).toHaveProperty('disabled', true)
    expect(submitting.getAttribute('aria-busy')).toBe('true')
    expect(inDialog().getByRole('button', { name: 'Cancel' })).toHaveProperty('disabled', true)
    expect(inDialog().getByRole('button', { name: 'Close dialog' })).toHaveProperty('disabled', true)
    expect(reports(requests)).toHaveLength(1)

    response.resolve(created({ reason: 'MISLEADING_INFORMATION' }))

    const confirmation = await inDialog().findByRole('status')
    expect(confirmation.textContent).toContain('Report submitted.')
    const done = inDialog().getByRole('button', { name: 'Done' })
    await waitFor(() => expect(document.activeElement).toBe(done))

    const [report] = reports(requests)
    expect(report.body).toEqual({
      targetType: 'LISTING',
      targetId: 1,
      reason: 'MISLEADING_INFORMATION',
      details: 'Revenue claims look invented.',
    })
    expect(report.headers.get('Authorization')).toBe('Bearer stored-token')
    expect(document.body.textContent).not.toContain('OPEN')
    expect(document.body.textContent).not.toContain('900')

    fireEvent.click(done)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(reportButton())
    expect(requests.filter((request) => request.path === '/listings/ledgerly')).toHaveLength(1)
  })

  it('sends blank details as null', async () => {
    const { requests } = await openReport({ [REPORT]: () => created() })

    choose('Spam')
    fireEvent.change(inDialog().getByLabelText('Details (optional)'), { target: { value: '   ' } })
    submit()

    await inDialog().findByText('Report submitted.')
    expect(reports(requests)[0].body).toEqual({ targetType: 'LISTING', targetId: 1, reason: 'SPAM', details: null })
  })

  it('relies on the backend for a repeat report (409)', async () => {
    const { handlers, requests } = await openReport({ [REPORT]: () => created() })
    choose('Spam')
    submit()
    fireEvent.click(await inDialog().findByRole('button', { name: 'Done' }))

    handlers[REPORT] = () => problem(409, LEAKY_DETAIL)
    fireEvent.click(reportButton())
    choose('Spam')
    submit()

    const notice = await inDialog().findByRole('status')
    expect(notice.textContent).toBe('You have already reported this.')
    expect(inDialog().queryByRole('button', { name: 'Submit report' })).toBeNull()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    expect(reports(requests)).toHaveLength(2)
  })
})

describe('Report failures', () => {
  it.each([
    [403, 'Your account is suspended, so you can’t submit reports.'],
    [404, 'This listing isn’t available any more, so it can’t be reported.'],
  ])('replaces the form with a final message after a %i', async (status, message) => {
    await openReport({ [REPORT]: () => problem(status, LEAKY_DETAIL) })
    choose('Spam')
    submit()

    const alert = await inDialog().findByRole('alert')
    expect(alert.textContent).toBe(message)
    expect(inDialog().queryByRole('button', { name: 'Submit report' })).toBeNull()
    const close = inDialog().getByRole('button', { name: 'Close' })
    expect(close).toBeTruthy()
    await waitFor(() => expect(document.activeElement?.textContent).toBe('Close'))
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('shows 400 field errors in the dialog’s own words, keeping the input', async () => {
    await openReport({
      [REPORT]: () => problem(400, 'Invalid request content.', [{ field: 'details', message: LEAKY_DETAIL }]),
    })
    choose('Something else')
    fireEvent.change(inDialog().getByLabelText('Details (optional)'), { target: { value: 'Something odd.' } })
    submit()

    expect(await inDialog().findByText('These details weren’t accepted. Shorten or rephrase them.')).toBeTruthy()
    expect(within(inDialog().getAllByRole('alert')[0]).getByText('Please correct the highlighted fields.')).toBeTruthy()
    expect((inDialog().getByLabelText('Details (optional)') as HTMLTextAreaElement).value).toBe('Something odd.')
    expect((inDialog().getByRole('radio', { name: /^Something else/ }) as HTMLInputElement).checked).toBe(true)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it.each([
    [429, 'You’ve submitted several reports recently. Please wait a while before sending another.'],
    [500, 'Your report wasn’t sent because something went wrong. Please try again.'],
    [503, 'Your report wasn’t sent because something went wrong. Please try again.'],
  ])('keeps the form after a %i so the user can try again', async (status, message) => {
    const { handlers, requests } = await openReport({ [REPORT]: () => problem(status, LEAKY_DETAIL) })
    choose('Scam or fraud')
    fireEvent.change(inDialog().getByLabelText('Details (optional)'), { target: { value: 'Asked for a deposit.' } })
    submit()

    const alert = await inDialog().findByRole('alert')
    expect(alert.textContent).toContain('Report not sent')
    expect(alert.textContent).toContain(message)
    expect((inDialog().getByLabelText('Details (optional)') as HTMLTextAreaElement).value).toBe('Asked for a deposit.')
    expect(inDialog().getByRole('button', { name: 'Submit report' })).toHaveProperty('disabled', false)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[REPORT] = () => created({ reason: 'SCAM_OR_FRAUD' })
    submit()

    expect(await inDialog().findByText('Report submitted.')).toBeTruthy()
    expect(reports(requests)).toHaveLength(2)
  })

  it('keeps the form after a network failure', async () => {
    await openReport({
      [REPORT]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    choose('Spam')
    submit()

    expect((await inDialog().findByRole('alert')).textContent).toContain(
      'Unable to reach the server. Your report wasn’t sent; check your connection and try again.',
    )
    expect(inDialog().getByRole('button', { name: 'Submit report' })).toBeTruthy()
  })

  it('sends the user to log in, and back here, when the session has expired', async () => {
    const { router } = await openReport({ [REPORT]: () => problem(401, 'A valid access token is required.') })
    choose('Spam')
    submit()

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings/ledgerly' } })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(getAccessToken()).toBeNull()
    expect(document.querySelector('[inert]')).toBeNull()
  })
})
