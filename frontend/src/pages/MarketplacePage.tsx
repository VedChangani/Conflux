import { ListingDiscovery } from '../features/listings/ListingDiscovery'

export function MarketplacePage() {
  return (
    <div className="marketplace">
      <title>Marketplace · Conflux</title>
      <header className="page-header">
        <p className="eyebrow">Marketplace</p>
        <h1 className="page-title">Browse listings</h1>
        <p className="lead">Ideas, projects and startups looking for a buyer or their next collaborators.</p>
      </header>
      <ListingDiscovery />
    </div>
  )
}
