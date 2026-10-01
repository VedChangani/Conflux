import { Link } from 'react-router'
import { StatusPanel } from '../components/StatusPanel'
import { paths } from './paths'

export function RouteError() {
  return (
    <main className="app-main">
      <div role="alert">
        <StatusPanel
          code="Error"
          title="This page could not be displayed"
          documentTitle="Something went wrong"
          actions={
            <Link to={paths.home} reloadDocument className="button button-primary">
              Go to the home page
            </Link>
          }
        >
          Something went wrong while showing this page. Going back to the home page usually fixes it.
        </StatusPanel>
      </div>
    </main>
  )
}
