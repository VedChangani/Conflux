import { useEffect, useRef, useState } from 'react'
import { Link, Navigate } from 'react-router'
import { paths } from '../app/paths'
import { AuthLayout } from '../features/auth/AuthLayout'
import { RegisterForm, type RegisteredAccount } from '../features/auth/RegisterForm'
import { useAuth } from '../features/auth/useAuth'

export function RegisterPage() {
  const { status } = useAuth()
  const [registered, setRegistered] = useState<RegisteredAccount | null>(null)

  if (status === 'authenticated') {
    return <Navigate to={paths.home} replace />
  }

  return (
    <AuthLayout
      eyebrow="Join Conflux"
      title="Create an account"
      intro="List what you're building, or find something to build next."
      footer={
        registered ? undefined : (
          <>
            Already have an account? <Link to={paths.login}>Log in</Link>
          </>
        )
      }
    >
      {registered ? <RegistrationSuccess account={registered} /> : <RegisterForm onRegistered={setRegistered} />}
    </AuthLayout>
  )
}

function RegistrationSuccess({ account }: { account: RegisteredAccount }) {
  const headingRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  return (
    <div className="registration-success">
      <div className="notice notice-success">
        <h2 ref={headingRef} tabIndex={-1} className="notice-title">
          Account created
        </h2>
        <p>
          Welcome to Conflux, {account.displayName}. Log in as <strong>@{account.username}</strong> to get started.
        </p>
      </div>
      <Link
        to={paths.login}
        state={{ identifier: account.username, registered: true }}
        className="button button-primary button-block"
      >
        Continue to log in
      </Link>
    </div>
  )
}
