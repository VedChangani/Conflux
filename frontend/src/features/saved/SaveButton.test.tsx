import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../../test/api'
import { listingCard, listingDetail, pageOf, savedListing } from '../../test/listings'
import { renderApp } from '../../test/renderApp'

const DISCOVER = 'GET /listings?page=0&size=12'
const DETAIL = 'GET /listings/ledgerly'
const LOOKUP = 'GET /saved-listings?page=0&size=50'
const SAVE = 'POST /listings/1/save'
const UNSAVE = 'DELETE /listings/1/save'

const LEDGERLY = listingCard()
const PAIRWISE = listingCard({ id: 2, slug: 'pairwise', title: 'Pairwise' })
const LEAKY_DETAIL = 'org.hibernate.SomethingFailed at SavedListingService.java:42'

const noContent = () => new Response(null, { status: 204 })
const calls = (requests: RecordedRequest[], key: string) =>
  requests.filter((request) => `${request.method} ${request.path}` === key)

const saveButton = (name: string | RegExp) => screen.getByRole('button', { name })

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

describe('Saving from listing cards', () => {
  it('shows which listings are already saved, from the saved listings lookup', async () => {
    const { requests } = signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      [LOOKUP]: () => json(pageOf([savedListing()], { size: 50 })),
    })
    renderApp('/listings')

    const saved = await screen.findByRole('button', { name: 'Saved Ledgerly' })
    expect(saved.getAttribute('aria-pressed')).toBe('true')
    const notSaved = screen.getByRole('button', { name: 'Save Pairwise' })
    expect(notSaved.getAttribute('aria-pressed')).toBe('false')

    expect(calls(requests, LOOKUP)).toHaveLength(1)
    expect(calls(requests, LOOKUP)[0].headers.get('Authorization')).toBe('Bearer stored-token')
    const [card] = screen.getAllByRole('article')
    expect(within(card).getAllByRole('link')).toHaveLength(1)
  })

  it('keeps the toggle disabled while the saved state is being looked up', async () => {
    const lookup = deferred<Response>()
    signedIn({ [DISCOVER]: () => json(pageOf([LEDGERLY])), [LOOKUP]: () => lookup.promise })
    renderApp('/listings')

    const button = await screen.findByRole('button', { name: 'Save Ledgerly' })
    expect(button).toHaveProperty('disabled', true)

    lookup.resolve(json(pageOf([], { size: 50 })))

    await waitFor(() => expect(button).toHaveProperty('disabled', false))
  })

  it('pages through every saved listing when looking them up', async () => {
    const { requests } = signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      [LOOKUP]: () => json(pageOf([savedListing({ id: 9 })], { size: 50, totalElements: 51 })),
      'GET /saved-listings?page=1&size=50': () =>
        json(pageOf([savedListing({ id: 2, slug: 'pairwise' })], { page: 1, size: 50, totalElements: 51 })),
    })
    renderApp('/listings')

    expect(await screen.findByRole('button', { name: 'Saved Pairwise' })).toBeTruthy()
    expect(saveButton('Save Ledgerly').getAttribute('aria-pressed')).toBe('false')
    expect(requests.filter((request) => request.path.startsWith('/saved-listings'))).toHaveLength(2)
  })

  it('saves a listing, updating only once the API has confirmed it', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY])),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => response.promise,
    })
    renderApp('/listings')
    const button = await screen.findByRole('button', { name: 'Save Ledgerly' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))

    fireEvent.click(button)

    expect(button.textContent).toContain('Saving…')
    expect(button).toHaveProperty('disabled', true)
    expect(button.getAttribute('aria-busy')).toBe('true')
    expect(button.getAttribute('aria-pressed')).toBe('false')

    response.resolve(noContent())

    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('true'))
    expect(button.textContent).toContain('Saved')
    expect(button).toHaveProperty('disabled', false)
    expect(calls(requests, SAVE)).toHaveLength(1)
    expect(calls(requests, SAVE)[0].headers.get('Authorization')).toBe('Bearer stored-token')
    expect(calls(requests, SAVE)[0].body).toBeUndefined()
  })

  it('sends one request however often the button is clicked while saving', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY])),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => response.promise,
    })
    renderApp('/listings')
    const button = await screen.findByRole('button', { name: 'Save Ledgerly' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))

    fireEvent.click(button)
    fireEvent.click(button)
    fireEvent.click(button)
    response.resolve(noContent())

    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('true'))
    expect(calls(requests, SAVE)).toHaveLength(1)
    expect(calls(requests, UNSAVE)).toHaveLength(0)
  })

  it('unsaves a saved listing', async () => {
    const { requests } = signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY])),
      [LOOKUP]: () => json(pageOf([savedListing()], { size: 50 })),
      [UNSAVE]: () => noContent(),
    })
    renderApp('/listings')

    fireEvent.click(await screen.findByRole('button', { name: 'Saved Ledgerly' }))

    const button = await screen.findByRole('button', { name: 'Save Ledgerly' })
    expect(button.getAttribute('aria-pressed')).toBe('false')
    expect(calls(requests, UNSAVE)).toHaveLength(1)
  })

  it('treats saving an already saved listing as saved (the API is idempotent)', async () => {
    const { requests } = signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY])),
      [LOOKUP]: () => problem(500, 'An unexpected error occurred.'),
      [SAVE]: () => noContent(),
    })
    renderApp('/listings')
    const button = await screen.findByRole('button', { name: 'Save Ledgerly' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))

    fireEvent.click(button)

    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('true'))
    expect(screen.queryByRole('alert')).toBeNull()
    expect(calls(requests, SAVE)).toHaveLength(1)
  })

  it('shares the confirmed state between pages', async () => {
    signedIn({
      [DISCOVER]: () => json(pageOf([LEDGERLY])),
      [DETAIL]: () => json(listingDetail()),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => noContent(),
    })
    const router = renderApp('/listings/ledgerly')
    const button = await screen.findByRole('button', { name: 'Save' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))
    fireEvent.click(button)
    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('true'))

    await router.navigate('/listings')

    expect((await screen.findByRole('button', { name: 'Saved Ledgerly' })).getAttribute('aria-pressed')).toBe('true')
  })
})

describe('Saving as an anonymous visitor', () => {
  it('sends the visitor to log in and back to the listing, without saving', async () => {
    const { requests } = mockApi({
      [DETAIL]: () => json(listingDetail()),
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
    })
    const router = renderApp('/listings/ledgerly')

    const button = await screen.findByRole('button', { name: 'Save' })
    expect(button.getAttribute('aria-pressed')).toBeNull()
    fireEvent.click(button)

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings/ledgerly' } })
    expect(requests.some((request) => request.path.endsWith('/save'))).toBe(false)

    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/listings/ledgerly')
  })

  it('keeps the marketplace query when redirecting from a card', async () => {
    mockApi({ 'GET /listings?category=FINTECH&page=0&size=12': () => json(pageOf([LEDGERLY])) })
    const router = renderApp('/listings?category=FINTECH')

    fireEvent.click(await screen.findByRole('button', { name: 'Save Ledgerly' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings', search: '?category=FINTECH' } })
  })
})

describe('Save errors', () => {
  it.each([
    [403, 'Your account is suspended, so saved listings can’t be changed.'],
    [404, 'This listing is no longer available, so it can’t be saved.'],
    [409, 'This listing changed in the meantime. Refresh the page and try again.'],
    [429, 'You’re doing that too often. Please wait a moment and try again.'],
    [500, 'We couldn’t save this listing. Please try again.'],
  ])('shows a %i as a friendly message and leaves the state unchanged', async (status, message) => {
    signedIn({
      [DETAIL]: () => json(listingDetail()),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => problem(status, LEAKY_DETAIL),
    })
    renderApp('/listings/ledgerly')
    const button = await screen.findByRole('button', { name: 'Save' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))

    fireEvent.click(button)

    expect((await screen.findByRole('alert')).textContent).toBe(message)
    expect(button.getAttribute('aria-pressed')).toBe('false')
    expect(button).toHaveProperty('disabled', false)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('reports a failed unsave and keeps the listing saved', async () => {
    signedIn({
      [DETAIL]: () => json(listingDetail()),
      [LOOKUP]: () => json(pageOf([savedListing()], { size: 50 })),
      [UNSAVE]: () => problem(503, LEAKY_DETAIL),
    })
    renderApp('/listings/ledgerly')

    fireEvent.click(await screen.findByRole('button', { name: 'Saved' }))

    expect((await screen.findByRole('alert')).textContent).toBe('We couldn’t remove this listing. Please try again.')
    expect(saveButton('Saved').getAttribute('aria-pressed')).toBe('true')
  })

  it('reports a network failure', async () => {
    signedIn({
      [DETAIL]: () => json(listingDetail()),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderApp('/listings/ledgerly')
    const button = await screen.findByRole('button', { name: 'Save' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))

    fireEvent.click(button)

    expect((await screen.findByRole('alert')).textContent).toBe(
      'Unable to reach the server. Check your connection and try again.',
    )
  })

  it('clears the error once a retry succeeds', async () => {
    const { handlers } = signedIn({
      [DETAIL]: () => json(listingDetail()),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => problem(429, 'Too many requests. Please try again later.'),
    })
    renderApp('/listings/ledgerly')
    const button = await screen.findByRole('button', { name: 'Save' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))
    fireEvent.click(button)
    await screen.findByRole('alert')

    handlers[SAVE] = () => noContent()
    fireEvent.click(button)

    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('true'))
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('sends the user to log in when the session has expired', async () => {
    signedIn({
      [DETAIL]: () => json(listingDetail()),
      [LOOKUP]: () => json(pageOf([], { size: 50 })),
      [SAVE]: () => problem(401, 'A valid access token is required.'),
    })
    const router = renderApp('/listings/ledgerly')
    const button = await screen.findByRole('button', { name: 'Save' })
    await waitFor(() => expect(button).toHaveProperty('disabled', false))

    fireEvent.click(button)

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings/ledgerly' } })
    expect(getAccessToken()).toBeNull()
  })
})
