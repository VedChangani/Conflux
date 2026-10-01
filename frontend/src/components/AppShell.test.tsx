import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken, setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi } from '../test/api'
import { renderApp } from '../test/renderApp'

const nav = () => screen.getByRole('navigation', { name: 'Main' })

describe('AppShell navigation', () => {
  it('shows Log in and Register to anonymous visitors', () => {
    renderApp('/')

    expect(within(nav()).getByRole('link', { name: 'Log in' })).toBeTruthy()
    expect(within(nav()).getByRole('link', { name: 'Register' })).toBeTruthy()
    expect(within(nav()).queryByRole('button', { name: 'Log out' })).toBeNull()
  })

  it('shows the display name and Log out, but no private account fields, when authenticated', async () => {
    setAccessToken('stored-token')
    mockApi({ 'GET /auth/me': () => json(ACCOUNT) })
    renderApp('/')

    expect(await within(nav()).findByText('Ada Lovelace')).toBeTruthy()
    expect(within(nav()).getByRole('button', { name: 'Log out' })).toBeTruthy()
    expect(within(nav()).queryByRole('link', { name: 'Log in' })).toBeNull()
    expect(within(nav()).queryByRole('link', { name: 'Register' })).toBeNull()

    const navHtml = nav().outerHTML
    expect(navHtml).not.toContain(ACCOUNT.email)
    expect(navHtml).not.toContain(ACCOUNT.role)
    expect(navHtml).not.toContain('stored-token')
  })

  it('shows neither signed-in nor signed-out UI while the stored token is being checked', async () => {
    setAccessToken('stored-token')
    const me = deferred<Response>()
    mockApi({ 'GET /auth/me': () => me.promise })
    renderApp('/')

    expect(screen.getByRole('status').textContent).toContain('Checking your session')
    expect(within(nav()).queryByRole('link', { name: 'Log in' })).toBeNull()
    expect(within(nav()).queryByRole('button', { name: 'Log out' })).toBeNull()
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull()

    me.resolve(json(ACCOUNT))

    expect(await screen.findByRole('heading', { level: 1, name: 'Conflux' })).toBeTruthy()
  })

  it('offers a retry when the session cannot be checked', async () => {
    setAccessToken('stored-token')
    const { handlers } = mockApi({
      'GET /auth/me': () => {
        throw new TypeError('Failed to fetch')
      },
    })
    renderApp('/')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("We couldn't confirm your session")

    handlers['GET /auth/me'] = () => json(ACCOUNT)
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await within(nav()).findByText('Ada Lovelace')).toBeTruthy()
  })

  it('logs out: clears the session and returns to the login page', async () => {
    setAccessToken('stored-token')
    mockApi({ 'GET /auth/me': () => json(ACCOUNT) })
    const router = renderApp('/private')
    expect(await screen.findByRole('heading', { name: 'Private page' })).toBeTruthy()

    fireEvent.click(within(nav()).getByRole('button', { name: 'Log out' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    // An explicit logout is not a "return here after login" redirect.
    expect(router.state.location.state).toBeNull()
    expect(screen.getByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(within(nav()).getByRole('link', { name: 'Log in' })).toBeTruthy()
    expect(within(nav()).queryByText('Ada Lovelace')).toBeNull()
    expect(getAccessToken()).toBeNull()
  })
})
