import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'

export function UserLink({ username, className, children }: { username: string; className?: string; children: ReactNode }) {
  return (
    <Link to={paths.user(username)} className={className ? `user-link ${className}` : 'user-link'}>
      {children}
    </Link>
  )
}
