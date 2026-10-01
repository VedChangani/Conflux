import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem, type RecordedRequest } from '../test/api'
import { ownProfile } from '../test/profile'
import { renderApp } from '../test/renderApp'

const PROFILE = 'GET /profile'
const UPDATE = 'PUT /profile'
const LEAKY_DETAIL = 'size must be between 0 and 255 [UpdateProfileRequest.websiteUrl]'

const nav = () => within(screen.getByRole('navigation', { name: 'Main' }))
const field = (label: string) => screen.getByLabelText(label) as HTMLInputElement | HTMLTextAreaElement
const saveButton = () => screen.getByRole('button', { name: 'Save changes' })
const updates = (requests: RecordedRequest[]) => requests.filter((request) => `${request.method} ${request.path}` === UPDATE)

function signedIn(handlers: Parameters<typeof mockApi>[0], account = ACCOUNT) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(account), [PROFILE]: () => json(ownProfile()), ...handlers })
}

/** Opens /profile and switches to the edit form. */
async function editProfile(handlers: Parameters<typeof mockApi>[0] = {}, account = ACCOUNT) {
  const api = signedIn(handlers, account)
  const router = renderApp('/profile')
  fireEvent.click(await screen.findByRole('button', { name: 'Edit profile' }))
  await screen.findByRole('heading', { level: 1, name: 'Edit profile' })
  return { ...api, router }
}

function type(label: string, value: string) {
  fireEvent.change(field(label), { target: { value } })
}

describe('Own profile', () => {
  it('is reached from the navigation, through login for anonymous visitors', async () => {
    mockApi({
      'POST /auth/login': () => json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /auth/me': () => json(ACCOUNT),
      [PROFILE]: () => json(ownProfile()),
    })
    const router = renderApp('/profile')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'ada' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct horse' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/profile')
    const profileLink = nav().getByRole('link', { name: 'Profile (Ada Lovelace)' })
    expect(profileLink.getAttribute('href')).toBe('/profile')
    expect(profileLink.getAttribute('aria-current')).toBe('page')
  })

  it('shows a loading state, then the profile with its own actions', async () => {
    const response = deferred<Response>()
    const { requests } = signedIn({ [PROFILE]: () => response.promise })
    renderApp('/profile')

    expect(await screen.findByText('Loading your profile…')).toBeTruthy()
    response.resolve(json(ownProfile({ githubUrl: null, bio: null })))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy()
    expect(screen.getByText('@ada')).toBeTruthy()
    expect(screen.getByText('London')).toBeTruthy()
    expect(screen.getByText('You haven’t written a bio yet.')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'View public profile' }).getAttribute('href')).toBe('/users/ada')
    expect(requests.find((request) => request.path === '/profile')?.headers.get('Authorization')).toBe('Bearer stored-token')
    // Account details from the session are not part of the profile page.
    expect(document.querySelector('.profile')?.textContent).not.toContain(ACCOUNT.email)
  })

  it('shows a load error with a working retry', async () => {
    const { handlers } = signedIn({ [PROFILE]: () => problem(503, LEAKY_DETAIL) })
    renderApp('/profile')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('We couldn’t load your profile')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[PROFILE] = () => json(ownProfile())
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy()
  })

  it('can be read by a suspended account', async () => {
    signedIn({}, { ...ACCOUNT, status: 'SUSPENDED' })
    renderApp('/profile')

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })
})

describe('Editing the profile', () => {
  it('fills the form with the current profile, labelling every field', async () => {
    await editProfile()

    expect(field('Display name').value).toBe('Ada Lovelace')
    expect(field('Location').value).toBe('London')
    expect(field('Bio').value).toBe('Building tools for small finance teams.')
    expect(field('Website').value).toBe('https://ada.dev')
    expect(field('GitHub').value).toBe('')
    expect(field('LinkedIn').value).toBe('')
    expect(field('Bio').tagName).toBe('TEXTAREA')
    expect(screen.getByRole('group', { name: 'About you' })).toBeTruthy()
    expect(screen.getByRole('group', { name: 'Links' })).toBeTruthy()
    // The username is shown for context but is not a form field.
    expect(screen.queryByLabelText(/username/i)).toBeNull()
    expect(document.activeElement).toBe(screen.getByRole('heading', { level: 1, name: 'Edit profile' }))
  })

  it('requires a display name and checks lengths without sending anything', async () => {
    const { requests } = await editProfile()

    type('Display name', '   ')
    type('Bio', 'b'.repeat(503))
    type('Location', 'l'.repeat(121))
    expect(screen.getByText('3 characters over the limit', { exact: false })).toBeTruthy()
    fireEvent.click(saveButton())

    expect(within(screen.getByRole('alert')).getByText('Please correct the highlighted fields.')).toBeTruthy()
    expect(field('Display name').getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByText('Enter a display name.')).toBeTruthy()
    expect(screen.getByText('Bio can be at most 500 characters (3 too many).')).toBeTruthy()
    expect(screen.getByText('Location can be at most 120 characters (1 too many).')).toBeTruthy()
    expect(field('Display name').getAttribute('aria-describedby')).toContain(screen.getByText('Enter a display name.').id)
    await waitFor(() => expect(document.activeElement).toBe(field('Display name')))
    expect(updates(requests)).toHaveLength(0)

    type('Display name', 'Ada')
    expect(screen.queryByText('Enter a display name.')).toBeNull()
    expect(field('Display name').getAttribute('aria-invalid')).toBeNull()
  })

  it('requires full http(s) addresses for links', async () => {
    const { requests } = await editProfile()

    type('GitHub', 'github.com/ada')
    type('LinkedIn', 'javascript:alert(1)')
    fireEvent.click(saveButton())

    const message = 'Enter a full web address starting with http:// or https://, e.g. https://example.com.'
    expect(screen.getAllByText(message)).toHaveLength(2)
    expect(field('GitHub').getAttribute('aria-invalid')).toBe('true')
    expect(field('Website').getAttribute('aria-invalid')).toBeNull()
    expect(updates(requests)).toHaveLength(0)
  })

  it('sends the complete editable profile and nothing else, then shows the saved profile', async () => {
    const response = deferred<Response>()
    const { requests } = await editProfile({ [UPDATE]: () => response.promise })

    type('Display name', '  Ada King  ')
    type('Bio', '  Maths, engines and marketplaces.  ')
    type('Location', '   ')
    type('GitHub', ' https://github.com/ada ')
    fireEvent.click(saveButton())

    // While saving: disabled, values kept, nothing shown as saved yet.
    const saving = screen.getByRole('button', { name: 'Saving…' })
    expect(saving).toHaveProperty('disabled', true)
    expect(saving.getAttribute('aria-busy')).toBe('true')
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveProperty('disabled', true)
    fireEvent.click(saving)
    fireEvent.submit(saving.closest('form') as HTMLFormElement)

    const [update] = updates(requests)
    expect(updates(requests)).toHaveLength(1)
    expect(update.body).toEqual({
      displayName: 'Ada King',
      bio: 'Maths, engines and marketplaces.',
      location: null,
      websiteUrl: 'https://ada.dev',
      githubUrl: 'https://github.com/ada',
      linkedinUrl: null,
    })
    for (const key of ['id', 'username', 'email', 'role', 'status', 'password', 'passwordHash', 'createdAt']) {
      expect(update.body).not.toHaveProperty(key)
    }
    expect(update.headers.get('Authorization')).toBe('Bearer stored-token')

    response.resolve(
      json(
        ownProfile({
          displayName: 'Ada King',
          bio: 'Maths, engines and marketplaces.',
          location: null,
          githubUrl: 'https://github.com/ada',
        }),
      ),
    )

    const notice = await screen.findByRole('status')
    expect(notice.textContent).toContain('Profile saved.')
    await waitFor(() => expect(document.activeElement).toBe(notice))
    expect(screen.getByRole('heading', { level: 1, name: 'Ada King' })).toBeTruthy()
    expect(screen.getByText('Maths, engines and marketplaces.')).toBeTruthy()
    expect(screen.queryByText('London')).toBeNull()
    // Saved in place: the profile is not loaded again.
    expect(requests.filter((request) => request.path === '/profile' && request.method === 'GET')).toHaveLength(1)
  })

  it('updates the name in the navigation from the backend after a name change', async () => {
    const { handlers, requests } = await editProfile({
      [UPDATE]: () => json(ownProfile({ displayName: 'Ada King' })),
    })
    handlers['GET /auth/me'] = () => json({ ...ACCOUNT, displayName: 'Ada King' })

    type('Display name', 'Ada King')
    fireEvent.click(saveButton())

    expect(await nav().findByRole('link', { name: 'Profile (Ada King)' })).toBeTruthy()
    expect(requests.filter((request) => request.path === '/auth/me')).toHaveLength(2)
  })

  it('cancels without saving or changing the profile', async () => {
    const { requests } = await editProfile()

    type('Display name', 'Someone else')
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy()
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Edit profile' })))
    expect(updates(requests)).toHaveLength(0)
  })
})

describe('Save failures', () => {
  it('shows backend field errors in the form’s own words and keeps the values', async () => {
    await editProfile({
      [UPDATE]: () =>
        problem(400, 'Invalid request content.', [
          { field: 'websiteUrl', message: LEAKY_DETAIL },
          { field: 'role', message: 'not allowed' },
        ]),
    })

    type('Website', 'https://ada.dev/a-page-the-server-rejects')
    type('Bio', 'Kept as typed.')
    fireEvent.click(saveButton())

    expect(await screen.findByText('Check this value: Website wasn’t accepted.')).toBeTruthy()
    expect(within(screen.getByRole('alert')).getByText('Please correct the highlighted fields.')).toBeTruthy()
    expect(field('Website').getAttribute('aria-invalid')).toBe('true')
    await waitFor(() => expect(document.activeElement).toBe(field('Website')))
    expect(field('Website').value).toBe('https://ada.dev/a-page-the-server-rejects')
    expect(field('Bio').value).toBe('Kept as typed.')
    expect(saveButton()).toHaveProperty('disabled', false)
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)
    expect(document.body.textContent).not.toContain('not allowed')
  })

  it('shows that a suspended account cannot update its profile (403)', async () => {
    await editProfile({ [UPDATE]: () => problem(403, 'This account is suspended.') }, { ...ACCOUNT, status: 'SUSPENDED' })

    type('Bio', 'New bio')
    fireEvent.click(saveButton())

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Your profile wasn’t saved')
    expect(alert.textContent).toContain('Your account is suspended, so your profile can’t be updated.')
    expect(field('Bio').value).toBe('New bio')
    expect(screen.queryByRole('status')).toBeNull()
  })

  it.each([
    [429, 'You’ve updated your profile too often. Please try again later.'],
    [500, 'Your changes weren’t saved because something went wrong. Please try again.'],
  ])('explains a %i and lets the user try again', async (status, message) => {
    const { handlers, requests } = await editProfile({ [UPDATE]: () => problem(status, LEAKY_DETAIL) })

    type('Location', 'Paris')
    fireEvent.click(saveButton())

    expect((await screen.findByRole('alert')).textContent).toContain(message)
    expect(field('Location').value).toBe('Paris')
    expect(document.body.textContent).not.toContain(LEAKY_DETAIL)

    handlers[UPDATE] = () => json(ownProfile({ location: 'Paris' }))
    fireEvent.click(saveButton())

    expect((await screen.findByRole('status')).textContent).toContain('Profile saved.')
    expect(screen.getByText('Paris')).toBeTruthy()
    expect(updates(requests)).toHaveLength(2)
  })

  it('explains a network failure', async () => {
    await editProfile({
      [UPDATE]: () => {
        throw new TypeError('Failed to fetch')
      },
    })

    fireEvent.click(saveButton())

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Unable to reach the server. Your changes weren’t saved; check your connection and try again.',
    )
  })

  it('sends the user to log in when the session has expired', async () => {
    const { router } = await editProfile({ [UPDATE]: () => problem(401, 'A valid access token is required.') })

    fireEvent.click(saveButton())

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/profile' } })
    expect(getAccessToken()).toBeNull()
  })
})
