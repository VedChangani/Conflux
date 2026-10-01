export type UserRole = 'USER' | 'ADMIN'

export type UserStatus = 'ACTIVE' | 'SUSPENDED'

/** The authenticated user's own account, as returned by `GET /auth/me` and `POST /auth/register`. */
export interface Account {
  id: number
  email: string
  username: string
  displayName: string
  role: UserRole
  status: UserStatus
}

/** `POST /auth/login` body. `identifier` is an email address or a username. */
export interface LoginRequest {
  identifier: string
  password: string
}

/** `POST /auth/register` body. */
export interface RegisterRequest {
  email: string
  username: string
  password: string
  displayName: string
}

/** `POST /auth/login` response. */
export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}
