import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { receivedConnection, sentConnection } from '../test/connections'
import { pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

const LEAKY_DETAIL = 'org.springframework.orm.ObjectRetrievalFailureException'

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

const section = (name: string) => within(screen.getByRole('region', { name }))
const statusPanel = () => within(screen.getByRole('complementary', { name: 'Request status' }))

describe('Connection detail', () => {
  it('shows a received request with exactly the response fields, and the owner actions', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({
      'GET /connections/21': () => response.promise,
    })
    renderApp('/connections/21')

    expect((await screen.findByText('Loading request…')).getAttribute('role')).toBe('status')
    response.resolve(json(receivedConnection({ updatedAt: '2026-03-04T08:15:00Z' })))

    expect(await screen.findByRole('heading', { level: 1, name: 'Pairwise' })).toBeTruthy()
    expect(screen.getByText('Received request')).toBeTruthy()
    expect(document.title).toBe('Request for Pairwise · Conflux')

    const listing = section('Listing')
    expect(listing.getByText('Find a technical co-founder.')).toBeTruthy()
    expect(listing.getByRole('link', { name: /View listing/ }).getAttribute('href')).toBe('/listings/pairwise')

    const people = section('People')
    expect(people.getByText('Requester')).toBeTruthy()
    expect(people.getByText('Bob Brown')).toBeTruthy()
    expect(people.getByText('@bob')).toBeTruthy()
    expect(people.getByText('Listing owner')).toBeTruthy()
    expect(people.getByText('@ada')).toBeTruthy()
    // "You" marks the signed-in user's side, from the verified account and the response.
    expect(people.getAllByText('You')).toHaveLength(1)
    expect(people.getByText('@ada').closest('.participant')?.textContent).toContain('You')

    const timeline = section('Timeline')
    expect(timeline.getByText('Sent').nextElementSibling?.querySelector('time')?.getAttribute('datetime')).toBe(
      '2026-03-02T09:30:00Z',
    )
    expect(timeline.getByText('Last updated').nextElementSibling?.querySelector('time')?.getAttribute('datetime')).toBe(
      '2026-03-04T08:15:00Z',
    )

    expect(statusPanel().getByText('Waiting for your answer.')).toBeTruthy()
    expect(statusPanel().getByRole('button', { name: 'Accept' })).toBeTruthy()
    expect(statusPanel().getByRole('button', { name: 'Reject' })).toBeTruthy()
    expect(statusPanel().queryByRole('button', { name: /Withdraw/ })).toBeNull()

    // No ids, emails or raw enum values.
    const text = document.body.textContent ?? ''
    expect(text).not.toContain('PUBLISHED')
    expect(text).not.toContain('PENDING')
    expect(text).not.toContain(ACCOUNT.email)
    expect(requests.find((request) => request.path === '/connections/21')?.headers.get('Authorization')).toBe(
      'Bearer stored-token',
    )
  })

  it('shows a sent request from the requester’s side', async () => {
    signedIn({ 'GET /connections/11': () => json(sentConnection()) })
    renderApp('/connections/11')

    expect(await screen.findByText('Sent request')).toBeTruthy()
    expect(statusPanel().getByText('Waiting for the owner’s answer.')).toBeTruthy()
    expect(statusPanel().getByRole('button', { name: 'Withdraw request' })).toBeTruthy()
    expect(statusPanel().queryByRole('button', { name: 'Accept' })).toBeNull()
    expect(section('People').getByText('@ada').closest('.participant')?.textContent).toContain('Requester')
  })

  it('offers the conversation, and no connection actions, for an ACCEPTED request', async () => {
    signedIn({ 'GET /connections/21': () => json(receivedConnection({ status: 'ACCEPTED' })) })
    renderApp('/connections/21')

    expect(await statusPanelText('You accepted this request. You’re connected.')).toBeTruthy()
    expect(statusPanel().getByRole('link', { name: 'Open conversation' }).getAttribute('href')).toBe(
      '/messages/connection/21',
    )
    expect(statusPanel().queryByRole('button')).toBeNull()
  })

  it.each([
    ['REJECTED', 'You rejected this request.'],
    ['WITHDRAWN', 'The requester withdrew this request.'],
  ] as const)('offers no actions for a %s request', async (status, summary) => {
    signedIn({ 'GET /connections/21': () => json(receivedConnection({ status })) })
    renderApp('/connections/21')

    expect(await statusPanelText(summary)).toBeTruthy()
    expect(statusPanel().getByText('No further action is available.')).toBeTruthy()
    expect(statusPanel().queryByRole('button')).toBeNull()
  })

  it('notes a listing that is no longer on the marketplace without linking to it', async () => {
    signedIn({
      'GET /connections/11': () =>
        json(sentConnection({ status: 'ACCEPTED', listing: { ...sentConnection().listing, status: 'SUSPENDED' } })),
    })
    renderApp('/connections/11')

    expect(await screen.findByText('This listing is no longer on the marketplace.')).toBeTruthy()
    expect(section('Listing').queryByRole('link')).toBeNull()
    expect(document.body.textContent).not.toContain('SUSPENDED')
  })

  it('shows a not-found page for a request that is missing or not the user’s', async () => {
    signedIn({ 'GET /connections/404': () => problem(404, 'Connection not found.') })
    renderApp('/connections/404')

    expect(await screen.findByRole('heading', { level: 1, name: 'Request not found' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Go to connections' }).getAttribute('href')).toBe('/connections')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it.each(['abc', '0', '1.5', '99999999999999999999'])('treats the id %s as not found without a request', async (id) => {
    const { requests } = signedIn({})
    renderApp(`/connections/${id}`)

    expect(await screen.findByRole('heading', { level: 1, name: 'Request not found' })).toBeTruthy()
    expect(requests.filter((request) => request.path.startsWith(`/connections/${id}`))).toHaveLength(0)
  })

  it('shows other failures without backend details, with a working retry', async () => {
    const { handlers } = signedIn({ 'GET /connections/21': () => problem(500, LEAKY_DETAIL) })
    renderApp('/connections/21')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load this request')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers['GET /connections/21'] = () => json(receivedConnection())
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Pairwise' })).toBeTruthy()
  })

  it('returns to the list it was opened from', async () => {
    signedIn({
      'GET /connections/received?status=PENDING&page=0&size=12': () => json(pageOf([receivedConnection()])),
      'GET /connections/21': () => json(receivedConnection()),
    })
    const router = renderApp('/connections/received?status=PENDING')

    fireEvent.click(await screen.findByRole('link', { name: 'Pairwise, request from Bob Brown' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Pairwise' })).toBeTruthy()

    fireEvent.click(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link', { name: /Back to connections/ }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/connections/received'))
    expect(router.state.location.search).toBe('?status=PENDING')
  })

  it('returns to the sent list for a request opened directly by its requester', async () => {
    signedIn({ 'GET /connections/11': () => json(sentConnection()) })
    renderApp('/connections/11')

    await screen.findByText('Sent request')
    const back = within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link')
    expect(back.getAttribute('href')).toBe('/connections/sent')
  })

  it('sends anonymous visitors through login and back to the request', async () => {
    mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      'GET /connections/21': () => json(receivedConnection()),
    })
    const router = renderApp('/connections/21')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Pairwise' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/connections/21')
  })

  it('sends the user to log in when the token is rejected while loading', async () => {
    signedIn({ 'GET /connections/21': () => problem(401, 'A valid access token is required.') })
    const router = renderApp('/connections/21')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/connections/21' } })
  })
})

async function statusPanelText(text: string) {
  await screen.findByRole('complementary', { name: 'Request status' })
  return statusPanel().getByText(text)
}
