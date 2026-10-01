import { useId, type ReactNode } from 'react'
import { initialOf } from '../listings/format'
import type { UserProfile } from './types'
import { isHttpUrl } from './validation'

const memberSince = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' })

function formatMemberSince(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : memberSince.format(date)
}

/** `https://github.com/ada/` → `github.com/ada`: the scheme adds nothing for people reading. */
function displayUrl(url: string): string {
  return url.replace(/^https?:\/\//i, '').replace(/\/$/, '')
}

interface ProfileViewProps {
  profile: UserProfile
  /** The signed-in user's own profile: empty fields invite them to fill them in. */
  isOwn?: boolean
  /** Buttons or links next to the name, e.g. "Edit profile". */
  actions?: ReactNode
  /** Shown under the header, e.g. a "saved" confirmation. */
  notice?: ReactNode
}

/**
 * A profile with exactly the public fields the backend returns: name, username, bio,
 * location, links and when the account was created. The id is never shown.
 */
export function ProfileView({ profile, isOwn = false, actions, notice }: ProfileViewProps) {
  const nameId = useId()
  const aboutId = useId()
  const linksId = useId()
  const links = [
    { label: 'Website', url: profile.websiteUrl },
    { label: 'GitHub', url: profile.githubUrl },
    { label: 'LinkedIn', url: profile.linkedinUrl },
  ].filter((link): link is { label: string; url: string } => Boolean(link.url))

  return (
    <article className="profile" aria-labelledby={nameId}>
      <header className="profile-header">
        <span className="avatar profile-avatar" aria-hidden="true">
          {initialOf(profile.displayName)}
        </span>
        <div className="profile-identity">
          <h1 id={nameId} className="profile-name">
            {profile.displayName}
          </h1>
          <p className="profile-username">@{profile.username}</p>
          {(profile.location || profile.createdAt) && (
            <ul className="profile-facts">
              {profile.location && (
                <li>
                  <span className="profile-fact-label">Based in</span> {profile.location}
                </li>
              )}
              {profile.createdAt && (
                <li>
                  <span className="profile-fact-label">Member since</span>{' '}
                  <time dateTime={profile.createdAt}>{formatMemberSince(profile.createdAt)}</time>
                </li>
              )}
            </ul>
          )}
        </div>
        {actions && <div className="profile-actions">{actions}</div>}
      </header>

      {notice}

      <div className="profile-layout">
        <section className="detail-section" aria-labelledby={aboutId}>
          <h2 id={aboutId} className="detail-section-title">
            About
          </h2>
          {profile.bio ? (
            <p className="profile-bio">{profile.bio}</p>
          ) : (
            <p className="profile-empty">{isOwn ? 'You haven’t written a bio yet.' : 'No bio yet.'}</p>
          )}
        </section>

        <section className="profile-links-panel" aria-labelledby={linksId}>
          <h2 id={linksId} className="eyebrow">
            Links
          </h2>
          {links.length > 0 ? (
            <dl className="profile-links">
              {links.map(({ label, url }) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>
                    {/* Shown as a link only if it is the http(s) URL the backend promises. */}
                    {isHttpUrl(url) ? (
                      <a href={url} target="_blank" rel="noopener noreferrer nofollow ugc">
                        {displayUrl(url)}
                        <span className="visually-hidden"> (opens in a new tab)</span>
                      </a>
                    ) : (
                      displayUrl(url)
                    )}
                  </dd>
                </div>
              ))}
            </dl>
          ) : (
            <p className="profile-empty">{isOwn ? 'You haven’t added any links yet.' : 'No links added.'}</p>
          )}
        </section>
      </div>
    </article>
  )
}
