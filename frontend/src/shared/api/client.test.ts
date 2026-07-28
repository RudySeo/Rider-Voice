import { ApiClient } from './client'
import { ApiError } from './errors'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('ApiClient', () => {
  it('serializes query parameters and attaches the in-memory bearer token', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        externalSearchStatus: 'AVAILABLE',
        candidates: [],
      }),
    )
    const client = new ApiClient({
      fetchFn,
      getBearerToken: () => 'access-token',
    })

    await client.request('/api/v1/restaurants/search', {
      method: 'get',
      query: { query: '강남 분식' },
      auth: 'access',
    })

    const [url, init] = fetchFn.mock.calls[0]
    expect(url).toBe('/api/v1/restaurants/search?query=%EA%B0%95%EB%82%A8+%EB%B6%84%EC%8B%9D')
    expect(new Headers(init?.headers).get('Authorization')).toBe(
      'Bearer access-token',
    )
  })

  it('returns undefined for a successful 204 response', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(null, { status: 204 }))
    const client = new ApiClient({
      fetchFn,
      getBearerToken: () => 'access-token',
    })

    const result = await client.request('/api/v1/auth/logout', {
      method: 'post',
      body: { refreshToken: 'refresh-token' },
      auth: 'access',
    })

    expect(result).toBeUndefined()
  })

  it('turns RFC 7807 responses into a safe error without provider detail', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          type: 'about:blank',
          title: 'Provider request failed',
          status: 503,
          detail: 'Kakao token secret-provider-token was rejected',
          code: 'EXTERNAL_PROVIDER_UNAVAILABLE',
        },
        503,
      ),
    )
    const client = new ApiClient({ fetchFn })

    const error = await client
      .request('/api/v1/restaurants/search', {
        method: 'get',
        query: { query: '강남 분식' },
      })
      .catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 503,
      code: 'EXTERNAL_PROVIDER_UNAVAILABLE',
    })
    expect((error as Error).message).not.toContain('secret-provider-token')
    expect((error as Error).message).not.toContain('Kakao')
  })

  it('shares one refresh across concurrent 401 responses and retries each request once', async () => {
    let accessToken = 'expired-access'
    let refreshCalls = 0
    const attempts = new Map<string, number>()
    const fetchFn = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      const url = String(input)
      const attempt = (attempts.get(url) ?? 0) + 1
      attempts.set(url, attempt)

      if (attempt === 1) {
        return jsonResponse({ code: 'UNAUTHORIZED' }, 401)
      }
      return jsonResponse({ id: url, status: 'ACTIVE', role: 'USER' })
    })
    const client = new ApiClient({
      fetchFn,
      getBearerToken: () => accessToken,
      refreshSession: async () => {
        refreshCalls += 1
        await Promise.resolve()
        accessToken = 'rotated-access'
      },
    })

    await Promise.all([
      client.request('/api/v1/users/me', {
        method: 'get',
        auth: 'access',
      }),
      client.request('/api/v1/users/me/reviews', {
        method: 'get',
        auth: 'access',
      }),
    ])

    expect(refreshCalls).toBe(1)
    expect(fetchFn).toHaveBeenCalledTimes(4)
    const retryHeaders = fetchFn.mock.calls.slice(2).map(([, init]) =>
      new Headers(init?.headers).get('Authorization'),
    )
    expect(retryHeaders).toEqual([
      'Bearer rotated-access',
      'Bearer rotated-access',
    ])
  })

  it('retries a 401 only once', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED' }, 401))
    const refreshSession = vi.fn().mockResolvedValue(undefined)
    const client = new ApiClient({
      fetchFn,
      getBearerToken: () => 'access-token',
      refreshSession,
    })

    await expect(
      client.request('/api/v1/users/me', {
        method: 'get',
        auth: 'access',
      }),
    ).rejects.toMatchObject({ status: 401 })

    expect(refreshSession).toHaveBeenCalledTimes(1)
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it.each([
    '/api/v1/auth/oauth2/exchange',
    '/api/v1/auth/refresh',
    '/api/v1/auth/consents',
    '/api/v1/auth/logout',
  ] as const)('does not refresh the session for %s', async (path) => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED' }, 401))
    const refreshSession = vi.fn().mockResolvedValue(undefined)
    const client = new ApiClient({
      fetchFn,
      getBearerToken: () => 'access-token',
      refreshSession,
    })

    await expect(
      client.request(path, {
        method: 'post',
        body:
          path === '/api/v1/auth/oauth2/exchange'
            ? { code: 'exchange-code' }
            : path === '/api/v1/auth/consents'
              ? { termsVersion: '2026-07-01' }
              : { refreshToken: 'refresh-token' },
        auth:
          path === '/api/v1/auth/consents'
            ? 'onboarding'
            : path === '/api/v1/auth/logout'
              ? 'access'
              : 'none',
      }),
    ).rejects.toMatchObject({ status: 401 })

    expect(refreshSession).not.toHaveBeenCalled()
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })
})
