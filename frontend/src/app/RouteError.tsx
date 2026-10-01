import { Link } from 'react-router'
import { ErrorMessage } from '../components/ErrorMessage'
import { paths } from './paths'

/** Router `errorElement`: shown when a route throws while rendering. */
export function RouteError() {
  return (
    <main className="app-main">
      <ErrorMessage message="This page could not be displayed.">
        <Link to={paths.home} reloadDocument>
          Go to the home page
        </Link>
      </ErrorMessage>
    </main>
  )
}
