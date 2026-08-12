import type { components } from './generated'
import { ApiClient } from './client'
import { ApiError } from './errors'

type AccessSessionResponse = components['schemas']['AccessSessionResponse']

export type AuthState =
  | { status: 'anonymous' }
  | { status: 'authenticated' }

type SessionOptions = {
  baseUrl?: string
  fetchFn?: typeof fetch
}

let accessToken: string | null = null

export const getAccessToken = (): string | null => accessToken

export const resetApiSessionForTests = (): void => {
  accessToken = null
}

const requireAccessToken = (response: AccessSessionResponse): string => {
  if (
    typeof response.accessToken !== 'string' ||
    response.accessToken.length === 0
  ) {
    throw new ApiError(500, 'INVALID_API_RESPONSE')
  }
  return response.accessToken
}

export class ApiSession {
  readonly client: ApiClient

  private state: AuthState
  private readonly listeners = new Set<(state: AuthState) => void>()
  private refreshPromise: Promise<void> | null = null
  private restorePromise: Promise<void> | null = null

  constructor(options: SessionOptions = {}) {
    this.state = { status: 'anonymous' }
    this.client = new ApiClient({
      baseUrl: options.baseUrl,
      fetchFn: options.fetchFn,
      getBearerToken: () => accessToken,
      refreshSession: () => this.refresh(),
    })
  }

  getState(): AuthState {
    return this.state
  }

  subscribe(listener: (state: AuthState) => void): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  async restore(): Promise<void> {
    if (!this.restorePromise) {
      this.restorePromise = this.refresh()
    }
    return this.restorePromise
  }

  async refresh(): Promise<void> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.performRefresh().finally(() => {
        this.refreshPromise = null
      })
    }
    return this.refreshPromise
  }

  async logout(): Promise<void> {
    try {
      await this.client.request('/api/v1/auth/logout', {
        method: 'post',
        auth: 'none',
      })
    } finally {
      this.clear()
    }
  }

  clear(): void {
    accessToken = null
    this.setState({ status: 'anonymous' })
  }

  private async performRefresh(): Promise<void> {
    try {
      const response = await this.client.request('/api/v1/auth/refresh', {
        method: 'post',
        auth: 'none',
      })
      this.applyAccessToken(requireAccessToken(response))
    } catch (error) {
      this.clear()
      throw error
    }
  }

  private applyAccessToken(token: string): void {
    accessToken = token
    this.setState({ status: 'authenticated' })
  }

  private setState(state: AuthState): void {
    this.state = state
    for (const listener of this.listeners) {
      listener(state)
    }
  }
}

export const createApiSession = (options?: SessionOptions): ApiSession =>
  new ApiSession(options)
