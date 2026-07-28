import {
  createApiSession,
  getAccessToken,
  resetApiSessionForTests,
  SESSION_STORAGE_KEYS,
} from './session'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('API session', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    resetApiSessionForTests()
  })

  it('keeps access in module memory and refresh in sessionStorage', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        termsAgreed: true,
        onboardingToken: null,
        tokens: {
          accessToken: 'access-token',
          refreshToken: 'refresh-token',
        },
      }),
    )
    const session = createApiSession({ fetchFn })

    await session.exchange('exchange-code')

    expect(getAccessToken()).toBe('access-token')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBe(
      'refresh-token',
    )
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.onboardingToken)).toBeNull()
    expect(localStorage.length).toBe(0)
    expect(document.cookie).not.toContain('access-token')
    expect(window.location.href).not.toContain('access-token')
    expect(session.getState()).toEqual({ status: 'authenticated' })
  })

  it('stores only onboarding token in sessionStorage for pending terms', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        termsAgreed: false,
        onboardingToken: 'onboarding-token',
        tokens: null,
      }),
    )
    const session = createApiSession({ fetchFn })

    await session.exchange('exchange-code')

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.onboardingToken)).toBe(
      'onboarding-token',
    )
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBeNull()
    expect(session.getState()).toEqual({ status: 'onboarding' })
  })

  it('restores once after reload and saves the rotated refresh token', async () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEYS.refreshToken,
      'previous-refresh',
    )
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        accessToken: 'restored-access',
        refreshToken: 'rotated-refresh',
        user: {
          id: 1,
          status: 'ACTIVE',
          role: 'USER',
          termsVersion: '2026-07-01',
        },
      }),
    )
    const session = createApiSession({ fetchFn })

    await Promise.all([session.restore(), session.restore()])

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('restored-access')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBe(
      'rotated-refresh',
    )
    expect(session.getState()).toEqual({ status: 'authenticated' })
  })

  it('clears every session token when refresh fails', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEYS.refreshToken, 'expired-refresh')
    sessionStorage.setItem(
      SESSION_STORAGE_KEYS.onboardingToken,
      'stale-onboarding',
    )
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'INVALID_REFRESH_TOKEN' }, 401))
    const session = createApiSession({ fetchFn })

    await expect(session.restore()).rejects.toMatchObject({ status: 401 })

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.onboardingToken)).toBeNull()
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })

  it('keeps the onboarding session when consent fails so it can be retried', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({
          termsAgreed: false,
          onboardingToken: 'onboarding-token',
          tokens: null,
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ code: 'SERVICE_UNAVAILABLE' }, 503))
    const session = createApiSession({ fetchFn })
    await session.exchange('exchange-code')

    await expect(session.consent('2026-07-01')).rejects.toMatchObject({
      status: 503,
    })

    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.onboardingToken)).toBe(
      'onboarding-token',
    )
    expect(session.getState()).toEqual({ status: 'onboarding' })
  })

  it('clears session state after logout even when the request fails', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({
          termsAgreed: true,
          onboardingToken: null,
          tokens: {
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
          },
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ code: 'LOGOUT_FAILED' }, 503))
    const session = createApiSession({ fetchFn })
    await session.exchange('exchange-code')

    await expect(session.logout()).rejects.toMatchObject({ status: 503 })

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.onboardingToken)).toBeNull()
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })

  it('clears session state on explicit logout after a 204 response', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({
          termsAgreed: true,
          onboardingToken: null,
          tokens: {
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
          },
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const session = createApiSession({ fetchFn })
    await session.exchange('exchange-code')

    await session.logout()

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.length).toBe(0)
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })
})
