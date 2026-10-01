import { SavedListings } from '../features/saved/SavedListings'

export function SavedListingsPage() {
  return (
    <div className="saved-page">
      <title>Saved listings · Conflux</title>
      <header className="page-header">
        <p className="eyebrow">Your account</p>
        <h1 className="page-title">Saved listings</h1>
        <p className="lead">Listings you’ve saved to come back to. Ones that are no longer published drop off this list.</p>
      </header>
      <SavedListings />
    </div>
  )
}
