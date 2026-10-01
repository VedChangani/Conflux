import { Navigate, type RouteObject } from 'react-router'
import { AppShell } from '../components/AppShell'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'
import { ConnectionList } from '../features/connections/ConnectionList'
import { ConversationForConnection } from '../features/messages/ConversationForConnection'
import { ConversationView } from '../features/messages/ConversationView'
import { ConnectionDetailPage } from '../pages/ConnectionDetailPage'
import { ConnectionsPage } from '../pages/ConnectionsPage'
import { HomePage } from '../pages/HomePage'
import { ListingDetailPage } from '../pages/ListingDetailPage'
import { LoginPage } from '../pages/LoginPage'
import { MarketplacePage } from '../pages/MarketplacePage'
import { MessagesIndex, MessagesPage } from '../pages/MessagesPage'
import { ProfilePage } from '../pages/ProfilePage'
import { PublicProfilePage } from '../pages/PublicProfilePage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RegisterPage } from '../pages/RegisterPage'
import { SavedListingsPage } from '../pages/SavedListingsPage'
import { paths } from './paths'
import { RouteError } from './RouteError'

/**
 * Application route tree. Pages that require a confirmed account go under a
 * `{ element: <ProtectedRoute />, children: [...] }` entry inside the shell.
 */
export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [
      { path: paths.home, element: <HomePage /> },
      { path: paths.listings, element: <MarketplacePage /> },
      { path: `${paths.listings}/:slug`, element: <ListingDetailPage /> },
      { path: paths.login, element: <LoginPage /> },
      { path: paths.register, element: <RegisterPage /> },
      { path: '/users/:username', element: <PublicProfilePage /> },
      {
        element: <ProtectedRoute />,
        children: [
          { path: paths.saved, element: <SavedListingsPage /> },
          { path: paths.profile, element: <ProfilePage /> },
          {
            path: paths.connections,
            element: <ConnectionsPage />,
            children: [
              { index: true, element: <Navigate to={paths.connectionsReceived} replace /> },
              // Keyed so switching tabs starts a fresh list rather than reusing the other one's state.
              { path: paths.connectionsReceived, element: <ConnectionList key="received" box="received" /> },
              { path: paths.connectionsSent, element: <ConnectionList key="sent" box="sent" /> },
            ],
          },
          { path: `${paths.connections}/:id`, element: <ConnectionDetailPage /> },
          {
            path: paths.messages,
            element: <MessagesPage />,
            children: [
              { index: true, element: <MessagesIndex /> },
              { path: `${paths.messages}/:id`, element: <ConversationView /> },
              { path: `${paths.messages}/connection/:connectionId`, element: <ConversationForConnection /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
