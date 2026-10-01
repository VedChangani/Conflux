import { clearAccessToken, getAccessToken } from '../features/auth/tokenStorage'
import { getApiBaseUrl } from '../lib/env'
import type { FieldError, ProblemDetail } from '../types/api'

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null, message?: string) {
    super(message ?? problem?.detail ?? problem?.title ?? 'Something went wrong. Please try again.')
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  get fieldErrors(): FieldError[] {
    return this.problem?.errors ?? []
  }
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  method?: HttpMethod
  body?: unknown
  headers?: HeadersInit
  signal?: AbortSignal
  auth?: boolean
}

type UnauthorizedListener = () => void

const unauthorizedListeners = new Set<UnauthorizedListener>()

export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => {
    unauthorizedListeners.delete(listener)
  }
}

function buildUrl(path: string): string {
  return `${getApiBaseUrl()}/${path.replace(/^\/+/, '')}`
}

function isJson(response: Response): boolean {
  const contentType = response.headers.get('Content-Type') ?? ''
  return contentType.includes('application/json') || contentType.includes('+json')
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  if (!isJson(response)) {
    return null
  }
  try {
    const body: unknown = await response.json()
    return body !== null && typeof body === 'object' ? (body as ProblemDetail) : null
  } catch {
    return null
  }
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) {
    return undefined
  }
  return isJson(response) ? JSON.parse(text) : text
}

export interface ApiResponse<T> {
  status: number
  data: T
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await apiRequestWithStatus<T>(path, options)).data
}

export async function apiRequestWithStatus<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const { method = 'GET', body, signal, auth = true } = options
  const url = buildUrl(path)

  const headers = new Headers(options.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json, application/problem+json')
  }
  let requestBody: string | undefined
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
    requestBody = JSON.stringify(body)
  }
  const token = auth ? getAccessToken() : null
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(url, { method, headers, body: requestBody, signal })
  } catch (error) {
    if (signal?.aborted) {
      throw error
    }
    throw new ApiError(0, null, 'Unable to reach the server. Check your connection and try again.')
  }

  if (!response.ok) {
    const problem = await readProblem(response)
    if (response.status === 401 && token) {
      clearAccessToken()
      unauthorizedListeners.forEach((listener) => listener())
    }
    throw new ApiError(response.status, problem)
  }

  return { status: response.status, data: (await readBody(response)) as T }
}

type MethodOptions = Omit<RequestOptions, 'method' | 'body'>

export const apiClient = {
  get: <T>(path: string, options?: MethodOptions) => apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: MethodOptions) =>
    apiRequest<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: MethodOptions) =>
    apiRequest<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: MethodOptions) =>
    apiRequest<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: MethodOptions) => apiRequest<T>(path, { ...options, method: 'DELETE' }),
}
