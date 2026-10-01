import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../features/auth/AuthProvider'
import { routes } from './routes'

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
}

describe('routes', () => {
  it.each([
    ['/', 'Conflux'],
    ['/login', 'Log in'],
    ['/register', 'Create an account'],
  ])('renders %s', (path, heading) => {
    renderAt(path)

    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeTruthy()
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeTruthy()
  })

  it.each(['/does-not-exist', '/listings/unknown/deep'])('renders the 404 page for %s', (path) => {
    renderAt(path)

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeTruthy()
  })
})
