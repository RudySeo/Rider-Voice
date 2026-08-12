import {
  createApiSession,
  getAccessToken,
  resetApiSessionForTests,
} from './session'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const accessSession = (accessToken: string) => ({
  accessToken,
  user: {
    id: 1,
    status: 'ACTIVE',
    role: 'USER',
  },
})

describe('API session', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    resetApiSessionForTests()
  })

  it('restores from the HttpOnly cookie and keeps only access in memory', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(accessSession('access-token')))
    const session = createApiSession({ fetchFn })

    await session.restore()

    expect(getAccessToken()).toBe('access-token')
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/v1/auth/refresh',
      expect.objectContaining({
        method: 'POST',
        body: undefined,
        credentials: 'include',
      }),
    )
    expect(sessionStorage.length).toBe(0)
    expect(localStorage.length).toBe(0)
    expect(document.cookie).not.toContain('access-token')
    expect(window.location.href).not.toContain('access-token')
    expect(session.getState()).toEqual({ status: 'authenticated' })
  })

  it('restores once after reload while the backend rotates the cookie', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(accessSession('restored-access')))
    const session = createApiSession({ fetchFn })

    await Promise.all([session.restore(), session.restore()])

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('restored-access')
    expect(session.getState()).toEqual({ status: 'authenticated' })
  })

  it('clears session state when refresh fails', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'INVALID_REFRESH_TOKEN' }, 401))
    const session = createApiSession({ fetchFn })

    await expect(session.restore()).rejects.toMatchObject({ status: 401 })

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.length).toBe(0)
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })

  it('clears session state after logout even when the request fails', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(accessSession('access-token')))
      .mockResolvedValueOnce(jsonResponse({ code: 'LOGOUT_FAILED' }, 503))
    const session = createApiSession({ fetchFn })
    await session.restore()

    await expect(session.logout()).rejects.toMatchObject({ status: 503 })

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.length).toBe(0)
    expect(session.getState()).toEqual({ status: 'anonymous' })
  })

  it('clears session state on explicit logout after a 204 response', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(accessSession('access-token')))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const session = createApiSession({ fetchFn })
    await session.restore()

    await session.logout()

    expect(getAccessToken()).toBeNull()
    expect(sessionStorage.length).toBe(0)
    expect(session.getState()).toEqual({ status: 'anonymous' })
    expect(fetchFn).toHaveBeenLastCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
        body: undefined,
        credentials: 'include',
      }),
    )
  })
})
