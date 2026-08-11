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

const tokens = (accessToken: string, refreshToken: string) => ({
  accessToken,
  refreshToken,
  user: {
    id: 1,
    status: 'ACTIVE',
    role: 'USER',
    termsVersion: '2026-07-01',
  },
})

describe('API session', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    resetApiSessionForTests()
  })

  it('keeps access in module memory and refresh in sessionStorage', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(tokens('access-token', 'refresh-token')))
    const session = createApiSession({ fetchFn })

    await session.exchange('exchange-code')

    expect(getAccessToken()).toBe('access-token')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBe(
      'refresh-token',
    )
    expect(Object.keys(SESSION_STORAGE_KEYS)).toEqual(['refreshToken'])
    expect(localStorage.length).toBe(0)
    expect(document.cookie).not.toContain('access-token')
    expect(window.location.href).not.toContain('access-token')
    expect(session.getState()).toEqual({ status: 'authenticated' })
  })

  it('restores once after reload and saves the rotated refresh token', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEYS.refreshToken, 'previous-refresh')
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(tokens('restored-access', 'rotated-refresh')))
    const session = createApiSession({ fetchFn })

    await Promise.all([session.restore(), session.restore()])

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('restored-access')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBe(
      'rotated-refresh',
    )
    expect(session.getState()).toEqual({ status: 'authenticated' })
  })

  it('clears session state when refresh fails', async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEYS.refreshToken, 'expired-refresh')
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'INVALID_REFRESH_TOKEN' }, 401))
    const session = createApiSession({ fetchFn })

    await expect(session.restore()).rejects.toMatchObject({ status: 401 })

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBeNull()
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })

  it('clears session state after logout even when the request fails', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(tokens('access-token', 'refresh-token')))
      .mockResolvedValueOnce(jsonResponse({ code: 'LOGOUT_FAILED' }, 503))
    const session = createApiSession({ fetchFn })
    await session.exchange('exchange-code')

    await expect(session.logout()).rejects.toMatchObject({ status: 503 })

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBeNull()
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })

  it('clears session state on explicit logout after a 204 response', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(tokens('access-token', 'refresh-token')))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const session = createApiSession({ fetchFn })
    await session.exchange('exchange-code')

    await session.logout()

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.length).toBe(0)
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })
})
