import { Link, useNavigate } from 'react-router'
import { paths } from '../app/paths'
import { ListingForm } from '../features/listings/ListingForm'

export function NewListingPage() {
  const navigate = useNavigate()

  return (
    <div className="listing-editor-page">
      <title>Create a listing · Conflux</title>
      <nav aria-label="Breadcrumb" className="back-nav">
        <Link to={paths.myListings} className="back-link">
          <span aria-hidden="true">←</span> Back to my listings
        </Link>
      </nav>
      <section className="listing-editor" aria-labelledby="new-listing-heading">
        <header className="listing-editor-header">
          <p className="eyebrow">My listings</p>
          <h1 id="new-listing-heading" className="page-title">
            Create a listing
          </h1>
          <p className="lead">
            New listings start as private drafts. Save the draft, then publish it when you’re ready for the marketplace.
          </p>
        </header>
        <ListingForm
          mode="create"
          onSaved={(listing) => void navigate(paths.myListing(listing.id), { state: { listing, notice: 'created' } })}
          onCancel={() => void navigate(paths.myListings)}
        />
      </section>
    </div>
  )
}
