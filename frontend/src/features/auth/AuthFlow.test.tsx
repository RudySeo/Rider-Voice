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

  it('routes through the login notice without storing the current path', async () => {
    render(
      <MemoryRouter initialEntries={['/restaurants/17?tab=reviews#latest']}>
        <LoginButton />
      </MemoryRouter>,
    )

    const login = screen.getByRole('link', { name: '카카오로 로그인' })
    login.addEventListener('click', (event) => event.preventDefault())
    await userEvent.click(login)

    expect(login).toHaveAttribute('href', '/login')
    expect(sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)).toBeNull()
  })

  it('restores a new-user callback from the HttpOnly cookie once', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        accessToken: 'private-access-token',
        user: {
          id: 7,
          status: 'ACTIVE',
          role: 'USER',
        },
      }),
    )
    const session = createApiSession({ fetchFn })
    const router = renderApp('/auth/callback', session)

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/v1/auth/refresh',
      expect.objectContaining({
        method: 'POST',
        body: undefined,
        credentials: 'include',
      }),
    )
    expect(document.body).not.toHaveTextContent('private-access-token')
    expect(sessionStorage.length).toBe(0)
  })

  it('applies an existing-user session, clears a legacy return path, and goes home', async () => {
    sessionStorage.setItem(
      LOGIN_RETURN_PATH_KEY,
      '/me/reviews?cursor=next-review',
    )
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        accessToken: 'private-access-token',
        user: {
          id: 7,
          status: 'ACTIVE',
          role: 'USER',
        },
      }),
    )
    const router = renderApp(
      '/auth/callback',
      createApiSession({ fetchFn }),
    )

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))

    expect(fetchFn).toHaveBeenCalledTimes(1)
    expect(document.body).not.toHaveTextContent('private-access-token')
    expect(sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)).toBeNull()
  })

  it('shows only the trust notice before starting Kakao OAuth', async () => {
    renderApp('/login', createApiSession())

    expect(await screen.findByText(/라이더 신분이나 실제 방문을 인증하지 않습니다/)).toBeInTheDocument()
    expect(screen.queryByText(/필수 약관/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '카카오 로그인' })).toHaveAttribute(
      'href',
      OAUTH_LOGIN_PATH,
    )
  })

  it.each([
    ['/auth/callback?error=oauth_failed&error_description=provider-secret', true],
  ] as const)(
    'shows one generic retry UI for callback failure %s',
    async (path, callsApi) => {
      const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
        jsonResponse(
          {
            code: 'AUTHENTICATION_REQUIRED',
            detail: 'invalid cookie provider-secret',
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

  it('shows a safe message when the refresh cookie is rejected', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          code: 'AUTHENTICATION_REQUIRED',
          detail: 'invalid cookie provider-secret',
        },
        401,
      ),
    )
    const router = renderApp(
      '/auth/callback',
      createApiSession({ fetchFn }),
    )

    expect(
      await screen.findByRole('heading', { name: '로그인을 완료하지 못했습니다' }),
    ).toBeInTheDocument()
    expect(screen.getByText('로그인이 필요하거나 세션이 만료되었습니다.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/auth/callback')
    expect(router.state.location.search).toBe('')
    expect(document.body).not.toHaveTextContent('provider-secret')
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('uses the same generic retry UI for a network failure', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new Error('network provider-secret'))
    const router = renderApp(
      '/auth/callback',
      createApiSession({ fetchFn }),
    )

    expect(
      await screen.findByRole('heading', { name: '로그인을 완료하지 못했습니다' }),
    ).toBeInTheDocument()
    expect(screen.getByText('잠시 후 카카오 로그인을 다시 시작해 주세요.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/auth/callback')
    expect(router.state.location.search).toBe('')
    expect(document.body).not.toHaveTextContent('provider-secret')
  })

  it('keeps protected content hidden during restore without preserving its path', async () => {
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
    expect(sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)).toBeNull()
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
