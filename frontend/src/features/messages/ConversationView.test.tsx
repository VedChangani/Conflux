import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../../test/api'
import { pageOf } from '../../test/listings'
import { conversation, messageFromBob, messageFromMe } from '../../test/messages'
import { renderApp } from '../../test/renderApp'

const DETAIL = 'GET /conversations/31'
const MESSAGES = 'GET /conversations/31/messages?page=0&size=50'
const LEAKY_DETAIL = 'Conversation 31 not visible to user 42'

const thread = () => within(screen.getByRole('list', { name: 'Messages, newest first' }))
const count = (requests: RecordedRequest[], key: string) =>
  requests.filter((request) => `${request.method} ${request.path}` === key).length

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({
    'GET /auth/me': () => json(ACCOUNT),
    'GET /conversations?page=0&size=20': () => json(pageOf([conversation()], { size: 20 })),
    ...handlers,
  })
}

describe('Conversation detail', () => {
  it('shows the other participant, the listing and the conversation details', async () => {
    const { requests } = signedIn({
      [DETAIL]: () => json(conversation()),
      [MESSAGES]: () => json(pageOf([messageFromBob()], { size: 50 })),
    })
    renderApp('/messages/31')

    expect(await screen.findByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
    expect(screen.getByText('@bob')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Pairwise' }).getAttribute('href')).toBe('/listings/pairwise')
    expect(document.title).toBe('Bob Brown · Messages · Conflux')

    const details = screen.getByText('Conversation details').closest('details') as HTMLElement
    const facts = within(details)
    expect(facts.getByText('Started').nextElementSibling?.querySelector('time')?.getAttribute('datetime')).toBe(
      '2026-03-02T10:00:00Z',
    )
    expect(facts.getByText('Last message').nextElementSibling?.querySelector('time')?.getAttribute('datetime')).toBe(
      '2026-03-03T10:00:00Z',
    )
    expect(facts.getByText('Latest').nextElementSibling?.textContent).toBe('Happy to talk on Thursday.')

    // No ids or backend-only fields.
    const conversationText = screen.getByRole('region', { name: 'Bob Brown' }).textContent ?? ''
    expect(conversationText).not.toMatch(/\b(31|21|501)\b/)
    expect(conversationText).not.toContain(ACCOUNT.email)
    expect(requests.find((request) => request.path === '/conversations/31')?.headers.get('Authorization')).toBe(
      'Bearer stored-token',
    )
  })

  it('says when there are no messages yet', async () => {
    signedIn({
      [DETAIL]: () => json(conversation({ lastMessagePreview: null, lastMessageAt: null })),
      [MESSAGES]: () => json(pageOf([], { size: 50 })),
    })
    renderApp('/messages/31')

    expect(await screen.findByText(/Start the conversation with Bob Brown/)).toBeTruthy()
    expect(screen.getByText('No messages yet', { selector: '.thread-empty-title' })).toBeTruthy()
    const details = within(screen.getByText('Conversation details').closest('details') as HTMLElement)
    expect(details.getByText('Last message').nextElementSibling?.textContent).toBe('No messages yet')
    expect(details.queryByText('Latest')).toBeNull()
  })

  it('treats a conversation the user is not part of as not found (the backend’s 404)', async () => {
    const { requests } = signedIn({ 'GET /conversations/77': () => problem(404, LEAKY_DETAIL) })
    renderApp('/messages/77')

    expect(await screen.findByRole('heading', { name: 'Conversation not found' })).toBeTruthy()
    expect(screen.getByText('This conversation doesn’t exist, or you’re not part of it.')).toBeTruthy()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    // Without access, its messages are not requested either.
    expect(count(requests, 'GET /conversations/77/messages?page=0&size=50')).toBe(0)
    expect(screen.queryByRole('textbox')).toBeNull()
  })

  it.each(['abc', '0', '-3'])('treats the id %s as not found without a request', async (id) => {
    const { requests } = signedIn({})
    renderApp(`/messages/${id}`)

    expect(await screen.findByRole('heading', { name: 'Conversation not found' })).toBeTruthy()
    expect(requests.some((request) => request.path.startsWith(`/conversations/${id}`))).toBe(false)
  })

  it('shows other failures with a working retry', async () => {
    const { handlers } = signedIn({ [DETAIL]: () => problem(500, LEAKY_DETAIL), [MESSAGES]: () => json(pageOf([], { size: 50 })) })
    renderApp('/messages/31')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load this conversation')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[DETAIL] = () => json(conversation())
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
  })

  it('sends the user to log in when the token is rejected', async () => {
    signedIn({ [DETAIL]: () => problem(401, 'A valid access token is required.') })
    const router = renderApp('/messages/31')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/messages/31' } })
  })
})

describe('Message list', () => {
  it('shows a loading state, then the messages newest first as the backend orders them', async () => {
    const response = deferred<Response>()
    signedIn({ [DETAIL]: () => json(conversation()), [MESSAGES]: () => response.promise })
    renderApp('/messages/31')

    expect(await screen.findByText('Loading messages…')).toBeTruthy()
    response.resolve(json(pageOf([messageFromBob(), messageFromMe()], { size: 50 })))

    const items = await within(await screen.findByRole('list', { name: 'Messages, newest first' })).findAllByRole('listitem')
    expect(items.map((item) => item.querySelector('.message-content')?.textContent)).toEqual([
      'Happy to talk on Thursday.',
      'Thanks for accepting!',
    ])
    expect(items[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-03-03T10:00:00Z')
  })

  it('tells the user’s own messages apart without relying on colour', async () => {
    signedIn({
      [DETAIL]: () => json(conversation()),
      [MESSAGES]: () => json(pageOf([messageFromBob(), messageFromMe()], { size: 50 })),
    })
    renderApp('/messages/31')

    const [fromBob, fromMe] = await within(await screen.findByRole('list', { name: 'Messages, newest first' })).findAllByRole(
      'listitem',
    )
    expect(fromBob.getAttribute('data-own')).toBe('false')
    expect(within(fromBob).getByText('Bob Brown')).toBeTruthy()
    expect(within(fromBob).queryByText('You')).toBeNull()

    expect(fromMe.getAttribute('data-own')).toBe('true')
    expect(within(fromMe).getByText('Ada Lovelace')).toBeTruthy()
    expect(within(fromMe).getByText('You')).toBeTruthy()
  })

  it('keeps long messages and line breaks intact', async () => {
    const long = `${'word '.repeat(400)}\nsecond line`
    signedIn({
      [DETAIL]: () => json(conversation()),
      [MESSAGES]: () => json(pageOf([messageFromBob({ content: long })], { size: 50 })),
    })
    renderApp('/messages/31')

    const [item] = await within(await screen.findByRole('list', { name: 'Messages, newest first' })).findAllByRole('listitem')
    expect(item.querySelector('.message-content')?.textContent).toBe(long)
  })

  it('loads older messages at the end, without duplicates', async () => {
    const newest = Array.from({ length: 50 }, (_, i) => messageFromBob({ id: 1000 - i, content: `Message ${1000 - i}` }))
    const { requests } = signedIn({
      [DETAIL]: () => json(conversation()),
      [MESSAGES]: () => json(pageOf(newest, { size: 50, totalElements: 52 })),
      // The page shifted by one since: its first message was already shown.
      'GET /conversations/31/messages?page=1&size=50': () =>
        json(
          pageOf(
            [messageFromBob({ id: 951, content: 'Message 951' }), messageFromMe({ id: 900, content: 'The first one' })],
            { page: 1, size: 50, totalElements: 52 },
          ),
        ),
    })
    renderApp('/messages/31')

    const button = await screen.findByRole('button', { name: 'Load older messages' })
    fireEvent.click(button)
    expect(screen.getByRole('button', { name: 'Loading older messages…' })).toHaveProperty('disabled', true)

    await waitFor(() => expect(thread().getAllByRole('listitem')).toHaveLength(51))
    const contents = thread()
      .getAllByRole('listitem')
      .map((item) => item.querySelector('.message-content')?.textContent)
    expect(contents[0]).toBe('Message 1000')
    expect(contents.at(-1)).toBe('The first one')
    expect(screen.queryByRole('button', { name: /older messages/ })).toBeNull()
    expect(count(requests, 'GET /conversations/31/messages?page=1&size=50')).toBe(1)
  })

  it('shows a message-loading error with a working retry', async () => {
    const { handlers } = signedIn({ [DETAIL]: () => json(conversation()), [MESSAGES]: () => problem(503, LEAKY_DETAIL) })
    renderApp('/messages/31')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load the messages')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[MESSAGES] = () => json(pageOf([messageFromBob()], { size: 50 }))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Happy to talk on Thursday.', { selector: '.message-content' })).toBeTruthy()
  })
})
