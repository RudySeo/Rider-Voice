import type { components } from './generated'
import { ApiClient } from './client'
import { ApiError } from './errors'

type AuthTokensResponse = components['schemas']['AuthTokensResponse']
type OAuth2LoginResponse = components['schemas']['OAuth2LoginResponse']

export const SESSION_STORAGE_KEYS = {
  refreshToken: 'riderVoice.refreshToken',
  onboardingToken: 'riderVoice.onboardingToken',
} as const

export type AuthState =
  | { status: 'anonymous' }
  | { status: 'onboarding' }
  | { status: 'authenticated' }

type SessionOptions = {
  baseUrl?: string
  fetchFn?: typeof fetch
  storage?: Storage
}

let accessToken: string | null = null

export const getAccessToken = (): string | null => accessToken

export const resetApiSessionForTests = (): void => {
  accessToken = null
}

const requireTokens = (
  response: AuthTokensResponse,
): { accessToken: string; refreshToken: string } => {
  if (
    typeof response.accessToken !== 'string' ||
    response.accessToken.length === 0 ||
    typeof response.refreshToken !== 'string' ||
    response.refreshToken.length === 0
  ) {
    throw new ApiError(500, 'INVALID_API_RESPONSE')
  }
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
  }
}

export class ApiSession {
  readonly client: ApiClient

  private readonly storage: Storage
  private state: AuthState
  private readonly listeners = new Set<(state: AuthState) => void>()
  private refreshPromise: Promise<void> | null = null
  private restorePromise: Promise<void> | null = null

  constructor(options: SessionOptions = {}) {
    this.storage = options.storage ?? sessionStorage
    this.state = this.storage.getItem(SESSION_STORAGE_KEYS.onboardingToken)
      ? { status: 'onboarding' }
      : { status: 'anonymous' }
    this.client = new ApiClient({
      baseUrl: options.baseUrl,
      fetchFn: options.fetchFn,
      getBearerToken: (auth) =>
        auth === 'access'
          ? accessToken
          : this.storage.getItem(SESSION_STORAGE_KEYS.onboardingToken),
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

  async exchange(code: string): Promise<OAuth2LoginResponse> {
    const response = await this.client.request(
      '/api/v1/auth/oauth2/exchange',
      {
        method: 'post',
        body: { code },
        auth: 'none',
      },
    )

    if (response.termsAgreed && response.tokens) {
      this.applyServiceTokens(response.tokens)
      return response
    }
    if (!response.termsAgreed && response.onboardingToken) {
      this.applyOnboardingToken(response.onboardingToken)
      return response
    }

    this.clear()
    throw new ApiError(500, 'INVALID_API_RESPONSE')
  }

  async consent(termsVersion: string): Promise<void> {
    const response = await this.client.request('/api/v1/auth/consents', {
      method: 'post',
      body: { termsVersion },
      auth: 'onboarding',
    })
    this.applyServiceTokens(requireTokens(response))
  }

  async restore(): Promise<void> {
    if (!this.storage.getItem(SESSION_STORAGE_KEYS.refreshToken)) {
      accessToken = null
      return
    }
    if (!this.restorePromise) {
      this.restorePromise = this.refresh().finally(() => {
        this.restorePromise = null
      })
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
    const refreshToken = this.storage.getItem(
      SESSION_STORAGE_KEYS.refreshToken,
    )
    try {
      if (refreshToken) {
        await this.client.request('/api/v1/auth/logout', {
          method: 'post',
          body: { refreshToken },
          auth: 'access',
        })
      }
    } finally {
      this.clear()
    }
  }

  clear(): void {
    accessToken = null
    this.storage.removeItem(SESSION_STORAGE_KEYS.refreshToken)
    this.storage.removeItem(SESSION_STORAGE_KEYS.onboardingToken)
    this.setState({ status: 'anonymous' })
  }

  private async performRefresh(): Promise<void> {
    const refreshToken = this.storage.getItem(
      SESSION_STORAGE_KEYS.refreshToken,
    )
    if (!refreshToken) {
      this.clear()
      throw new ApiError(401, 'INVALID_REFRESH_TOKEN')
    }

    try {
      const response = await this.client.request('/api/v1/auth/refresh', {
        method: 'post',
        body: { refreshToken },
        auth: 'none',
      })
      this.applyServiceTokens(requireTokens(response))
    } catch (error) {
      this.clear()
      throw error
    }
  }

  private applyServiceTokens(tokens: {
    accessToken: string
    refreshToken: string
  }): void {
    accessToken = tokens.accessToken
    this.storage.setItem(
      SESSION_STORAGE_KEYS.refreshToken,
      tokens.refreshToken,
    )
    this.storage.removeItem(SESSION_STORAGE_KEYS.onboardingToken)
    this.setState({ status: 'authenticated' })
  }

  private applyOnboardingToken(token: string): void {
    accessToken = null
    this.storage.removeItem(SESSION_STORAGE_KEYS.refreshToken)
    this.storage.setItem(SESSION_STORAGE_KEYS.onboardingToken, token)
    this.setState({ status: 'onboarding' })
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
