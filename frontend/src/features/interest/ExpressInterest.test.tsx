import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../../test/api'
import { listingDetail, pageOf } from '../../test/listings'
import { renderApp } from '../../test/renderApp'
import type { Connection, ConnectionStatus } from './types'

const DETAIL = 'GET /listings/ledgerly'
const INTEREST = 'POST /listings/1/interest'
const LEAKY_DETAIL = 'ConnectionService.expressInterest failed: requester_id=42 listing_id=1'

function connection(status: ConnectionStatus): Connection {
  return {
    id: 99,
    status,
    createdAt: '2026-03-01T12:00:00Z',
    updatedAt: '2026-03-01T12:00:00Z',
    listing: { id: 1, slug: 'ledgerly', title: 'Ledgerly', shortPitch: 'Close the books faster.', status: 'PUBLISHED' },
    requester: { id: ACCOUNT.id, username: ACCOUNT.username, displayName: ACCOUNT.displayName },
    owner: { id: 7, username: 'alice', displayName: 'Alice Anders' },
  }
}

const interestCalls = (requests: RecordedRequest[]) =>
  requests.filter((request) => `${request.method} ${request.path}` === INTEREST)
const summary = () => within(screen.getByRole('complementary', { name: 'Listing summary' }))

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({
    'GET /auth/me': () => json(ACCOUNT),
    [DETAIL]: () => json(listingDetail()),
    'GET /saved-listings?page=0&size=50': () => json(pageOf([], { size: 50 })),
    ...handlers,
  })
}

async function expressInterest() {
  fireEvent.click(await screen.findByRole('button', { name: 'Express interest' }))
}

describe('Express interest', () => {
  it('sends a new request and shows it as pending, not as a connection', async () => {
    const { requests } = signedIn({ [INTEREST]: () => json(connection('PENDING'), 201) })
    renderApp('/listings/ledgerly')

    await expressInterest()

    const outcome = await screen.findByRole('status')
    expect(outcome.textContent).toContain('Pending')
    expect(outcome.textContent).toContain('Interest sent')
    expect(outcome.textContent).toContain('You’ll be connected only if they accept it.')
    expect(outcome.textContent).not.toMatch(/you’re connected/i)
    await waitFor(() => expect(document.activeElement).toBe(outcome))
    expect(screen.queryByRole('button', { name: 'Express interest' })).toBeNull()

    const [request] = interestCalls(requests)
    expect(interestCalls(requests)).toHaveLength(1)
    expect(request.headers.get('Authorization')).toBe('Bearer stored-token')
    expect(request.body).toBeUndefined()
    expect(document.body.textContent).not.toContain('99')
  })

  it('reports an existing pending request', async () => {
    signedIn({ [INTEREST]: () => json(connection('PENDING'), 200) })
    renderApp('/listings/ledgerly')

    await expressInterest()

    const outcome = await screen.findByRole('status')
    expect(outcome.textContent).toContain('Interest already sent')
    expect(outcome.textContent).toContain('still waiting for the owner’s response')
  })

  it('reports an accepted request as a connection', async () => {
    signedIn({ [INTEREST]: () => json(connection('ACCEPTED'), 200) })
    renderApp('/listings/ledgerly')

    await expressInterest()

    const outcome = await screen.findByRole('status')
    expect(outcome.textContent).toContain('Connected')
    expect(outcome.textContent).toContain('Request accepted')
  })

  it('disables the button and sends one request while sending', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({ [INTEREST]: () => response.promise })
    renderApp('/listings/ledgerly')

    const button = await screen.findByRole('button', { name: 'Express interest' })
    fireEvent.click(button)
    fireEvent.click(button)

    expect(button.textContent).toBe('Sending interest…')
    expect(button).toHaveProperty('disabled', true)
    expect(button.getAttribute('aria-busy')).toBe('true')
    expect(screen.queryByText('Interest sent')).toBeNull()

    response.resolve(json(connection('PENDING'), 201))

    expect(await screen.findByText('Interest sent')).toBeTruthy()
    expect(interestCalls(requests)).toHaveLength(1)
  })

  it('offers no interest action on your own listing', async () => {
    const { requests } = signedIn({
      [DETAIL]: () => json(listingDetail({ owner: { id: ACCOUNT.id, username: 'ada', displayName: 'Ada Lovelace' } })),
    })
    renderApp('/listings/ledgerly')

    await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })
    expect(summary().getByText('This is your listing.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /express interest/i })).toBeNull()
    expect(interestCalls(requests)).toHaveLength(0)
  })

  it('sends anonymous visitors to log in and back to the listing', async () => {
    const { requests } = mockApi({
      [DETAIL]: () => json(listingDetail()),
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      'GET /saved-listings?page=0&size=50': () => json(pageOf([], { size: 50 })),
    })
    const router = renderApp('/listings/ledgerly')

    fireEvent.click(await screen.findByRole('button', { name: 'Log in to express interest' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings/ledgerly' } })
    expect(interestCalls(requests)).toHaveLength(0)

    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('button', { name: 'Express interest' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/listings/ledgerly')
  })
})

describe('Express interest failures', () => {
  it.each([
    [409, 'Interest can’t be sent', 'an earlier request was declined or withdrawn, or the listing is your own'],
    [404, 'Listing unavailable', 'This listing is no longer available on the marketplace.'],
    [403, 'Interest not sent', 'Your account is suspended, so you can’t express interest in listings.'],
  ])('treats a %i as final and withdraws the action', async (status, title, message) => {
    signedIn({ [INTEREST]: () => problem(status, LEAKY_DETAIL) })
    renderApp('/listings/ledgerly')

    await expressInterest()

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain(title)
    expect(alert.textContent).toContain(message)
    expect(screen.queryByRole('button', { name: 'Express interest' })).toBeNull()
    expect(screen.queryByRole('status')).toBeNull()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    await waitFor(() => expect(document.activeElement?.contains(alert)).toBe(true))
  })

  it.each([
    [429, 'You’ve expressed interest in a lot of listings recently. Please try again later.'],
    [500, 'Something went wrong. Please try again.'],
  ])('lets the user try again after a %i', async (status, message) => {
    const { handlers, requests } = signedIn({ [INTEREST]: () => problem(status, LEAKY_DETAIL) })
    renderApp('/listings/ledgerly')

    await expressInterest()

    expect((await screen.findByRole('alert')).textContent).toContain(message)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    const button = screen.getByRole('button', { name: 'Express interest' })
    expect(button).toHaveProperty('disabled', false)

    handlers[INTEREST] = () => json(connection('PENDING'), 201)
    fireEvent.click(button)

    expect(await screen.findByText('Interest sent')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(interestCalls(requests)).toHaveLength(2)
  })

  it('reports a network failure', async () => {
    signedIn({
      [INTEREST]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderApp('/listings/ledgerly')

    await expressInterest()

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Unable to reach the server. Check your connection and try again.',
    )
    expect(screen.getByRole('button', { name: 'Express interest' })).toBeTruthy()
  })

  it('treats an unexpected closed request in a 200 as final', async () => {
    signedIn({ [INTEREST]: () => json(connection('WITHDRAWN'), 200) })
    renderApp('/listings/ledgerly')

    await expressInterest()

    expect((await screen.findByRole('alert')).textContent).toContain('Interest can’t be sent')
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('sends the user to log in when the session has expired', async () => {
    signedIn({ [INTEREST]: () => problem(401, 'A valid access token is required.') })
    const router = renderApp('/listings/ledgerly')

    await expressInterest()

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/listings/ledgerly' } })
    expect(getAccessToken()).toBeNull()
  })
})
