import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../../test/api'
import { pageOf } from '../../test/listings'
import { conversation, messageFromBob, messageFromMe } from '../../test/messages'
import { renderApp } from '../../test/renderApp'

const LIST = 'GET /conversations?page=0&size=20'
const DETAIL = 'GET /conversations/31'
const MESSAGES = 'GET /conversations/31/messages?page=0&size=50'
const SEND = 'POST /conversations/31/messages'
const LEAKY_DETAIL = 'Messages can only be sent in conversations of accepted connections. [ConnectionStatus=REJECTED]'

const SENT = messageFromMe({ id: 600, content: 'See you Thursday at 10.', createdAt: '2026-03-04T09:00:00Z' })

const input = () => screen.getByRole('textbox', { name: 'Message to Bob Brown' }) as HTMLTextAreaElement
const sendButton = () => screen.getByRole('button', { name: 'Send' })
const sends = (requests: RecordedRequest[]) =>
  requests.filter((request) => `${request.method} ${request.path}` === SEND)
const contents = () =>
  within(screen.getByRole('list', { name: 'Messages, newest first' }))
    .getAllByRole('listitem')
    .map((item) => item.querySelector('.message-content')?.textContent)

async function openConversation(handlers: Parameters<typeof mockApi>[0] = {}) {
  setAccessToken('stored-token')
  const api = mockApi({
    'GET /auth/me': () => json(ACCOUNT),
    [LIST]: () => json(pageOf([conversation()], { size: 20 })),
    [DETAIL]: () => json(conversation()),
    [MESSAGES]: () => json(pageOf([messageFromBob()], { size: 50 })),
    ...handlers,
  })
  const router = renderApp('/messages/31')
  await screen.findByText('Happy to talk on Thursday.', { selector: '.message-content' })
  return { ...api, router }
}

function type(text: string) {
  fireEvent.change(input(), { target: { value: text } })
}

describe('Sending a message', () => {
  it('sends the trimmed text, then shows the returned message on top and clears the input', async () => {
    const response = deferred<Response>()
    const { requests } = await openConversation({ [SEND]: () => response.promise })
    expect(input().getAttribute('aria-describedby')).toContain(screen.getByText(/Enter to send/).id)

    type('  See you Thursday at 10.  ')
    fireEvent.click(sendButton())

    // While sending: controls disabled, the draft kept, nothing inserted yet.
    const sending = screen.getByRole('button', { name: 'Sending…' })
    expect(sending).toHaveProperty('disabled', true)
    expect(sending.getAttribute('aria-busy')).toBe('true')
    expect(input().readOnly).toBe(true)
    expect(input().value).toBe('  See you Thursday at 10.  ')
    expect(contents()).toEqual(['Happy to talk on Thursday.'])

    response.resolve(json(SENT, 201))

    await waitFor(() => expect(contents()).toEqual(['See you Thursday at 10.', 'Happy to talk on Thursday.']))
    expect(input().value).toBe('')
    expect(input().readOnly).toBe(false)
    expect(document.activeElement).toBe(input())
    expect(screen.getByText('Message sent.')).toBeTruthy()
    const [newest] = within(screen.getByRole('list', { name: 'Messages, newest first' })).getAllByRole('listitem')
    expect(newest.getAttribute('data-own')).toBe('true')
    expect(newest.querySelector('time')?.getAttribute('datetime')).toBe('2026-03-04T09:00:00Z')

    expect(sends(requests)).toHaveLength(1)
    expect(sends(requests)[0].body).toEqual({ content: 'See you Thursday at 10.' })
    expect(sends(requests)[0].headers.get('Authorization')).toBe('Bearer stored-token')
  })

  it('inserts the message without reloading the thread, and refreshes the list and details from the backend', async () => {
    const { requests } = await openConversation({ [SEND]: () => json(SENT, 201) })

    type('See you Thursday at 10.')
    fireEvent.click(sendButton())

    await waitFor(() => expect(contents()[0]).toBe('See you Thursday at 10.'))
    expect(requests.filter((request) => request.path === MESSAGES.slice(4))).toHaveLength(1)
    await waitFor(() => expect(requests.filter((request) => request.path === LIST.slice(4))).toHaveLength(2))
    await waitFor(() => expect(requests.filter((request) => request.path === DETAIL.slice(4))).toHaveLength(2))
    // Still the same page: no navigation happened.
    expect(screen.getByRole('heading', { level: 2, name: 'Bob Brown' })).toBeTruthy()
  })

  it('sends with Enter, and adds a line with Shift+Enter', async () => {
    const { requests } = await openConversation({ [SEND]: () => json(SENT, 201) })

    type('First line')
    fireEvent.keyDown(input(), { key: 'Enter', shiftKey: true })
    expect(sends(requests)).toHaveLength(0)

    fireEvent.keyDown(input(), { key: 'Enter' })

    await waitFor(() => expect(sends(requests)).toHaveLength(1))
    await waitFor(() => expect(input().value).toBe(''))
  })

  it('sends one request however often Send or Enter is used while sending', async () => {
    const response = deferred<Response>()
    const { requests } = await openConversation({ [SEND]: () => response.promise })

    type('Hello')
    fireEvent.click(sendButton())
    fireEvent.keyDown(input(), { key: 'Enter' })
    fireEvent.submit(input().closest('form') as HTMLFormElement)
    response.resolve(json(SENT, 201))

    await waitFor(() => expect(input().value).toBe(''))
    expect(sends(requests)).toHaveLength(1)
  })

  it.each(['', '   ', '\n\n  \t'])('does not send a blank message (%j)', async (text) => {
    const { requests } = await openConversation({ [SEND]: () => json(SENT, 201) })

    type(text)
    fireEvent.click(sendButton())

    expect((await screen.findByRole('alert')).textContent).toBe('Write a message before sending.')
    expect(input().getAttribute('aria-invalid')).toBe('true')
    expect(sends(requests)).toHaveLength(0)

    type('Now with text')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('counts down near the 5,000-character limit and refuses longer messages', async () => {
    const { requests } = await openConversation({ [SEND]: () => json(SENT, 201) })

    type('a'.repeat(4600))
    expect(screen.getByText('400 characters left')).toBeTruthy()

    type(`  ${'a'.repeat(5000)}  `)
    expect(screen.getByText('0 characters left')).toBeTruthy()

    type('a'.repeat(5003))
    const counter = screen.getByText('3 characters over the limit')
    expect(input().getAttribute('aria-describedby')).toContain(counter.id)
    fireEvent.click(sendButton())

    expect((await screen.findByRole('alert')).textContent).toBe(
      'Messages can be at most 5,000 characters. Shorten yours by 3.',
    )
    expect(sends(requests)).toHaveLength(0)
    expect(input().value).toHaveLength(5003)
  })
})

describe('Send failures', () => {
  it('explains a 400 and keeps the draft', async () => {
    await openConversation({ [SEND]: () => problem(400, 'Invalid request content.', [{ field: 'content', message: 'must not be blank' }]) })

    type('Hello')
    fireEvent.click(sendButton())

    expect((await screen.findByRole('alert')).textContent).toBe(
      'This message can’t be sent. Messages need some text and can be at most 5,000 characters.',
    )
    expect(input().value).toBe('Hello')
    expect(document.body.textContent).not.toContain('must not be blank')
  })

  it.each([
    [403, 'Your account can’t send messages right now. It may be suspended.'],
    [404, 'This conversation doesn’t exist, or you no longer have access to it.'],
    [409, 'This conversation isn’t open for messages. Messaging is only available for accepted connections.'],
  ])('closes the composer and says why after a %i', async (status, message) => {
    await openConversation({ [SEND]: () => problem(status, LEAKY_DETAIL) })

    type('Hello')
    fireEvent.click(sendButton())

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('You can’t send messages here')
    expect(alert.textContent).toContain(message)
    expect(screen.queryByRole('textbox', { name: 'Message to Bob Brown' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Send' })).toBeNull()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    // The messages already there stay readable.
    expect(contents()).toEqual(['Happy to talk on Thursday.'])
  })

  it.each([
    [429, 'You’re sending messages too quickly. Wait a moment, then try again.'],
    [500, 'Your message wasn’t sent because something went wrong. Please try again.'],
    [503, 'Your message wasn’t sent because something went wrong. Please try again.'],
  ])('keeps the draft after a %i and offers to try again', async (status, message) => {
    const { handlers, requests } = await openConversation({ [SEND]: () => problem(status, LEAKY_DETAIL) })

    type('Hello again')
    fireEvent.click(sendButton())

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain(message)
    expect(input().value).toBe('Hello again')
    expect(sendButton()).toHaveProperty('disabled', false)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[SEND] = () => json(messageFromMe({ id: 601, content: 'Hello again' }), 201)
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(contents()[0]).toBe('Hello again'))
    expect(screen.queryByRole('alert')).toBeNull()
    expect(input().value).toBe('')
    expect(sends(requests)).toHaveLength(2)
  })

  it('keeps the draft after a network failure', async () => {
    await openConversation({
      [SEND]: () => {
        throw new TypeError('Failed to fetch')
      },
    })

    type('Are you there?')
    fireEvent.click(sendButton())

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Unable to reach the server. Your message wasn’t sent; check your connection and try again.',
    )
    expect(input().value).toBe('Are you there?')
    expect(within(screen.getByRole('alert')).getByRole('button', { name: 'Try again' })).toBeTruthy()
  })

  it('sends the user to log in when the session has expired', async () => {
    const { router } = await openConversation({ [SEND]: () => problem(401, 'A valid access token is required.') })

    type('Hello')
    fireEvent.click(sendButton())

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/messages/31' } })
    expect(getAccessToken()).toBeNull()
  })
})
