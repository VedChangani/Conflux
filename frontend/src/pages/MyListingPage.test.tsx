import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, json, mockApi, problem } from '../test/api'
import { ownerListing } from '../test/listings'
import { renderApp } from '../test/renderApp'

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

const panel = () => within(screen.getByRole('complementary', { name: 'Listing status' }))
const heading = () => screen.findByRole('heading', { level: 1, name: 'Harbor Metrics' })

describe('My listing', () => {
  it('loads the private listing from /listings/mine/{id}, never from the public endpoint', async () => {
    const { requests } = signedIn({
      'GET /listings/mine/5': () =>
        json(ownerListing({ problem: 'Teams fly blind.', collaborationDetails: 'Looking for a co-founder.' })),
    })
    renderApp('/my-listings/5')

    expect(await heading()).toBeTruthy()
    expect(document.title).toBe('Harbor Metrics · My listings · Conflux')
    expect(screen.getByText('Fleet dashboards for small teams.')).toBeTruthy()
    expect(screen.getByText('Built for teams without an SRE.')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'The problem' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Collaboration details' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'The solution' })).toBeNull()

    expect(panel().getByText('Draft')).toBeTruthy()
    expect(panel().getByText(/A private draft: only you can see it/)).toBeTruthy()
    expect(screen.getByText('$12,000')).toBeTruthy()
    expect(screen.getByText('Not yet')).toBeTruthy()
    expect(screen.getByText('/listings/harbor-metrics-1a2b3c4d')).toBeTruthy()
    expect(document.querySelector('time[datetime="2026-03-01T09:00:00Z"]')).toBeTruthy()

    const paths = requests.map((request) => request.path)
    expect(paths).toContain('/listings/mine/5')
    expect(paths.some((path) => path.startsWith('/listings/harbor'))).toBe(false)
    expect(requests.find((request) => request.path === '/listings/mine/5')?.headers.get('Authorization')).toBe(
      'Bearer stored-token',
    )
  })

  it('offers Publish, Edit and Archive for a draft, and no marketplace link', async () => {
    signedIn({ 'GET /listings/mine/5': () => json(ownerListing()) })
    renderApp('/my-listings/5')
    await heading()

    expect(panel().getByRole('button', { name: 'Publish' })).toBeTruthy()
    expect(panel().getByRole('link', { name: 'Edit' }).getAttribute('href')).toBe('/my-listings/5/edit')
    expect(panel().getByRole('button', { name: 'Archive' })).toBeTruthy()
    expect(panel().queryByRole('link', { name: /marketplace/ })).toBeNull()
  })

  it('publishes a draft, then shows it as PUBLISHED with its published time and public link', async () => {
    const { requests } = signedIn({
      'GET /listings/mine/5': () => json(ownerListing()),
      'POST /listings/5/publish': () =>
        json(ownerListing({ status: 'PUBLISHED', publishedAt: '2026-03-03T08:00:00Z', updatedAt: '2026-03-03T08:00:00Z' })),
    })
    renderApp('/my-listings/5')
    await heading()

    fireEvent.click(panel().getByRole('button', { name: 'Publish' }))

    expect(await panel().findByText('Published', { selector: '.listing-status-badge' })).toBeTruthy()
    expect(screen.getByText('Published.').closest('[role="status"]')?.textContent).toContain('live on the marketplace')
    expect(screen.getByRole('link', { name: 'View it as others see it' }).getAttribute('href')).toBe(
      '/listings/harbor-metrics-1a2b3c4d',
    )
    expect(panel().getByRole('link', { name: /View on the marketplace/ }).getAttribute('href')).toBe(
      '/listings/harbor-metrics-1a2b3c4d',
    )
    expect(document.querySelector('time[datetime="2026-03-03T08:00:00Z"]')).toBeTruthy()
    expect(screen.queryByText('Not yet')).toBeNull()
    expect(panel().queryByRole('button', { name: 'Publish' })).toBeNull()
    expect(panel().getByRole('link', { name: 'Edit' })).toBeTruthy()
    expect(panel().getByRole('button', { name: 'Archive' })).toBeTruthy()
    expect(requests.filter((request) => request.path === '/listings/5/publish')).toHaveLength(1)
  })

  it('asks before archiving, and Cancel changes nothing', async () => {
    const { requests } = signedIn({ 'GET /listings/mine/5': () => json(ownerListing({ status: 'PUBLISHED' })) })
    renderApp('/my-listings/5')
    await heading()

    fireEvent.click(panel().getByRole('button', { name: 'Archive' }))
    const confirm = within(screen.getByRole('group', { name: 'Confirm' }))
    expect(confirm.getByText(/can’t be edited, published or restored afterwards/)).toBeTruthy()
    await waitFor(() => expect(document.activeElement).toBe(confirm.getByRole('button', { name: 'Yes, archive' })))

    fireEvent.click(confirm.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('group', { name: 'Confirm' })).toBeNull()
    await waitFor(() => expect(document.activeElement).toBe(panel().getByRole('button', { name: 'Archive' })))
    expect(requests.filter((request) => request.method === 'DELETE')).toHaveLength(0)
  })

  it('archives via DELETE, then re-reads the listing and shows the state the backend reports', async () => {
    const { handlers, requests } = signedIn({
      'GET /listings/mine/5': () => json(ownerListing({ status: 'PUBLISHED', publishedAt: '2026-03-03T08:00:00Z' })),
      'DELETE /listings/5': () => new Response(null, { status: 204 }),
    })
    renderApp('/my-listings/5')
    await heading()

    fireEvent.click(panel().getByRole('button', { name: 'Archive' }))
    handlers['GET /listings/mine/5'] = () =>
      json(ownerListing({ status: 'ARCHIVED', publishedAt: '2026-03-03T08:00:00Z' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, archive' }))

    expect(await panel().findByText('Archived')).toBeTruthy()
    expect(screen.getByText('Listing archived.')).toBeTruthy()
    expect(panel().queryByRole('button')).toBeNull()
    expect(panel().queryByRole('link', { name: 'Edit' })).toBeNull()
    expect(panel().queryByRole('link', { name: /marketplace/ })).toBeNull()
    const order = requests.filter((request) => request.path.includes('/5')).map((r) => `${r.method} ${r.path}`)
    expect(order.slice(-2)).toEqual(['DELETE /listings/5', 'GET /listings/mine/5'])
  })

  it.each([
    ['ARCHIVED', /Archived: off the marketplace for good/],
    ['SUSPENDED', /Suspended by Conflux moderators/],
  ])('shows a %s listing with no owner actions and no moderation controls', async (status, summary) => {
    signedIn({ 'GET /listings/mine/5': () => json(ownerListing({ status })) })
    renderApp('/my-listings/5')
    await heading()

    expect(panel().getByText(summary)).toBeTruthy()
    expect(panel().queryByRole('button')).toBeNull()
    expect(panel().queryByRole('link', { name: 'Edit' })).toBeNull()
    expect(screen.queryByRole('button', { name: /Suspend|Restore/ })).toBeNull()
  })

  it('shows a not-found page for a listing that is missing or someone else’s', async () => {
    signedIn({ 'GET /listings/mine/99': () => problem(404, 'Listing not found.') })
    renderApp('/my-listings/99')

    expect(await screen.findByRole('heading', { level: 1, name: 'Listing not found' })).toBeTruthy()
    expect(screen.getByText('This listing doesn’t exist, or it isn’t one of yours.')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Go to my listings' }).getAttribute('href')).toBe('/my-listings')
  })

  it.each(['abc', '0', '-3', '1.5'])('treats the id %s as not found without asking the backend', async (id) => {
    const { requests } = signedIn({})
    renderApp(`/my-listings/${id}`)

    expect(await screen.findByRole('heading', { level: 1, name: 'Listing not found' })).toBeTruthy()
    expect(requests.some((request) => request.path.startsWith('/listings/mine/'))).toBe(false)
  })

  it('reports a 409 on publish and shows the listing’s current state', async () => {
    const { handlers } = signedIn({
      'GET /listings/mine/5': () => json(ownerListing()),
      'POST /listings/5/publish': () => problem(409, 'A listing with status SUSPENDED cannot be published.'),
    })
    renderApp('/my-listings/5')
    await heading()

    handlers['GET /listings/mine/5'] = () => json(ownerListing({ status: 'SUSPENDED' }))
    fireEvent.click(panel().getByRole('button', { name: 'Publish' }))

    expect(await panel().findByText('Suspended')).toBeTruthy()
    expect(panel().getByRole('alert').textContent).toBe(
      'Only drafts can be published. The listing’s current status is shown.',
    )
    expect(document.body.textContent).not.toContain('cannot be published')
  })

  it.each([
    [403, 'archive', 'Your account is suspended, so your listings can’t be changed.'],
    [404, 'archive', 'This listing doesn’t exist, or it isn’t one of yours.'],
    [429, 'publish', 'You’ve published several listings recently. Please wait a while and try again.'],
  ] as const)('explains a %i on %s and keeps the listing as it was', async (status, action, message) => {
    signedIn({
      'GET /listings/mine/5': () => json(ownerListing()),
      'POST /listings/5/publish': () => problem(status, 'backend wording'),
      'DELETE /listings/5': () => problem(status, 'backend wording'),
    })
    renderApp('/my-listings/5')
    await heading()

    if (action === 'publish') {
      fireEvent.click(panel().getByRole('button', { name: 'Publish' }))
    } else {
      fireEvent.click(panel().getByRole('button', { name: 'Archive' }))
      fireEvent.click(screen.getByRole('button', { name: 'Yes, archive' }))
    }

    expect((await panel().findByRole('alert')).textContent).toBe(message)
    expect(panel().getByText('Draft')).toBeTruthy()
    expect(document.body.textContent).not.toContain('backend wording')
  })

  it('ends the session when an action’s token is rejected', async () => {
    signedIn({
      'GET /listings/mine/5': () => json(ownerListing()),
      'POST /listings/5/publish': () => problem(401, 'A valid access token is required.'),
    })
    const router = renderApp('/my-listings/5')
    await heading()

    fireEvent.click(panel().getByRole('button', { name: 'Publish' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  it('shows other load failures without backend details', async () => {
    signedIn({ 'GET /listings/mine/5': () => problem(500, 'org.springframework.dao.DataAccessException') })
    renderApp('/my-listings/5')

    expect((await screen.findByRole('alert')).textContent).toContain('We couldn’t load this listing')
    expect(document.body.textContent).not.toContain('DataAccessException')
  })
})
