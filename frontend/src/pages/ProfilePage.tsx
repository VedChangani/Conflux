import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { paths } from '../app/paths'
import { Button } from '../components/Button'
import { LoadError } from '../components/LoadError'
import { Loading } from '../components/Loading'
import { useAuth } from '../features/auth/useAuth'
import { profileApi } from '../features/profile/profileApi'
import { ProfileForm } from '../features/profile/ProfileForm'
import { ProfileView } from '../features/profile/ProfileView'
import type { UserProfile } from '../features/profile/types'
import { useAsync } from '../lib/useAsync'

type Mode = 'view' | 'edit' | 'saved'

/**
 * `/profile`: the signed-in user's own profile (`GET /profile`), with an edit mode.
 * Rendered behind {@link ProtectedRoute}.
 */
export function ProfilePage() {
  const { account, refreshAccount } = useAuth()
  const load = useCallback((signal: AbortSignal) => profileApi.ownProfile(signal), [])
  const result = useAsync(load)
  // The profile as the last successful save returned it.
  const [saved, setSaved] = useState<UserProfile | null>(null)
  const [mode, setMode] = useState<Mode>('view')
  const [focusAfter, setFocusAfter] = useState<Mode | null>(null)
  const editButtonRef = useRef<HTMLButtonElement>(null)
  const noticeRef = useRef<HTMLDivElement>(null)

  // Keep keyboard focus where the user is: the form's heading, the confirmation, or the Edit button.
  useEffect(() => {
    if (focusAfter === 'edit') {
      document.querySelector<HTMLElement>('.profile-edit h1')?.focus()
    } else if (focusAfter === 'saved') {
      noticeRef.current?.focus()
    } else if (focusAfter === 'view') {
      editButtonRef.current?.focus()
    }
  }, [focusAfter])

  function switchTo(next: Mode) {
    setMode(next)
    setFocusAfter(next)
  }

  function handleSaved(profile: UserProfile) {
    setSaved(profile)
    switchTo('saved')
    // The navigation shows the session's display name: read the account again if it changed.
    if (profile.displayName !== account?.displayName) {
      void refreshAccount()
    }
  }

  if (result.status === 'loading') {
    return (
      <div className="profile-page">
        <title>Your profile · Conflux</title>
        <Loading label="Loading your profile…" />
      </div>
    )
  }

  if (result.status === 'error') {
    return (
      <div className="profile-page listing-error">
        <title>Your profile · Conflux</title>
        <h1 className="visually-hidden">Your profile</h1>
        <LoadError title="We couldn’t load your profile" error={result.error} onRetry={result.retry} />
      </div>
    )
  }

  const profile = saved ?? result.data

  return (
    <div className="profile-page">
      <title>{mode === 'edit' ? 'Edit profile · Conflux' : 'Your profile · Conflux'}</title>
      {mode === 'edit' ? (
        <ProfileForm profile={profile} onSaved={handleSaved} onCancel={() => switchTo('view')} />
      ) : (
        <ProfileView
          profile={profile}
          isOwn
          actions={
            <>
              <Button ref={editButtonRef} className="button-small" onClick={() => switchTo('edit')}>
                Edit profile
              </Button>
              <Link to={paths.user(profile.username)} className="button button-secondary button-small">
                View public profile
              </Link>
            </>
          }
          notice={
            mode === 'saved' && (
              <div ref={noticeRef} tabIndex={-1} className="notice notice-success profile-notice" role="status">
                <strong>Profile saved.</strong> Your changes are live on your public profile.
              </div>
            )
          }
        />
      )}
    </div>
  )
}
