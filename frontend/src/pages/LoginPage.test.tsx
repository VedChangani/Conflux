import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, json, mockApi, problem } from '../test/api'
import { renderApp } from '../test/renderApp'

const TOKEN_RESPONSE = { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600 }

function submitLogin(identifier: string, password: string) {
  fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: identifier } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginPage', () => {
  it('logs in and goes to the home page', async () => {
    const { requests } = mockApi({
      'POST /auth/login': () => json(TOKEN_RESPONSE),
      'GET /auth/me': () => json(ACCOUNT),
    })
    const router = renderApp('/login')

    submitLogin(' ada ', 'correct horse')

    expect(await screen.findByRole('heading', { level: 1, name: 'Conflux' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/')
    expect(getAccessToken()).toBe('new-token')
    expect(requests[0].body).toEqual({ identifier: 'ada', password: 'correct horse' })
  })

  it('shows the backend error, clears the password and never renders it', async () => {
    mockApi({ 'POST /auth/login': () => problem(401, 'Invalid email/username or password.') })
    renderApp('/login')

    submitLogin('ada', 'wrong-password-123')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Invalid email/username or password.')
    expect((screen.getByLabelText('Password') as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText('Email or username') as HTMLInputElement).value).toBe('ada')
    expect(document.body.textContent).not.toContain('wrong-password-123')
    expect(getAccessToken()).toBeNull()
    expect(screen.getByRole('button', { name: 'Log in' })).toHaveProperty('disabled', false)
  })

  it('shows field validation errors from the backend', async () => {
    mockApi({
      'POST /auth/login': () =>
        problem(400, 'Invalid request content.', [{ field: 'password', message: 'must not be blank' }]),
    })
    renderApp('/login')

    submitLogin('ada', '')

    expect(await screen.findByText('Must not be blank.')).toBeTruthy()
    expect(screen.getByLabelText('Password').getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByRole('alert').textContent).toContain('Please correct the highlighted fields.')
  })

  it('disables the submit button while logging in', async () => {
    mockApi({
      'POST /auth/login': () => new Promise<Response>(() => undefined),
    })
    renderApp('/login')

    submitLogin('ada', 'correct horse')

    const button = await screen.findByRole('button', { name: 'Logging in…' })
    expect(button).toHaveProperty('disabled', true)
  })

  it('redirects an already authenticated user away from the login page', async () => {
    setAccessToken('stored-token')
    mockApi({ 'GET /auth/me': () => json(ACCOUNT) })
    const router = renderApp('/login')

    expect(await screen.findByRole('heading', { level: 1, name: 'Conflux' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/')
  })
})

describe('Protected routes', () => {
  it('redirects anonymous users to login and returns them to the requested page afterwards', async () => {
    mockApi({
      'POST /auth/login': () => json(TOKEN_RESPONSE),
      'GET /auth/me': () => json(ACCOUNT),
    })
    const router = renderApp('/private?tab=offers#top')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/login')

    submitLogin('ada', 'correct horse')

    expect(await screen.findByRole('heading', { name: 'Private page' })).toBeTruthy()
    expect(router.state.location).toMatchObject({ pathname: '/private', search: '?tab=offers', hash: '#top' })
  })

  it('lets a user with a verified token straight through', async () => {
    setAccessToken('stored-token')
    mockApi({ 'GET /auth/me': () => json(ACCOUNT) })
    const router = renderApp('/private')

    expect(await screen.findByRole('heading', { name: 'Private page' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/private')
  })

  it('sends a user with an expired token to login', async () => {
    setAccessToken('expired-token')
    mockApi({ 'GET /auth/me': () => problem(401, 'A valid access token is required.') })
    const router = renderApp('/private')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(screen.queryByRole('heading', { name: 'Private page' })).toBeNull()
  })
})
