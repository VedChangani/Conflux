import { render, screen, within } from '@testing-library/react'
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

  it('gives the 404 page a title and ways back into the app', () => {
    renderAt('/does-not-exist')

    expect(document.title).toBe('Page not found · Conflux')
    const panel = screen.getByRole('region', { name: 'Page not found' })
    expect(within(panel).getByText('404')).toBeTruthy()
    expect(within(panel).getByRole('link', { name: 'Browse the marketplace' }).getAttribute('href')).toBe('/listings')
    expect(within(panel).getByRole('link', { name: 'Go to the home page' }).getAttribute('href')).toBe('/')
  })

  it.each([
    ['/', 'Conflux · Ideas, projects and startups'],
    ['/login', 'Log in · Conflux'],
    ['/register', 'Create an account · Conflux'],
  ])('sets the document title on %s', (path, title) => {
    renderAt(path)

    expect(document.title).toBe(title)
  })
})
