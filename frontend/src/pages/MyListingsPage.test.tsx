import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { myListingSummary, pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

const LEAKY_DETAIL = 'org.hibernate.exception.JDBCConnectionException'

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

const nav = () => screen.getByRole('navigation', { name: 'Main' })
const items = () => screen.getAllByRole('article')

describe('My listings', () => {
  it('sends anonymous visitors to log in without asking for anyone’s listings', async () => {
    const { requests } = mockApi({})
    const router = renderApp('/my-listings')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/login')
    expect(requests.some((request) => request.path.startsWith('/listings/mine'))).toBe(false)
    expect(within(nav()).queryByRole('link', { name: 'My listings' })).toBeNull()
  })

  it('is in the main navigation for signed-in members', async () => {
    signedIn({ 'GET /listings?page=0&size=6': () => json(pageOf([])) })
    renderApp('/')

    const link = await within(nav()).findByRole('link', { name: 'My listings' })
    expect(link.getAttribute('href')).toBe('/my-listings')
  })

  it('lists the user’s own listings in every state, with the fields the summary carries', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({ 'GET /listings/mine?page=0&size=12': () => response.promise })
    renderApp('/my-listings')

    expect(await screen.findByText('Loading your listings…')).toBeTruthy()
    expect(document.title).toBe('My listings · Conflux')
    response.resolve(
      json(
        pageOf([
          myListingSummary({ id: 5, title: 'Harbor Metrics', status: 'DRAFT' }),
          myListingSummary({
            id: 6,
            title: 'Pairwise',
            status: 'PUBLISHED',
            marketplaceMode: 'COLLABORATE',
            assetType: 'IDEA',
            category: 'AI',
            stage: 'CONCEPT',
            askingPrice: null,
            currency: null,
            priceNegotiable: false,
            publishedAt: '2026-03-05T12:00:00Z',
          }),
          myListingSummary({ id: 7, title: 'Old Shop', status: 'ARCHIVED' }),
          myListingSummary({ id: 8, title: 'Hidden Thing', status: 'SUSPENDED' }),
        ]),
      ),
    )

    expect(await screen.findByText('4 listings')).toBeTruthy()
    const [draft, published, archived, suspended] = items()
    expect(draft.getAttribute('data-status')).toBe('draft')
    expect(within(draft).getByText('Draft')).toBeTruthy()
    expect(within(draft).getByRole('link', { name: 'Harbor Metrics' }).getAttribute('href')).toBe('/my-listings/5')
    expect(within(draft).getByText('MVP', { selector: '.tag-strong' })).toBeTruthy()
    expect(within(draft).getByText('Acquisition')).toBeTruthy()
    expect(within(draft).getByText('Developer tools')).toBeTruthy()
    expect(within(draft).getByText('$12,000')).toBeTruthy()
    expect(within(draft).getByText('Negotiable')).toBeTruthy()
    expect(draft.querySelector('time[datetime="2026-03-01T09:00:00Z"]')).toBeTruthy()
    expect(draft.querySelector('time[datetime="2026-03-02T10:30:00Z"]')).toBeTruthy()
    expect(draft.textContent).not.toContain('Published')

    expect(within(published).getByText('Published')).toBeTruthy()
    expect(within(published).getByText('Collaboration')).toBeTruthy()
    expect(within(published).getByText('Open to collaborate')).toBeTruthy()
    expect(published.querySelector('time[datetime="2026-03-05T12:00:00Z"]')).toBeTruthy()

    expect(within(archived).getByText('Archived')).toBeTruthy()
    expect(within(suspended).getByText('Suspended')).toBeTruthy()
    expect(new Set(items().map((item) => item.getAttribute('data-status')))).toEqual(
      new Set(['draft', 'published', 'archived', 'suspended']),
    )
    expect(screen.queryByRole('button', { name: /Suspend|Restore/ })).toBeNull()
    expect(document.body.textContent).not.toMatch(/DRAFT|PUBLISHED|ARCHIVED|SUSPENDED/)
    expect(requests.find((request) => request.path.startsWith('/listings/mine'))?.headers.get('Authorization')).toBe(
      'Bearer stored-token',
    )
  })

  it('offers to create a listing when there are none yet', async () => {
    signedIn({ 'GET /listings/mine?page=0&size=12': () => json(pageOf([])) })
    renderApp('/my-listings')

    expect(await screen.findByRole('heading', { name: 'No listings yet' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Create your first listing' }).getAttribute('href')).toBe('/my-listings/new')
    expect(screen.getByRole('link', { name: 'Create listing' }).getAttribute('href')).toBe('/my-listings/new')
  })

  it('pages with the backend’s zero-based page parameter', async () => {
    const { requests } = signedIn({
      'GET /listings/mine?page=1&size=12': () =>
        json(pageOf([myListingSummary({ id: 30, title: 'Thirteenth' })], { page: 1, totalElements: 13 })),
    })
    renderApp('/my-listings?page=2')

    expect(await screen.findByText('Showing 13–13 of 13 listings')).toBeTruthy()
    expect(requests.some((request) => request.path === '/listings/mine?page=1&size=12')).toBe(true)
    const pagination = screen.getByRole('navigation', { name: 'Pagination' })
    expect(within(pagination).getByRole('link', { name: 'Page 1' }).getAttribute('href')).toBe('/my-listings')
  })

  it('shows failures without backend details, and retries', async () => {
    const { handlers } = signedIn({ 'GET /listings/mine?page=0&size=12': () => problem(500, LEAKY_DETAIL) })
    renderApp('/my-listings')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load your listings')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers['GET /listings/mine?page=0&size=12'] = () => json(pageOf([myListingSummary()]))
    within(alert).getByRole('button', { name: 'Try again' }).click()
    expect(await screen.findByRole('link', { name: 'Harbor Metrics' })).toBeTruthy()
  })

  it('explains a 403 for a suspended account', async () => {
    signedIn({ 'GET /listings/mine?page=0&size=12': () => problem(403, 'This account is suspended.') })
    renderApp('/my-listings')

    expect((await screen.findByRole('alert')).textContent).toContain('It may be suspended.')
  })

  it('ends the session when the token is rejected', async () => {
    signedIn({ 'GET /listings/mine?page=0&size=12': () => problem(401, 'A valid access token is required.') })
    const router = renderApp('/my-listings')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })
})
