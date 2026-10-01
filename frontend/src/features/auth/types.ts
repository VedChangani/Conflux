export type UserRole = 'USER' | 'ADMIN'

export type UserStatus = 'ACTIVE' | 'SUSPENDED'

export interface Account {
  id: number
  email: string
  username: string
  displayName: string
  role: UserRole
  status: UserStatus
}

export interface LoginRequest {
  identifier: string
  password: string
}

export interface RegisterRequest {
  email: string
  username: string
  password: string
  displayName: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}
