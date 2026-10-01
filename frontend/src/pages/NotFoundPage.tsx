import { Link } from 'react-router'
import { paths } from '../app/paths'
import { Page } from '../components/Page'

export function NotFoundPage() {
  return (
    <Page title="Page not found">
      <p>The page you are looking for does not exist.</p>
      <Link to={paths.home}>Go to the home page</Link>
    </Page>
  )
}
