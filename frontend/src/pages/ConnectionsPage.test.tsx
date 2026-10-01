import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../test/api'
import { PENDING_COUNT, receivedConnection, sentConnection } from '../test/connections'
import { pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

const RECEIVED = 'GET /connections/received?page=0&size=12'
const SENT = 'GET /connections/sent?page=0&size=12'
const LEAKY_DETAIL = 'JDBC exception: connections.requester_id constraint'

const nav = () => within(screen.getByRole('navigation', { name: 'Main' }))
const tabs = () => within(screen.getByRole('navigation', { name: 'Connection requests' }))
const filters = () => within(screen.getByRole('navigation', { name: 'Filter by status' }))
const results = (name: string) => screen.getByRole('region', { name })
const listRequests = (requests: RecordedRequest[]) =>
  requests.map((request) => request.path).filter((path) => /^\/connections\/(sent|received)\?/.test(path) && !path.endsWith('size=1'))

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

describe('Connections navigation', () => {
  it('sends anonymous visitors through login and back to the requested list', async () => {
    mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      'GET /connections/sent?status=ACCEPTED&page=0&size=12': () => json(pageOf([sentConnection({ status: 'ACCEPTED' })])),
    })
    const router = renderApp('/connections/sent?status=ACCEPTED')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(nav().queryByRole('link', { name: /Connections/ })).toBeNull()
    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 3, name: /Ledgerly/ })).toBeTruthy()
    expect(router.state.location).toMatchObject({ pathname: '/connections/sent', search: '?status=ACCEPTED' })
  })

  it('opens the received tab from /connections', async () => {
    signedIn({ [RECEIVED]: () => json(pageOf([])) })
    const router = renderApp('/connections')

    expect(await screen.findByRole('heading', { level: 1, name: 'Connections' })).toBeTruthy()
    await waitFor(() => expect(router.state.location.pathname).toBe('/connections/received'))
    await waitFor(() => expect(tabs().getByRole('link', { name: 'Received' }).getAttribute('aria-current')).toBe('page'))
    expect(tabs().getByRole('link', { name: 'Sent' }).getAttribute('href')).toBe('/connections/sent')
  })

  it('shows the number of pending received requests, read once', async () => {
    const { requests } = signedIn({ [PENDING_COUNT]: () => json(pageOf([receivedConnection()], { size: 1, totalElements: 3 })) })
    renderApp('/')

    const link = await nav().findByRole('link', { name: 'Connections, 3 pending' })
    expect(link.getAttribute('href')).toBe('/connections')
    expect(link.textContent).toBe('Connections3')
    expect(requests.filter((request) => request.path === PENDING_COUNT.slice(4))).toHaveLength(1)
    expect(requests.find((request) => request.path === PENDING_COUNT.slice(4))?.headers.get('Authorization')).toBe(
      'Bearer stored-token',
    )
  })

  it('shows no count when nothing is pending or the count cannot be read', async () => {
    signedIn({ [PENDING_COUNT]: () => problem(503, 'Unavailable'), 'GET /listings?page=0&size=6': () => json(pageOf([])) })
    renderApp('/')

    expect(await nav().findByRole('link', { name: 'Connections' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('drops the count and the links on logout', async () => {
    signedIn({ [PENDING_COUNT]: () => json(pageOf([], { size: 1, totalElements: 2 })) })
    renderApp('/')
    await nav().findByRole('link', { name: 'Connections, 2 pending' })

    fireEvent.click(nav().getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(nav().queryByRole('link', { name: /Connections/ })).toBeNull())
    expect(nav().queryByRole('link', { name: 'Saved' })).toBeNull()
  })
})

describe('Received requests', () => {
  it('shows a loading state, then the requests', async () => {
    const response = deferred<Response>()
    signedIn({ [RECEIVED]: () => response.promise })
    renderApp('/connections/received')

    const region = await screen.findByRole('region', { name: 'Requests for your listings' })
    expect(within(region).getByRole('status').textContent).toBe('Loading requests…')
    expect(region.getAttribute('aria-busy')).toBe('true')

    response.resolve(
      json(
        pageOf([
          receivedConnection(),
          receivedConnection({ id: 22, status: 'ACCEPTED', updatedAt: '2026-03-05T10:00:00Z' }),
        ]),
      ),
    )

    const [pending, accepted] = await within(region).findAllByRole('article')
    expect(region.getAttribute('aria-busy')).toBe('false')
    expect(within(region).getByRole('status').textContent).toBe('2 requests')

    const row = within(pending)
    expect(row.getByText('Pending')).toBeTruthy()
    expect(row.getByText('Bob Brown')).toBeTruthy()
    expect(row.getByText('@bob')).toBeTruthy()
    expect(row.getByText('Waiting for your answer.')).toBeTruthy()
    expect(row.getByText('Mar 2, 2026').getAttribute('datetime')).toBe('2026-03-02T09:30:00Z')
    const link = row.getByRole('link', { name: 'Pairwise, request from Bob Brown' })
    expect(link.getAttribute('href')).toBe('/connections/21')
    expect(row.getAllByRole('link')).toHaveLength(1)
    expect(row.getByRole('button', { name: 'Accept request from Bob Brown for Pairwise' })).toBeTruthy()
    expect(row.getByRole('button', { name: 'Reject request from Bob Brown for Pairwise' })).toBeTruthy()

    const done = within(accepted)
    expect(done.getByText('Accepted')).toBeTruthy()
    expect(done.getByRole('link', { name: 'Open conversation with Bob Brown about Pairwise' }).getAttribute('href')).toBe(
      '/messages/connection/22',
    )
    expect(done.getByText('Mar 5, 2026').getAttribute('datetime')).toBe('2026-03-05T10:00:00Z')
    expect(done.queryByRole('button')).toBeNull()

    // Ids and private fields are not rendered.
    expect(region.textContent).not.toContain('21')
    expect(region.textContent).not.toContain(ACCOUNT.email)
  })

  it('shows an empty state', async () => {
    signedIn({ [RECEIVED]: () => json(pageOf([])) })
    renderApp('/connections/received')

    expect(await screen.findByRole('heading', { name: 'No requests yet' })).toBeTruthy()
    expect(within(results('Requests for your listings')).getByRole('status').textContent).toBe('0 requests')
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).toBeNull()
  })

  it('shows an error without backend details, with a working retry', async () => {
    const { handlers } = signedIn({ [RECEIVED]: () => problem(500, LEAKY_DETAIL) })
    renderApp('/connections/received')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load your requests')
    expect(alert.textContent).toContain('Something went wrong. Please try again.')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[RECEIVED] = () => json(pageOf([receivedConnection()]))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 3, name: /Pairwise/ })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('reports a network failure', async () => {
    signedIn({
      [RECEIVED]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderApp('/connections/received')

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Unable to reach the server. Check your connection and try again.',
    )
  })

  it('filters by status through the URL and the backend', async () => {
    const { requests } = signedIn({
      [RECEIVED]: () => json(pageOf([receivedConnection(), receivedConnection({ id: 22, status: 'REJECTED' })], { totalElements: 30 })),
      'GET /connections/received?status=REJECTED&page=0&size=12': () =>
        json(pageOf([receivedConnection({ id: 22, status: 'REJECTED' })])),
    })
    const router = renderApp('/connections/received?page=2')
    await waitFor(() => expect(listRequests(requests)).toEqual(['/connections/received?page=1&size=12']))

    const statuses = filters().getAllByRole('link')
    expect(statuses.map((link) => link.textContent)).toEqual(['All', 'Pending', 'Accepted', 'Rejected', 'Withdrawn'])
    expect(filters().getByRole('link', { name: 'All' }).getAttribute('aria-current')).toBe('true')

    fireEvent.click(filters().getByRole('link', { name: 'Rejected' }))

    // A new filter starts again at the first page.
    await waitFor(() => expect(router.state.location.search).toBe('?status=REJECTED'))
    await waitFor(() => expect(listRequests(requests).at(-1)).toBe('/connections/received?status=REJECTED&page=0&size=12'))
    expect(filters().getByRole('link', { name: 'Rejected' }).getAttribute('aria-current')).toBe('true')
    expect(filters().getByRole('link', { name: 'All' }).getAttribute('aria-current')).toBeNull()
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(1))
    expect(screen.getByText('You rejected this request.')).toBeTruthy()
  })

  it('shows a filtered empty state that can clear the filter', async () => {
    signedIn({
      'GET /connections/received?status=WITHDRAWN&page=0&size=12': () => json(pageOf([])),
      [RECEIVED]: () => json(pageOf([receivedConnection()])),
    })
    const router = renderApp('/connections/received?status=WITHDRAWN')

    expect(await screen.findByRole('heading', { name: 'No withdrawn requests' })).toBeTruthy()
    fireEvent.click(screen.getByRole('link', { name: 'Show all requests' }))

    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(await screen.findByRole('heading', { level: 3, name: /Pairwise/ })).toBeTruthy()
  })

  it('explains an invalid status filter instead of showing the backend error', async () => {
    signedIn({
      'GET /connections/received?status=MAYBE&page=0&size=12': () =>
        problem(400, "Failed to convert 'status' with value: 'MAYBE'"),
    })
    renderApp('/connections/received?status=MAYBE')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('This filter isn’t valid')
    expect(alert.textContent).not.toContain('Failed to convert')
    expect(within(alert).getByRole('link', { name: 'Show all requests' }).getAttribute('href')).toBe(
      '/connections/received',
    )
  })

  it('paginates with the backend page metadata', async () => {
    const pageOne = Array.from({ length: 12 }, (_, i) =>
      receivedConnection({ id: 100 + i, listing: { ...receivedConnection().listing, title: `A${i}` } }),
    )
    const pageTwo = Array.from({ length: 2 }, (_, i) =>
      receivedConnection({ id: 200 + i, listing: { ...receivedConnection().listing, title: `B${i}` } }),
    )
    const { requests } = signedIn({
      'GET /connections/received?status=PENDING&page=0&size=12': () => json(pageOf(pageOne, { totalElements: 14 })),
      'GET /connections/received?status=PENDING&page=1&size=12': () =>
        json(pageOf(pageTwo, { page: 1, totalElements: 14 })),
    })
    const router = renderApp('/connections/received?status=PENDING')
    await screen.findByRole('heading', { level: 3, name: /^A0,/ })
    const status = () => within(results('Requests for your listings')).getByRole('status')
    expect(status().textContent).toBe('Showing 1–12 of 14 requests')

    const pagination = within(screen.getByRole('navigation', { name: 'Pagination' }))
    fireEvent.click(pagination.getByRole('link', { name: /Next/ }))

    expect(await screen.findByRole('heading', { level: 3, name: /^B0,/ })).toBeTruthy()
    expect(router.state.location.search).toBe('?status=PENDING&page=2')
    expect(listRequests(requests).at(-1)).toBe('/connections/received?status=PENDING&page=1&size=12')
    expect(status().textContent).toBe('Showing 13–14 of 14 requests')
    expect(document.activeElement).toBe(screen.getByRole('heading', { level: 2, name: 'Requests for your listings' }))
  })

  it('handles a page past the end', async () => {
    signedIn({ 'GET /connections/received?page=5&size=12': () => json(pageOf([], { page: 5, totalElements: 13 })) })
    renderApp('/connections/received?page=6')

    expect(await screen.findByRole('heading', { name: 'There is no page 6' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Go to page 2' }).getAttribute('href')).toBe('/connections/received?page=2')
  })
})

describe('Sent requests', () => {
  it('shows a loading state, then the requests with the owner and a withdraw action', async () => {
    const response = deferred<Response>()
    signedIn({ [SENT]: () => response.promise })
    renderApp('/connections/sent')

    const region = await screen.findByRole('region', { name: 'Requests you’ve sent' })
    expect(within(region).getByRole('status').textContent).toBe('Loading requests…')
    expect(tabs().getByRole('link', { name: 'Sent' }).getAttribute('aria-current')).toBe('page')

    response.resolve(
      json(
        pageOf([
          sentConnection(),
          sentConnection({ id: 12, status: 'WITHDRAWN' }),
          sentConnection({ id: 13, status: 'REJECTED', listing: { ...sentConnection().listing, status: 'ARCHIVED' } }),
        ]),
      ),
    )

    const [pending, withdrawn, rejected] = await within(region).findAllByRole('article')
    expect(within(pending).getByText('To')).toBeTruthy()
    expect(within(pending).getByText('Alice Anders')).toBeTruthy()
    expect(within(pending).getByText('Waiting for the owner’s answer.')).toBeTruthy()
    expect(within(pending).getByRole('link', { name: 'Ledgerly, request to Alice Anders' })).toBeTruthy()
    expect(within(pending).getByRole('button', { name: 'Withdraw request for Ledgerly' })).toBeTruthy()
    expect(within(pending).queryByRole('button', { name: /Accept/ })).toBeNull()

    expect(within(withdrawn).getByText('Withdrawn')).toBeTruthy()
    expect(within(withdrawn).getByText('You withdrew this request.')).toBeTruthy()
    expect(within(withdrawn).queryByRole('button')).toBeNull()

    expect(within(rejected).getByText('Rejected')).toBeTruthy()
    expect(rejected.textContent).toContain('The listing is no longer on the marketplace.')
    expect(rejected.textContent).not.toContain('ARCHIVED')
  })

  it('shows an empty state that leads to the marketplace', async () => {
    signedIn({ [SENT]: () => json(pageOf([])) })
    renderApp('/connections/sent')

    expect(await screen.findByRole('heading', { name: 'No requests sent yet' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Browse listings' }).getAttribute('href')).toBe('/listings')
  })

  it('shows an error with a working retry', async () => {
    const { handlers } = signedIn({ [SENT]: () => problem(502, LEAKY_DETAIL) })
    renderApp('/connections/sent')

    const alert = await screen.findByRole('alert')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    handlers[SENT] = () => json(pageOf([sentConnection()]))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 3, name: /Ledgerly/ })).toBeTruthy()
  })

  it('switches between the tabs with separate requests', async () => {
    const { requests } = signedIn({
      [RECEIVED]: () => json(pageOf([receivedConnection()])),
      [SENT]: () => json(pageOf([sentConnection()])),
    })
    renderApp('/connections/received')
    await screen.findByRole('heading', { level: 3, name: /Pairwise/ })

    fireEvent.click(tabs().getByRole('link', { name: 'Sent' }))

    expect(await screen.findByRole('heading', { level: 3, name: /Ledgerly/ })).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 3, name: /Pairwise/ })).toBeNull()
    expect(listRequests(requests)).toEqual(['/connections/received?page=0&size=12', '/connections/sent?page=0&size=12'])
  })
})
