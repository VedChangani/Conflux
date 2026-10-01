import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { ALICE, receivedConnection } from '../test/connections'
import { pageOf } from '../test/listings'
import { conversation, messageFromBob } from '../test/messages'
import { renderApp } from '../test/renderApp'

const LIST = 'GET /conversations?page=0&size=20'
const LEAKY_DETAIL = 'could not extract ResultSet; SQL [select c from Conversation c]'

const sidebar = () => within(screen.getByRole('region', { name: 'Conversations' }))
const findSidebar = async () => within(await screen.findByRole('region', { name: 'Conversations' }))
const layout = () => document.querySelector('.messages-layout')

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

const ALICE_CHAT = conversation({
  id: 32,
  connectionId: 11,
  otherParticipant: ALICE,
  listing: { id: 1, slug: 'ledgerly', title: 'Ledgerly', shortPitch: 'Close the books faster.' },
  lastMessagePreview: null,
  lastMessageAt: null,
  createdAt: '2026-02-20T08:00:00Z',
})

describe('Messages navigation', () => {
  it('is in the navigation for signed-in users only', async () => {
    signedIn({ [LIST]: () => json(pageOf([], { size: 20 })) })
    renderApp('/messages')

    const nav = within(screen.getByRole('navigation', { name: 'Main' }))
    const link = await nav.findByRole('link', { name: 'Messages' })
    expect(link.getAttribute('href')).toBe('/messages')
    expect(link.getAttribute('aria-current')).toBe('page')

    fireEvent.click(nav.getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(nav.queryByRole('link', { name: 'Messages' })).toBeNull())
  })

  it('sends anonymous visitors through login and back to the conversation', async () => {
    mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      [LIST]: () => json(pageOf([conversation()], { size: 20 })),
      'GET /conversations/31': () => json(conversation()),
      'GET /conversations/31/messages?page=0&size=50': () => json(pageOf([], { size: 50 })),
    })
    const router = renderApp('/messages/31')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/messages/31')
  })
})

describe('Conversations list', () => {
  it('shows a loading state, then the conversations in the backend’s order', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({ [LIST]: () => response.promise })
    renderApp('/messages')

    expect(await screen.findByRole('heading', { level: 1, name: 'Messages' })).toBeTruthy()
    expect(sidebar().getByRole('status').textContent).toBe('Loading conversations…')
    expect(screen.getByRole('region', { name: 'Conversations' }).getAttribute('aria-busy')).toBe('true')

    response.resolve(json(pageOf([ALICE_CHAT, conversation()], { size: 20 })))

    const links = await (await findSidebar()).findAllByRole('link', { name: /, / })
    expect(links.map((link) => link.getAttribute('aria-label'))).toEqual(['Alice Anders, Ledgerly', 'Bob Brown, Pairwise'])
    expect(sidebar().getByRole('status').textContent).toBe('2 conversations')

    const bob = within(links[1])
    expect(bob.getByText('Happy to talk on Thursday.')).toBeTruthy()
    expect(links[1].getAttribute('aria-describedby')).toBe(bob.getByText('Happy to talk on Thursday.').id)
    expect(bob.getByText('Pairwise')).toBeTruthy()
    expect(links[1].querySelector('time')?.getAttribute('datetime')).toBe('2026-03-03T10:00:00Z')
    expect(links[1].getAttribute('href')).toBe('/messages/31')

    expect(within(links[0]).getByText('No messages yet')).toBeTruthy()
    expect(links[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-02-20T08:00:00Z')

    expect(requests.find((request) => request.path === '/conversations?page=0&size=20')?.headers.get('Authorization')).toBe(
      'Bearer stored-token',
    )
    const text = screen.getByRole('region', { name: 'Conversations' }).textContent ?? ''
    expect(text).not.toContain('31')
    expect(text).not.toContain(ACCOUNT.email)
  })

  it('shows an empty state that points to connections', async () => {
    signedIn({ [LIST]: () => json(pageOf([], { size: 20 })) })
    renderApp('/messages')

    expect(await (await findSidebar()).findByText('No conversations yet')).toBeTruthy()
    expect(sidebar().getByText('A conversation opens when a connection request is accepted.')).toBeTruthy()
    expect(sidebar().getByRole('link', { name: 'Go to connections' }).getAttribute('href')).toBe('/connections')
  })

  it('shows an error without backend details, with a working retry', async () => {
    const { handlers } = signedIn({ [LIST]: () => problem(500, LEAKY_DETAIL) })
    renderApp('/messages')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load your conversations')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[LIST] = () => json(pageOf([conversation()], { size: 20 }))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await (await findSidebar()).findByRole('link', { name: 'Bob Brown, Pairwise' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('reports a network failure', async () => {
    signedIn({
      [LIST]: () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderApp('/messages')

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Unable to reach the server. Check your connection and try again.',
    )
  })

  it('paginates with the backend page metadata', async () => {
    const pageOne = Array.from({ length: 20 }, (_, i) =>
      conversation({ id: 100 + i, otherParticipant: { id: 200 + i, username: `u${i}`, displayName: `Person ${i}` } }),
    )
    const { requests } = signedIn({
      [LIST]: () => json(pageOf(pageOne, { size: 20, totalElements: 21 })),
      'GET /conversations?page=1&size=20': () => json(pageOf([ALICE_CHAT], { page: 1, size: 20, totalElements: 21 })),
    })
    const router = renderApp('/messages')
    await (await findSidebar()).findByRole('link', { name: 'Person 0, Pairwise' })

    const pagination = within(screen.getByRole('navigation', { name: 'Pagination' }))
    expect(pagination.getByText('Page 1 of 2')).toBeTruthy()
    fireEvent.click(pagination.getByRole('link', { name: /Next/ }))

    expect(await (await findSidebar()).findByRole('link', { name: 'Alice Anders, Ledgerly' })).toBeTruthy()
    expect(router.state.location.search).toBe('?page=2')
    expect(requests.at(-1)?.path).toBe('/conversations?page=1&size=20')
    expect(sidebar().getByRole('link', { name: 'Alice Anders, Ledgerly' }).getAttribute('href')).toBe('/messages/32?page=2')
  })

  it('handles a page past the end', async () => {
    signedIn({ 'GET /conversations?page=4&size=20': () => json(pageOf([], { page: 4, size: 20, totalElements: 3 })) })
    renderApp('/messages?page=5')

    expect(await (await findSidebar()).findByText('There is no page 5')).toBeTruthy()
    expect(sidebar().getByRole('link', { name: 'Go to page 1' }).getAttribute('href')).toBe('/messages')
  })
})

describe('Messages layout', () => {
  it('shows the list on its own at /messages, with a prompt for wide screens', async () => {
    signedIn({ [LIST]: () => json(pageOf([conversation()], { size: 20 })) })
    renderApp('/messages')

    await (await findSidebar()).findByRole('link', { name: 'Bob Brown, Pairwise' })
    expect(layout()?.getAttribute('data-pane')).toBe('list')
    expect(screen.getByText('Select a conversation')).toBeTruthy()
  })

  it('opens a conversation next to the list, marking it as current', async () => {
    signedIn({
      [LIST]: () => json(pageOf([ALICE_CHAT, conversation()], { size: 20 })),
      'GET /conversations/31': () => json(conversation()),
      'GET /conversations/31/messages?page=0&size=50': () => json(pageOf([messageFromBob()], { size: 50 })),
    })
    const router = renderApp('/messages')

    fireEvent.click(await (await findSidebar()).findByRole('link', { name: 'Bob Brown, Pairwise' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/messages/31')
    expect(layout()?.getAttribute('data-pane')).toBe('conversation')
    expect(sidebar().getByRole('link', { name: 'Bob Brown, Pairwise' }).getAttribute('aria-current')).toBe('page')
    expect(sidebar().getByRole('link', { name: 'Alice Anders, Ledgerly' }).getAttribute('aria-current')).toBeNull()

    fireEvent.click(screen.getByRole('link', { name: /All conversations/ }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/messages'))
    expect(layout()?.getAttribute('data-pane')).toBe('list')
  })
})

describe('From a connection to its conversation', () => {
  it('opens the conversation of an accepted connection', async () => {
    const { requests } = signedIn({
      'GET /connections/21': () => json(receivedConnection({ status: 'ACCEPTED' })),
      [LIST]: () => json(pageOf([conversation()], { size: 20 })),
      'GET /conversations?page=0&size=50': () =>
        json(pageOf([ALICE_CHAT], { size: 50, totalElements: 51 })),
      'GET /conversations?page=1&size=50': () => json(pageOf([conversation()], { page: 1, size: 50, totalElements: 51 })),
      'GET /conversations/31': () => json(conversation()),
      'GET /conversations/31/messages?page=0&size=50': () => json(pageOf([], { size: 50 })),
    })
    const router = renderApp('/connections/21')

    fireEvent.click(await screen.findByRole('link', { name: 'Open conversation' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/messages/31')
    expect(requests.filter((request) => request.path.endsWith('size=50') && request.path.startsWith('/conversations?'))).toHaveLength(2)

    await router.navigate(-1)
    await waitFor(() => expect(router.state.location.pathname).toBe('/connections/21'))
  })

  it('explains when no conversation exists for the connection', async () => {
    signedIn({
      [LIST]: () => json(pageOf([], { size: 20 })),
      'GET /conversations?page=0&size=50': () => json(pageOf([ALICE_CHAT], { size: 50 })),
    })
    renderApp('/messages/connection/21')

    expect(await screen.findByRole('heading', { name: 'No conversation found' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'All conversations' }).getAttribute('href')).toBe('/messages')
  })

  it('offers a retry when the lookup fails', async () => {
    const { handlers } = signedIn({
      [LIST]: () => json(pageOf([], { size: 20 })),
      'GET /conversations?page=0&size=50': () => problem(503, LEAKY_DETAIL),
    })
    renderApp('/messages/connection/21')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t open this conversation')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers['GET /conversations?page=0&size=50'] = () => json(pageOf([conversation()], { size: 50 }))
    handlers['GET /conversations/31'] = () => json(conversation())
    handlers['GET /conversations/31/messages?page=0&size=50'] = () => json(pageOf([], { size: 50 }))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
  })
})
