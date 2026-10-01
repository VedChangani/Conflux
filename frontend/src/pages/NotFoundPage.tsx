import { Link } from 'react-router'
import { paths } from '../app/paths'
import { StatusPanel } from '../components/StatusPanel'

export function NotFoundPage() {
  return (
    <StatusPanel
      code="404"
      title="Page not found"
      documentTitle="Page not found"
      actions={
        <>
          <Link to={paths.listings} className="button button-primary">
            Browse the marketplace
          </Link>
          <Link to={paths.home} className="button button-secondary">
            Go to the home page
          </Link>
        </>
      }
    >
      The page you are looking for does not exist, or the link is out of date.
    </StatusPanel>
  )
}
