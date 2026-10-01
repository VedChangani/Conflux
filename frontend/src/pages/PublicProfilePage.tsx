import { useCallback } from 'react'
import { Link, useParams } from 'react-router'
import { paths } from '../app/paths'
import { LoadError } from '../components/LoadError'
import { Loading } from '../components/Loading'
import { StatusPanel } from '../components/StatusPanel'
import { useAuth } from '../features/auth/useAuth'
import { profileApi } from '../features/profile/profileApi'
import { ProfileView } from '../features/profile/ProfileView'
import { isValidUsername } from '../features/profile/validation'
import { ReportButton } from '../features/reports/ReportButton'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

/** Not a username the backend could ever accept: treated as its 404, without asking. */
class InvalidUsername extends Error {}

/** `/users/:username`: an active user's public profile. Open to everyone. */
export function PublicProfilePage() {
  const { username = '' } = useParams()
  const { account } = useAuth()

  const load = useCallback(
    (signal: AbortSignal) =>
      isValidUsername(username)
        ? profileApi.publicProfile(username.trim().toLowerCase(), signal)
        : Promise.reject(new InvalidUsername()),
    [username],
  )
  const result = useAsync(load)

  if (result.status === 'loading') {
    return (
      <div className="profile-page">
        <title>Loading profile… · Conflux</title>
        <Loading label="Loading profile…" />
      </div>
    )
  }

  if (result.status === 'error') {
    // Unknown and suspended accounts look the same: the backend answers 404 for both.
    const unavailable =
      result.error instanceof InvalidUsername || (result.error instanceof ApiError && result.error.status === 404)
    return unavailable ? (
      <StatusPanel
        code="404"
        title="Profile not found"
        documentTitle="Profile not found"
        actions={
          <Link to={paths.listings} className="button button-primary">
            Browse the marketplace
          </Link>
        }
      >
        This profile doesn’t exist, or it isn’t available.
      </StatusPanel>
    ) : (
      <div className="profile-page listing-error">
        <title>Profile unavailable · Conflux</title>
        <h1 className="visually-hidden">Profile unavailable</h1>
        <LoadError title="We couldn’t load this profile" error={result.error} onRetry={result.retry} />
      </div>
    )
  }

  const profile = result.data
  // From the verified session and the response, never from the URL alone.
  const isOwn = account !== null && account.username === profile.username

  return (
    <div className="profile-page">
      <title>{`${profile.displayName} (@${profile.username}) · Conflux`}</title>
      <ProfileView
        profile={profile}
        actions={
          isOwn ? (
            <Link to={paths.profile} className="button button-secondary button-small">
              Edit your profile
            </Link>
          ) : (
            <ReportButton
              target={{ type: 'USER', id: profile.id, description: `${profile.displayName} (@${profile.username})` }}
              accessibleLabel={`Report ${profile.displayName}`}
            />
          )
        }
      />
    </div>
  )
}
