import { Link, Navigate, useLocation } from 'react-router'
import { paths } from '../app/paths'
import { AuthLayout } from '../features/auth/AuthLayout'
import { LoginForm } from '../features/auth/LoginForm'
import { redirectTargetFrom } from '../features/auth/redirect'
import { useAuth } from '../features/auth/useAuth'

interface LoginLocationState {
  identifier?: unknown
  registered?: unknown
}

export function LoginPage() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'authenticated') {
    return <Navigate to={redirectTargetFrom(location.state)} replace />
  }

  const state = (location.state ?? {}) as LoginLocationState
  const identifier = typeof state.identifier === 'string' ? state.identifier : ''

  return (
    <AuthLayout
      eyebrow="Welcome back"
      title="Log in"
      intro="Use your email address or username."
      footer={
        <>
          New to Conflux? <Link to={paths.register}>Create an account</Link>
        </>
      }
    >
      {state.registered === true && (
        <div className="notice notice-success" role="status">
          <strong>Account created.</strong> Log in to get started.
        </div>
      )}
      <LoginForm initialIdentifier={identifier} />
    </AuthLayout>
  )
}
