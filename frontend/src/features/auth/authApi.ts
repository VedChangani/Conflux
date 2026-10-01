import { apiClient } from '../../services/apiClient'
import type { Account, LoginRequest, RegisterRequest, TokenResponse } from './types'

export const authApi = {
  login: (request: LoginRequest) => apiClient.post<TokenResponse>('/auth/login', request, { auth: false }),
  register: (request: RegisterRequest) => apiClient.post<Account>('/auth/register', request, { auth: false }),
  me: () => apiClient.get<Account>('/auth/me'),
}
