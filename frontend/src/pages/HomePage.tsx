import { Link, useNavigate } from 'react-router'
import { paths } from '../app/paths'
import { useAuth } from '../features/auth/useAuth'
import { SearchForm } from '../features/listings/DiscoveryControls'
import { withChanges } from '../features/listings/discoveryParams'
import { ASSET_TYPE_LABELS } from '../features/listings/labels'
import { LatestListings } from '../features/listings/LatestListings'
import { ASSET_TYPES } from '../features/listings/types'

export function HomePage() {
  const { status, account } = useAuth()
  const navigate = useNavigate()

  const browseHref = (changes: Parameters<typeof withChanges>[1]) => {
    const search = withChanges(new URLSearchParams(), changes).toString()
    return search ? `${paths.listings}?${search}` : paths.listings
  }

  return (
    <div className="home">
      <title>Conflux · Ideas, projects and startups</title>
      <section className="hero" aria-labelledby="home-heading">
        <h1 id="home-heading" className="hero-kicker">
          Conflux
        </h1>
        <p className="hero-statement">
          Ideas, projects and startups, <mark>ready for their next builder.</mark>
        </p>
        <p className="lead">
          {status === 'authenticated' && account
            ? `Welcome back, ${account.displayName}. See what's new on the marketplace.`
            : 'Acquire a promising project, or find the people to build it with.'}
        </p>

        <div className="hero-search">
          <SearchForm search="" onSearch={(search) => void navigate(browseHref({ search }))} />
        </div>

        <nav className="hero-shortcuts" aria-label="Browse by type">
          <span className="hero-shortcuts-label">Browse</span>
          <ul>
            {ASSET_TYPES.map((assetType) => (
              <li key={assetType}>
                <Link to={browseHref({ assetType })} className="shortcut">
                  {ASSET_TYPE_LABELS[assetType]}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        {status === 'anonymous' && (
          <p className="hero-account">
            Have something to list? <Link to={paths.register}>Create an account</Link>
          </p>
        )}
      </section>

      <LatestListings />
    </div>
  )
}
