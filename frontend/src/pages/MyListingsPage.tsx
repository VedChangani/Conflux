import { Link } from 'react-router'
import { paths } from '../app/paths'
import { MyListings } from '../features/listings/MyListings'

export function MyListingsPage() {
  return (
    <div className="my-listings-page">
      <title>My listings · Conflux</title>
      <header className="page-header my-listings-header">
        <div className="my-listings-intro">
          <p className="eyebrow">Your account</p>
          <h1 className="page-title">My listings</h1>
          <p className="lead">Drafts, published, archived and suspended listings. Only you can see this page.</p>
        </div>
        <Link to={paths.newListing} className="button button-primary">
          Create listing
        </Link>
      </header>
      <MyListings />
    </div>
  )
}
