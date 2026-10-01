import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'
import { ProtectedRoute } from './ProtectedRoute'

function renderProtected(status: AuthStatus, loggedOut = false) {
  const auth: AuthContextValue = {
    status,
    account:
      status === 'authenticated'
        ? { id: 1, email: 'a@example.com', username: 'alice', displayName: 'Alice', role: 'USER', status: 'ACTIVE' }
        : null,
    verificationFailed: false,
    loggedOut,
    login: vi.fn(),
    logout: vi.fn(),
    retryVerification: vi.fn(),
    refreshAccount: vi.fn(),
  }
  const router = createMemoryRouter(
    [
      { path: '/login', element: <h1>Login page</h1> },
      { element: <ProtectedRoute />, children: [{ path: '/private', element: <h1>Private page</h1> }] },
    ],
    { initialEntries: ['/private'] },
  )
  render(
    <AuthContext value={auth}>
      <RouterProvider router={router} />
    </AuthContext>,
  )
  return router
}

describe('ProtectedRoute', () => {
  it('renders the page for a confirmed account', () => {
    renderProtected('authenticated')

    expect(screen.getByRole('heading', { name: 'Private page' })).toBeTruthy()
  })

  it('waits while a stored token has not been verified', () => {
    renderProtected('unverified')

    expect(screen.getByRole('status')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Private page' })).toBeNull()
  })

  it('redirects anonymous visitors to login, remembering the requested page', () => {
    const router = renderProtected('anonymous')

    expect(screen.getByRole('heading', { name: 'Login page' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/private' } })
  })

  it('redirects to login without a return location after a deliberate logout', () => {
    const router = renderProtected('anonymous', true)

    expect(screen.getByRole('heading', { name: 'Login page' })).toBeTruthy()
    expect(router.state.location.state).toBeNull()
  })
})
