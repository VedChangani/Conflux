import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../../test/api'
import { PENDING_COUNT, receivedConnection, sentConnection } from '../../test/connections'
import { pageOf } from '../../test/listings'
import { renderApp } from '../../test/renderApp'

const RECEIVED = 'GET /connections/received?page=0&size=12'
const SENT = 'GET /connections/sent?page=0&size=12'
const ACCEPT = 'POST /connections/21/accept'
const REJECT = 'POST /connections/21/reject'
const WITHDRAW = 'DELETE /connections/11'
const LEAKY_DETAIL = 'A connection with status ACCEPTED cannot be rejected. (ConnectionStateException)'
const LATER = '2026-03-09T16:45:00Z'

const noContent = () => new Response(null, { status: 204 })
const count = (requests: RecordedRequest[], key: string) =>
  requests.filter((request) => `${request.method} ${request.path}` === key).length

const acceptButton = () => screen.getByRole('button', { name: 'Accept request from Bob Brown for Pairwise' })
const rejectButton = () => screen.getByRole('button', { name: 'Reject request from Bob Brown for Pairwise' })
const row = () => within(screen.getByRole('article'))

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

async function renderReceived(handlers: Parameters<typeof mockApi>[0]) {
  const api = signedIn({ [RECEIVED]: () => json(pageOf([receivedConnection()])), ...handlers })
  const router = renderApp('/connections/received')
  await screen.findByRole('button', { name: 'Accept request from Bob Brown for Pairwise' })
  return { ...api, router }
}

describe('Accepting a request', () => {
  it('disables both actions while accepting, then updates the row in place', async () => {
    const response = deferred<Response>()
    const { requests } = await renderReceived({
      [ACCEPT]: () => response.promise,
      [PENDING_COUNT]: () => json(pageOf([], { size: 1, totalElements: 1 })),
    })
    await screen.findByRole('link', { name: 'Connections, 1 pending' })

    fireEvent.click(acceptButton())
    fireEvent.click(screen.getByRole('button', { name: 'Accepting…' }))

    const accepting = screen.getByRole('button', { name: 'Accepting…' })
    expect(accepting).toHaveProperty('disabled', true)
    expect(accepting.getAttribute('aria-busy')).toBe('true')
    expect(rejectButton()).toHaveProperty('disabled', true)
    expect(row().getByText('Pending')).toBeTruthy()

    response.resolve(json(receivedConnection({ status: 'ACCEPTED', updatedAt: LATER })))

    expect(await row().findByText('Accepted')).toBeTruthy()
    expect(row().getByText('You accepted this request. You’re connected.')).toBeTruthy()
    expect(row().getByRole('link', { name: 'Open conversation with Bob Brown about Pairwise' })).toBeTruthy()
    expect(row().queryByRole('button')).toBeNull()
    expect(row().getByText('Request accepted.')).toBeTruthy()
    expect(row().getByText('Mar 9, 2026').getAttribute('datetime')).toBe(LATER)

    expect(count(requests, ACCEPT)).toBe(1)
    expect(count(requests, RECEIVED)).toBe(1)
    expect(requests.find((request) => request.path === '/connections/21/accept')?.body).toBeUndefined()
    await waitFor(() => expect(count(requests, PENDING_COUNT)).toBe(2))
  })

  it('accepts from the detail page and shows that nothing more can be done', async () => {
    const { requests } = signedIn({
      'GET /connections/21': () => json(receivedConnection()),
      [ACCEPT]: () => json(receivedConnection({ status: 'ACCEPTED', updatedAt: LATER })),
    })
    renderApp('/connections/21')

    fireEvent.click(await screen.findByRole('button', { name: 'Accept' }))

    const panel = within(screen.getByRole('complementary', { name: 'Request status' }))
    expect(await panel.findByText('You accepted this request. You’re connected.')).toBeTruthy()
    expect(panel.getByRole('link', { name: 'Open conversation' }).getAttribute('href')).toBe('/messages/connection/21')
    expect(panel.queryByRole('button')).toBeNull()
    expect(screen.getAllByText('Accepted').length).toBeGreaterThan(0)
    expect(count(requests, 'GET /connections/21')).toBe(1)
  })
})

describe('Rejecting a request', () => {
  it('asks for confirmation, can be cancelled, and updates the row once rejected', async () => {
    const response = deferred<Response>()
    const { requests } = await renderReceived({ [REJECT]: () => response.promise })

    fireEvent.click(rejectButton())

    const confirm = within(screen.getByRole('group', { name: 'Confirm' }))
    expect(confirm.getByText(/Reject this request\? This can’t be undone/)).toBeTruthy()
    expect(document.activeElement).toBe(confirm.getByRole('button', { name: 'Yes, reject' }))
    expect(count(requests, REJECT)).toBe(0)

    fireEvent.click(confirm.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('group', { name: 'Confirm' })).toBeNull()
    await waitFor(() => expect(document.activeElement).toBe(rejectButton()))

    fireEvent.click(rejectButton())
    fireEvent.click(screen.getByRole('button', { name: 'Yes, reject' }))

    expect(screen.getByRole('button', { name: 'Rejecting…' })).toHaveProperty('disabled', true)
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveProperty('disabled', true)

    response.resolve(json(receivedConnection({ status: 'REJECTED', updatedAt: LATER })))

    expect(await row().findByText('Rejected')).toBeTruthy()
    expect(row().getByText('You rejected this request.')).toBeTruthy()
    expect(row().queryByRole('button')).toBeNull()
    expect(count(requests, REJECT)).toBe(1)
    expect(count(requests, RECEIVED)).toBe(1)
  })

  it('cancels the confirmation with Escape', async () => {
    await renderReceived({})

    fireEvent.click(rejectButton())
    fireEvent.keyDown(screen.getByRole('button', { name: 'Yes, reject' }), { key: 'Escape' })

    expect(screen.queryByRole('group', { name: 'Confirm' })).toBeNull()
    expect(acceptButton()).toBeTruthy()
  })
})

describe('Withdrawing a request', () => {
  it('confirms, withdraws and shows the request as the backend now has it', async () => {
    const { requests } = signedIn({
      [SENT]: () => json(pageOf([sentConnection()])),
      [WITHDRAW]: () => noContent(),
      'GET /connections/11': () => json(sentConnection({ status: 'WITHDRAWN', updatedAt: LATER })),
    })
    renderApp('/connections/sent')

    fireEvent.click(await screen.findByRole('button', { name: 'Withdraw request for Ledgerly' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, withdraw' }))

    expect(await row().findByText('Withdrawn')).toBeTruthy()
    expect(row().getByText('You withdrew this request.')).toBeTruthy()
    expect(row().getByText('Mar 9, 2026')).toBeTruthy()
    expect(row().queryByRole('button')).toBeNull()
    const order = requests.map((request) => `${request.method} ${request.path}`).filter((key) => key.includes('/connections/11'))
    expect(order).toEqual([WITHDRAW, 'GET /connections/11'])
    expect(count(requests, SENT)).toBe(1)
  })

  it('relies on the 204 when the request cannot be read again', async () => {
    signedIn({
      [SENT]: () => json(pageOf([sentConnection()])),
      [WITHDRAW]: () => noContent(),
      'GET /connections/11': () => problem(503, 'Unavailable'),
    })
    renderApp('/connections/sent')

    fireEvent.click(await screen.findByRole('button', { name: 'Withdraw request for Ledgerly' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, withdraw' }))

    expect(await row().findByText('Withdrawn')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('withdraws from the detail page', async () => {
    let withdrawn = false
    signedIn({
      'GET /connections/11': () => json(sentConnection(withdrawn ? { status: 'WITHDRAWN', updatedAt: LATER } : {})),
      [WITHDRAW]: () => {
        withdrawn = true
        return noContent()
      },
    })
    renderApp('/connections/11')

    expect(await screen.findByText('Waiting for the owner’s answer.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Accept' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Withdraw request' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, withdraw' }))

    expect(screen.getByRole('button', { name: 'Withdrawing…' })).toHaveProperty('disabled', true)
    expect(await screen.findByText('You withdrew this request.')).toBeTruthy()
    expect(screen.getByText('No further action is available.')).toBeTruthy()
  })
})

describe('Action failures', () => {
  it.each([
    [403, 'You can’t answer this request. Only the listing owner can, and suspended accounts can’t make changes.'],
    [404, 'This request no longer exists, or you no longer have access to it.'],
  ])('treats a %i as final: explains it and withdraws the actions', async (status, message) => {
    await renderReceived({ [ACCEPT]: () => problem(status, LEAKY_DETAIL) })

    fireEvent.click(acceptButton())

    expect((await screen.findByRole('alert')).textContent).toBe(message)
    expect(row().queryByRole('button')).toBeNull()
    expect(row().getByText('Pending')).toBeTruthy()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it('explains a 403 on withdraw from the requester’s side', async () => {
    signedIn({ [SENT]: () => json(pageOf([sentConnection()])), [WITHDRAW]: () => problem(403, LEAKY_DETAIL) })
    renderApp('/connections/sent')

    fireEvent.click(await screen.findByRole('button', { name: 'Withdraw request for Ledgerly' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, withdraw' }))

    expect((await screen.findByRole('alert')).textContent).toBe(
      'You can’t withdraw this request. Only the person who sent it can, and suspended accounts can’t make changes.',
    )
    expect(screen.queryByRole('button', { name: /Withdraw/ })).toBeNull()
  })

  it('on a 409, reads the request again and shows its real, final status', async () => {
    const { requests } = await renderReceived({
      [REJECT]: () => problem(409, LEAKY_DETAIL),
      'GET /connections/21': () => json(receivedConnection({ status: 'WITHDRAWN', updatedAt: LATER })),
    })

    fireEvent.click(rejectButton())
    fireEvent.click(screen.getByRole('button', { name: 'Yes, reject' }))

    expect((await screen.findByRole('alert')).textContent).toBe(
      'This request has already been answered or withdrawn, so it can’t be changed.',
    )
    expect(await row().findByText('Withdrawn')).toBeTruthy()
    expect(row().getByText('The requester withdrew this request.')).toBeTruthy()
    expect(row().getByText('No further action')).toBeTruthy()
    expect(row().queryByRole('button')).toBeNull()
    expect(count(requests, 'GET /connections/21')).toBe(1)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it.each([
    [429, 'You’re doing that too often. Please wait a while and try again.'],
    [500, 'Something went wrong. Please try again.'],
    [503, 'Something went wrong. Please try again.'],
  ])('keeps the actions after a %i so the user can try again', async (status, message) => {
    const { handlers, requests } = await renderReceived({ [ACCEPT]: () => problem(status, LEAKY_DETAIL) })

    fireEvent.click(acceptButton())

    expect((await screen.findByRole('alert')).textContent).toBe(message)
    expect(acceptButton()).toHaveProperty('disabled', false)
    expect(rejectButton()).toHaveProperty('disabled', false)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[ACCEPT] = () => json(receivedConnection({ status: 'ACCEPTED', updatedAt: LATER }))
    fireEvent.click(acceptButton())

    expect(await row().findByText('Accepted')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(count(requests, ACCEPT)).toBe(2)
  })

  it('reports a network failure', async () => {
    await renderReceived({
      [ACCEPT]: () => {
        throw new TypeError('Failed to fetch')
      },
    })

    fireEvent.click(acceptButton())

    expect((await screen.findByRole('alert')).textContent).toBe(
      'Unable to reach the server. Check your connection and try again.',
    )
    expect(acceptButton()).toHaveProperty('disabled', false)
  })

  it('sends the user to log in, and back here, when the session has expired', async () => {
    const { router } = await renderReceived({ [ACCEPT]: () => problem(401, 'A valid access token is required.') })

    fireEvent.click(acceptButton())

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/connections/received' } })
    expect(getAccessToken()).toBeNull()
  })
})
