import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { receivedConnection } from '../test/connections'
import { listingDetail, pageOf } from '../test/listings'
import { conversation } from '../test/messages'
import { aliceProfile, ownProfile } from '../test/profile'
import { renderApp } from '../test/renderApp'

const ALICE = 'GET /users/alice'
const LEAKY_DETAIL = 'User 7 is SUSPENDED'

const links = () => within(screen.getByRole('region', { name: 'Links' }))

describe('Public profile', () => {
  it('shows a loading state, then exactly the public fields', async () => {
    const response = deferred<Response>()
    const { requests } = mockApi({ [ALICE]: () => response.promise })
    renderApp('/users/alice')

    expect((await screen.findByText('Loading profile…')).getAttribute('role')).toBe('status')
    response.resolve(json(aliceProfile()))

    expect(await screen.findByRole('heading', { level: 1, name: 'Alice Anders' })).toBeTruthy()
    expect(screen.getByText('@alice')).toBeTruthy()
    expect(screen.getByText('Berlin, Germany')).toBeTruthy()
    expect(screen.getByText('January 2026').getAttribute('datetime')).toBe('2026-01-10T12:00:00Z')
    expect(document.title).toBe('Alice Anders (@alice) · Conflux')

    const about = within(screen.getByRole('region', { name: 'About' }))
    expect(about.getByText(/Second-time founder\./).textContent).toBe('Second-time founder.\nI build fintech products.')

    const website = links().getByRole('link', { name: /alice\.example\.com/ })
    expect(website.getAttribute('href')).toBe('https://alice.example.com/')
    expect(website.getAttribute('target')).toBe('_blank')
    expect(website.getAttribute('rel')).toBe('noopener noreferrer nofollow ugc')
    expect(website.textContent).toBe('alice.example.com (opens in a new tab)')
    expect(links().getByRole('link', { name: /github\.com\/alice-anders/ }).getAttribute('href')).toBe(
      'https://github.com/alice-anders',
    )
    expect(links().getByText('LinkedIn')).toBeTruthy()

    // No internal id; a public page sends no token.
    expect(document.querySelector('.profile')?.textContent).not.toMatch(/\b7\b/)
    expect(requests.find((request) => request.path === '/users/alice')?.headers.has('Authorization')).toBe(false)
    expect(screen.queryByRole('link', { name: 'Edit your profile' })).toBeNull()
  })

  it('shows empty bio and links plainly', async () => {
    mockApi({
      [ALICE]: () =>
        json(aliceProfile({ bio: null, location: null, websiteUrl: null, githubUrl: null, linkedinUrl: null })),
    })
    renderApp('/users/alice')

    expect(await screen.findByText('No bio yet.')).toBeTruthy()
    expect(screen.getByText('No links added.')).toBeTruthy()
    expect(screen.queryByText('Based in')).toBeNull()
  })

  it('never turns a non-http(s) value into a link', async () => {
    mockApi({ [ALICE]: () => json(aliceProfile({ websiteUrl: 'javascript:alert(1)', githubUrl: null, linkedinUrl: null })) })
    renderApp('/users/alice')

    await screen.findByRole('heading', { level: 1, name: 'Alice Anders' })
    expect(links().queryByRole('link')).toBeNull()
    expect(links().getByText('javascript:alert(1)')).toBeTruthy()
  })

  it('keeps very long values in the page instead of overflowing it', async () => {
    const long = `https://example.com/${'segment/'.repeat(30)}end`
    mockApi({ [ALICE]: () => json(aliceProfile({ websiteUrl: long })) })
    renderApp('/users/alice')

    await screen.findByRole('heading', { level: 1, name: 'Alice Anders' })
    const link = links().getByRole('link', { name: /example\.com/ })
    expect(link.getAttribute('href')).toBe(long)
    // Wrapping comes from .profile-links dd { overflow-wrap: anywhere } in the stylesheet.
    expect(link.closest('dd')).toBeTruthy()
  })

  it.each([
    ['an unknown user', 'User not found.'],
    ['a suspended user', LEAKY_DETAIL],
  ])('shows %s as not found, without backend details', async (_, detail) => {
    mockApi({ [ALICE]: () => problem(404, detail) })
    renderApp('/users/alice')

    expect(await screen.findByRole('heading', { level: 1, name: 'Profile not found' })).toBeTruthy()
    expect(screen.getByText('This profile doesn’t exist, or it isn’t available.')).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
  })

  it.each(['ab', 'ada.lovelace', 'a%20b', 'x'.repeat(31), '%C3%A1d%C3%A1'])(
    'treats the malformed username %s as not found without a request',
    async (username) => {
      const { requests } = mockApi({})
      renderApp(`/users/${username}`)

      expect(await screen.findByRole('heading', { level: 1, name: 'Profile not found' })).toBeTruthy()
      expect(requests.filter((request) => request.path.startsWith('/users/'))).toHaveLength(0)
    },
  )

  it('asks for the normalized username', async () => {
    const { requests } = mockApi({ [ALICE]: () => json(aliceProfile()) })
    renderApp('/users/Alice')

    expect(await screen.findByRole('heading', { level: 1, name: 'Alice Anders' })).toBeTruthy()
    expect(requests.map((request) => request.path)).toEqual(['/users/alice'])
  })

  it('shows other failures without backend details, with a working retry', async () => {
    const { handlers } = mockApi({ [ALICE]: () => problem(500, LEAKY_DETAIL) })
    renderApp('/users/alice')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load this profile')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[ALICE] = () => json(aliceProfile())
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Alice Anders' })).toBeTruthy()
  })

  it('offers the signed-in user a way to edit their own public profile', async () => {
    setAccessToken('stored-token')
    mockApi({ 'GET /auth/me': () => json(ACCOUNT), 'GET /users/ada': () => json(ownProfile()) })
    renderApp('/users/ada')

    const edit = await screen.findByRole('link', { name: 'Edit your profile' })
    expect(edit.getAttribute('href')).toBe('/profile')
  })
})

describe('Links to public profiles', () => {
  it('leads from a listing’s owner to their profile', async () => {
    mockApi({ 'GET /listings/ledgerly': () => json(listingDetail()), [ALICE]: () => json(aliceProfile()) })
    const router = renderApp('/listings/ledgerly')

    const summary = within(await screen.findByRole('complementary', { name: 'Listing summary' }))
    fireEvent.click(summary.getByRole('link', { name: 'Alice Anders' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Alice Anders' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/users/alice')
  })

  it('links the people on a connection and the other person in a conversation', async () => {
    setAccessToken('stored-token')
    mockApi({
      'GET /auth/me': () => json(ACCOUNT),
      'GET /connections/21': () => json(receivedConnection()),
      'GET /conversations?page=0&size=20': () => json(pageOf([conversation()], { size: 20 })),
      'GET /conversations/31': () => json(conversation()),
      'GET /conversations/31/messages?page=0&size=50': () => json(pageOf([], { size: 50 })),
    })
    const router = renderApp('/connections/21')

    const people = within(await screen.findByRole('region', { name: 'People' }))
    expect(people.getByRole('link', { name: '@bob' }).getAttribute('href')).toBe('/users/bob')
    expect(people.getByRole('link', { name: '@ada' }).getAttribute('href')).toBe('/users/ada')

    await router.navigate('/messages/31')
    const conversationRegion = within(await screen.findByRole('region', { name: 'Bob Brown' }))
    await waitFor(() => expect(conversationRegion.getByRole('link', { name: '@bob' }).getAttribute('href')).toBe('/users/bob'))
  })
})
