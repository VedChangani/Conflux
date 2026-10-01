import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, type InitialEntry, type RouteObject } from 'react-router'
import { routes } from '../app/routes'
import { AuthProvider } from '../features/auth/AuthProvider'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'

// The real route tree plus one protected page, since the app has none yet.
const [shell] = routes
const testRoutes: RouteObject[] = [
  {
    element: shell.element,
    errorElement: shell.errorElement,
    children: [
      ...(shell.children ?? []),
      { element: <ProtectedRoute />, children: [{ path: '/private', element: <h1>Private page</h1> }] },
    ],
  },
]

/** Renders the whole app (auth provider, shell, routes) at the given location. */
export function renderApp(entry: InitialEntry = '/') {
  const router = createMemoryRouter(testRoutes, { initialEntries: [entry] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return router
}
