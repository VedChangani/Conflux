import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../test/api'
import { listingDetail, pageOf, savedListing } from '../test/listings'
import { renderApp } from '../test/renderApp'

const FIRST_PAGE = 'GET /saved-listings?page=0&size=12'
const LEAKY_DETAIL = 'could not execute statement; SQL [n/a]; constraint [saved_listings_pkey]'

const LEDGERLY = savedListing()
const PAIRWISE = savedListing({ id: 2, slug: 'pairwise', title: 'Pairwise' })

const noContent = () => new Response(null, { status: 204 })
const list = () => screen.getByRole('region', { name: 'Your list' })
const count = () => within(list()).getByRole('status')
const paths = (requests: RecordedRequest[]) => requests.map((request) => `${request.method} ${request.path}`)

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

describe('Saved listings page', () => {
  it('redirects anonymous visitors to log in and back to their saved listings', async () => {
    mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      'GET /saved-listings?page=1&size=12': () => json(pageOf([LEDGERLY], { page: 1, totalElements: 13 })),
    })
    const router = renderApp('/saved?page=2')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Saved listings' })).toBeTruthy()
    expect(router.state.location).toMatchObject({ pathname: '/saved', search: '?page=2' })
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
  })

  it('is linked from the navigation for signed-in users only', async () => {
    signedIn({ [FIRST_PAGE]: () => json(pageOf([])) })
    renderApp('/saved')

    const nav = within(screen.getByRole('navigation', { name: 'Main' }))
    expect((await nav.findByRole('link', { name: 'Saved' })).getAttribute('href')).toBe('/saved')
    fireEvent.click(nav.getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(nav.queryByRole('link', { name: 'Saved' })).toBeNull())
  })

  it('shows a loading state, then the saved listings as saved cards', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({ [FIRST_PAGE]: () => response.promise })
    renderApp('/saved')

    expect((await within(await screen.findByRole('region', { name: 'Your list' })).findByRole('status')).textContent).toBe(
      'Loading saved listings…',
    )
    expect(list().getAttribute('aria-busy')).toBe('true')

    response.resolve(json(pageOf([LEDGERLY, PAIRWISE])))

    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(list().getAttribute('aria-busy')).toBe('false')
    expect(count().textContent).toBe('2 saved listings')
    const toggle = screen.getByRole('button', { name: 'Saved Ledgerly' })
    expect(toggle.getAttribute('aria-pressed')).toBe('true')
    expect(toggle).toHaveProperty('disabled', false)
    expect(screen.getByRole('link', { name: 'Ledgerly' }).getAttribute('href')).toBe('/listings/ledgerly')

    const listRequest = requests.find((request) => request.path === '/saved-listings?page=0&size=12')
    expect(listRequest?.headers.get('Authorization')).toBe('Bearer stored-token')
    expect(paths(requests).filter((request) => request.includes('/saved-listings'))).toEqual([FIRST_PAGE])
  })

  it('shows an empty state that leads to the marketplace', async () => {
    signedIn({ [FIRST_PAGE]: () => json(pageOf([])) })
    renderApp('/saved')

    expect(await screen.findByRole('heading', { name: 'Nothing saved yet' })).toBeTruthy()
    expect(count().textContent).toBe('0 saved listings')
    expect(screen.getByRole('link', { name: 'Browse listings' }).getAttribute('href')).toBe('/listings')
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).toBeNull()
  })

  it('shows an error without backend details, with a working retry', async () => {
    const { handlers } = signedIn({ [FIRST_PAGE]: () => problem(500, LEAKY_DETAIL) })
    renderApp('/saved')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("We couldn't load your saved listings")
    expect(alert.textContent).toContain('Something went wrong. Please try again.')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[FIRST_PAGE] = () => json(pageOf([LEDGERLY]))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('explains a rate limit in its own words, with a working retry', async () => {
    const { handlers } = signedIn({ [FIRST_PAGE]: () => problem(429, LEAKY_DETAIL) })
    renderApp('/saved')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("We couldn't load your saved listings")
    expect(alert.textContent).toContain('Too many requests in a short time. Wait a moment, then try again.')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[FIRST_PAGE] = () => json(pageOf([LEDGERLY]))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
  })

  it('paginates with the backend page metadata', async () => {
    const pageOne = Array.from({ length: 12 }, (_, i) => savedListing({ id: i + 1, slug: `a-${i}`, title: `A${i}` }))
    const pageTwo = Array.from({ length: 3 }, (_, i) => savedListing({ id: i + 20, slug: `b-${i}`, title: `B${i}` }))
    const { requests } = signedIn({
      [FIRST_PAGE]: () => json(pageOf(pageOne, { totalElements: 15 })),
      'GET /saved-listings?page=1&size=12': () => json(pageOf(pageTwo, { page: 1, totalElements: 15 })),
    })
    const router = renderApp('/saved')
    await screen.findByRole('heading', { level: 3, name: 'A0' })
    expect(count().textContent).toBe('Showing 1–12 of 15 saved listings')

    const pagination = within(screen.getByRole('navigation', { name: 'Pagination' }))
    fireEvent.click(pagination.getByRole('link', { name: /Next/ }))

    expect(await screen.findByRole('heading', { level: 3, name: 'B0' })).toBeTruthy()
    expect(router.state.location.search).toBe('?page=2')
    expect(requests.at(-1)?.path).toBe('/saved-listings?page=1&size=12')
    expect(count().textContent).toBe('Showing 13–15 of 15 saved listings')
    expect(document.activeElement).toBe(screen.getByRole('heading', { level: 2, name: 'Your list' }))
  })

  it('handles a page past the end', async () => {
    signedIn({ 'GET /saved-listings?page=4&size=12': () => json(pageOf([], { page: 4, totalElements: 13 })) })
    renderApp('/saved?page=5')

    expect(await screen.findByRole('heading', { name: 'There is no page 5' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Go to page 2' }).getAttribute('href')).toBe('/saved?page=2')
  })

  it('links a card back to the saved listings from the listing page', async () => {
    signedIn({
      'GET /saved-listings?page=1&size=12': () => json(pageOf([LEDGERLY], { page: 1, totalElements: 13 })),
      'GET /listings/ledgerly': () => json(listingDetail()),
      'GET /saved-listings?page=0&size=50': () => json(pageOf([LEDGERLY], { size: 50 })),
    })
    const router = renderApp('/saved?page=2')

    fireEvent.click(await screen.findByRole('link', { name: 'Ledgerly' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })).toBeTruthy()

    const back = within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link')
    expect(back.textContent).toContain('Back to saved listings')
    expect(back.getAttribute('href')).toBe('/saved?page=2')
    expect(router.state.location.pathname).toBe('/listings/ledgerly')
  })
})

describe('Unsaving from the saved listings page', () => {
  it('removes the card without a reload and refreshes the page in the background', async () => {
    const unsave = deferred<Response>()
    const { handlers, requests } = signedIn({
      [FIRST_PAGE]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      'DELETE /listings/1/save': () => unsave.promise,
    })
    renderApp('/saved')

    const toggle = await screen.findByRole('button', { name: 'Saved Ledgerly' })
    fireEvent.click(toggle)

    expect(toggle.textContent).toContain('Removing…')
    expect(toggle).toHaveProperty('disabled', true)
    expect(screen.getByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()

    handlers[FIRST_PAGE] = () => json(pageOf([PAIRWISE]))
    unsave.resolve(noContent())

    await waitFor(() => expect(screen.queryByRole('heading', { level: 3, name: 'Ledgerly' })).toBeNull())
    expect(screen.getByRole('heading', { level: 3, name: 'Pairwise' })).toBeTruthy()
    expect(count().textContent).toBe('1 saved listing')
    expect(screen.getByText('Removed “Ledgerly” from your saved listings.')).toBeTruthy()
    expect(document.activeElement).toBe(screen.getByRole('heading', { level: 2, name: 'Your list' }))

    await waitFor(() => expect(paths(requests).filter((request) => request === FIRST_PAGE)).toHaveLength(2))
    expect(paths(requests).filter((request) => request === 'DELETE /listings/1/save')).toHaveLength(1)
    await waitFor(() => expect(list().getAttribute('aria-busy')).toBe('false'))
    expect(screen.getByRole('heading', { level: 3, name: 'Pairwise' })).toBeTruthy()
  })

  it('shows the empty state after the last listing is unsaved', async () => {
    const { handlers } = signedIn({
      [FIRST_PAGE]: () => json(pageOf([LEDGERLY])),
      'DELETE /listings/1/save': () => noContent(),
    })
    renderApp('/saved')

    const toggle = await screen.findByRole('button', { name: 'Saved Ledgerly' })
    handlers[FIRST_PAGE] = () => json(pageOf([]))
    fireEvent.click(toggle)

    expect(await screen.findByRole('heading', { name: 'Nothing saved yet' })).toBeTruthy()
    expect(count().textContent).toBe('0 saved listings')
  })

  it('keeps the card and explains when unsaving fails', async () => {
    signedIn({
      [FIRST_PAGE]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      'DELETE /listings/1/save': () => problem(429, LEAKY_DETAIL),
    })
    renderApp('/saved')

    fireEvent.click(await screen.findByRole('button', { name: 'Saved Ledgerly' }))

    expect((await screen.findByRole('alert')).textContent).toBe(
      'You’re doing that too often. Please wait a moment and try again.',
    )
    expect(screen.getByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Saved Ledgerly' }).getAttribute('aria-pressed')).toBe('true')
    expect(count().textContent).toBe('2 saved listings')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })
})
