import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  MemoryRouter,
  Route,
  Routes,
  createMemoryRouter,
} from 'react-router-dom'

import { App } from '@/app/App'
import { appRoutes } from '@/app/router'
import {
  AuthProvider,
  type AuthSession,
  LOGIN_RETURN_PATH_KEY,
  LoginButton,
  OAUTH_LOGIN_PATH,
  ProtectedRoute,
} from '@/features/auth/AuthFlow'
import {
  createApiSession,
  resetApiSessionForTests,
  SESSION_STORAGE_KEYS,
  type AuthState,
} from '@/shared/api/session'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const renderApp = (path: string, session: AuthSession) => {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  render(<App router={router} session={session} />)
  return router
}

class FakeAuthSession implements AuthSession {
  private state: AuthState
  private readonly listeners = new Set<(state: AuthState) => void>()
  readonly restore = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
  readonly exchange = vi.fn<AuthSession['exchange']>()
  readonly logout = vi.fn(async () => {
    this.setState({ status: 'anonymous' })
  })

  constructor(state: AuthState) {
    this.state = state
  }

  getState = () => this.state

  subscribe = (listener: (state: AuthState) => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  private setState(state: AuthState) {
    this.state = state
    this.listeners.forEach((listener) => listener(state))
  }
}

describe('auth user flow', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    resetApiSessionForTests()
  })

  it('stores the current internal path before routing through the login notice', async () => {
    render(
      <MemoryRouter initialEntries={['/restaurants/17?tab=reviews#latest']}>
        <LoginButton />
      </MemoryRouter>,
    )

    const login = screen.getByRole('link', { name: '카카오로 로그인' })
    login.addEventListener('click', (event) => event.preventDefault())
    await userEvent.click(login)

    expect(login).toHaveAttribute('href', '/login')
    expect(sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)).toBe(
      '/restaurants/17?tab=reviews#latest',
    )
  })

  it('exchanges a new-user callback code once and applies the service session', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        accessToken: 'private-access-token',
        refreshToken: 'private-refresh-token',
        user: {
          id: 7,
          status: 'ACTIVE',
          role: 'USER',
          termsVersion: '2026-07-01',
        },
      }),
    )
    const session = createApiSession({ fetchFn })
    const router = renderApp('/auth/callback?code=single-use-code', session)

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/v1/auth/oauth2/exchange',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ code: 'single-use-code' }),
      }),
    )
    expect(document.body).not.toHaveTextContent('private-access-token')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEYS.refreshToken)).toBe(
      'private-refresh-token',
    )
  })

  it('applies an existing-user session and returns only to a safe stored internal path', async () => {
    sessionStorage.setItem(
      LOGIN_RETURN_PATH_KEY,
      '/me/reviews?cursor=next-review',
    )
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        accessToken: 'private-access-token',
        refreshToken: 'private-refresh-token',
        user: {
          id: 7,
          status: 'ACTIVE',
          role: 'USER',
          termsVersion: '2026-07-01',
        },
      }),
    )
    const router = renderApp(
      '/auth/callback?code=single-use-code',
      createApiSession({ fetchFn }),
    )

    await waitFor(() =>
      expect(`${router.state.location.pathname}${router.state.location.search}`).toBe(
        '/me/reviews?cursor=next-review',
      ),
    )

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(document.body).not.toHaveTextContent('private-access-token')
    expect(document.body).not.toHaveTextContent('private-refresh-token')
    expect(sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)).toBeNull()
  })

  it.each([
    'https://attacker.example/steal',
    '//attacker.example/steal',
    '/\\attacker.example/steal',
  ])('rejects the unsafe stored return target %s', async (returnTarget) => {
    sessionStorage.setItem(LOGIN_RETURN_PATH_KEY, returnTarget)
    const session = createApiSession({
      fetchFn: vi.fn<typeof fetch>().mockResolvedValue(
        jsonResponse({
          accessToken: 'access-token',
          refreshToken: 'refresh-token',
          user: {
            id: 7,
            status: 'ACTIVE',
            role: 'USER',
            termsVersion: '2026-07-01',
          },
        }),
      ),
    })
    const router = renderApp('/auth/callback?code=code', session)

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('shows the terms and trust notice before starting Kakao OAuth', async () => {
    renderApp('/login', createApiSession())

    expect(
      await screen.findByText(/필수 약관에 동의한 것으로 처리됩니다/),
    ).toBeInTheDocument()
    expect(screen.getByText(/라이더 신분이나 실제 방문을 인증하지 않습니다/)).toBeInTheDocument()
    expect(
      screen
        .getAllByRole('link', { name: '카카오로 로그인' })
        .some((link) => link.getAttribute('href') === OAUTH_LOGIN_PATH),
    ).toBe(true)
  })

  it.each([
    ['/auth/callback?error=oauth_failed&error_description=provider-secret', false],
    ['/auth/callback', false],
    ['/auth/callback?code=expired-or-reused', true],
  ] as const)(
    'shows one generic retry UI for callback failure %s',
    async (path, callsApi) => {
      const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
        jsonResponse(
          {
            code: 'INVALID_OAUTH_EXCHANGE_CODE',
            detail: 'expired code provider-secret',
          },
          401,
        ),
      )
      renderApp(path, createApiSession({ fetchFn }))

      expect(
        await screen.findByRole('heading', { name: '로그인을 완료하지 못했습니다' }),
      ).toBeInTheDocument()
      expect(screen.getByRole('link', { name: '다시 로그인' })).toHaveAttribute(
        'href',
        '/login',
      )
      expect(document.body).not.toHaveTextContent('provider-secret')
      expect(fetchFn).toHaveBeenCalledTimes(callsApi ? 1 : 0)
    },
  )

  it('uses the same generic retry UI for a network failure', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new Error('network provider-secret'))
    renderApp(
      '/auth/callback?code=single-use-code',
      createApiSession({ fetchFn }),
    )

    expect(
      await screen.findByRole('heading', { name: '로그인을 완료하지 못했습니다' }),
    ).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('provider-secret')
  })

  it('keeps protected content hidden during restore and preserves its path for login', async () => {
    let finishRestore: (() => void) | undefined
    const session = new FakeAuthSession({ status: 'anonymous' })
    session.restore.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishRestore = resolve
        }),
    )

    render(
      <AuthProvider session={session}>
        <MemoryRouter initialEntries={['/reviews/new?restaurantId=17']}>
          <Routes>
            <Route
              path="/reviews/new"
              element={
                <ProtectedRoute>
                  <h1>보호된 리뷰 작성 화면</h1>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<h1>로그인 화면</h1>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('로그인 상태 확인 중')
    expect(screen.queryByText('보호된 리뷰 작성 화면')).not.toBeInTheDocument()

    finishRestore?.()

    expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument()
    expect(sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)).toBe(
      '/reviews/new?restaurantId=17',
    )
  })

  it('shows authenticated header actions and clears them after logout', async () => {
    const session = new FakeAuthSession({ status: 'authenticated' })
    renderApp('/', session)

    expect(await screen.findByRole('link', { name: '내 리뷰' })).toHaveAttribute(
      'href',
      '/me/reviews',
    )
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(session.logout).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('link', { name: '카카오로 로그인' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '내 리뷰' })).not.toBeInTheDocument()
  })

})
