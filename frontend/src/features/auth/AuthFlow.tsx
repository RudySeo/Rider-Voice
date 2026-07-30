import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { useForm } from 'react-hook-form'
import {
  Link,
  Navigate,
  useLocation,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'
import { z } from 'zod'

import type { components } from '@/shared/api/generated'
import {
  createApiSession,
  type ApiSession,
  type AuthState,
} from '@/shared/api/session'

import styles from './AuthFlow.module.css'

type OAuth2LoginResponse = components['schemas']['OAuth2LoginResponse']

export const OAUTH_LOGIN_PATH =
  '/api/v1/auth/oauth2/authorization/kakao' as const
export const LOGIN_RETURN_PATH_KEY = 'riderVoice.loginReturnPath' as const
export const CURRENT_TERMS_VERSION = '2026-07-01' as const

const AUTH_FLOW_PATHS = new Set(['/login', '/auth/callback', '/consent'])

export type AuthSession = Pick<
  ApiSession,
  'getState' | 'subscribe' | 'restore' | 'consent' | 'logout'
> & {
  exchange(code: string): Promise<OAuth2LoginResponse>
}

type AuthContextValue = {
  session: AuthSession
  state: AuthState
  restoring: boolean
}

type AuthProviderProps = {
  children: ReactNode
  session: AuthSession
}

// The singleton is browser-memory state; tests can inject an isolated session.
// eslint-disable-next-line react-refresh/only-export-components
export const apiSession = createApiSession()

const AuthContext = createContext<AuthContextValue>({
  session: apiSession,
  state: { status: 'anonymous' },
  restoring: false,
})

export function AuthProvider({ children, session }: AuthProviderProps) {
  const [restoring, setRestoring] = useState(true)
  const state = useSyncExternalStore(
    (listener) => session.subscribe(listener),
    () => session.getState(),
    () => session.getState(),
  )

  useEffect(() => {
    let mounted = true

    void session
      .restore()
      .catch(() => undefined)
      .finally(() => {
        if (mounted) {
          setRestoring(false)
        }
      })

    return () => {
      mounted = false
    }
  }, [session])

  const value = useMemo(
    () => ({ session, state, restoring }),
    [restoring, session, state],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

const useAuth = (): AuthContextValue => {
  return useContext(AuthContext)
}

const toCurrentInternalPath = (
  location: Pick<Location, 'pathname' | 'search' | 'hash'>,
): string => `${location.pathname}${location.search}${location.hash}`

const isSafeInternalPath = (candidate: string): boolean => {
  if (
    !candidate.startsWith('/') ||
    candidate.startsWith('//') ||
    candidate.startsWith('/\\')
  ) {
    return false
  }

  try {
    return new URL(candidate, window.location.origin).origin === window.location.origin
  } catch {
    return false
  }
}

const rememberLoginReturnPath = (path: string): void => {
  if (!isSafeInternalPath(path)) {
    return
  }

  const pathname = new URL(path, window.location.origin).pathname
  if (!AUTH_FLOW_PATHS.has(pathname)) {
    sessionStorage.setItem(LOGIN_RETURN_PATH_KEY, path)
  }
}

const takeLoginReturnPath = (): string => {
  const candidate = sessionStorage.getItem(LOGIN_RETURN_PATH_KEY)
  sessionStorage.removeItem(LOGIN_RETURN_PATH_KEY)
  return candidate && isSafeInternalPath(candidate) ? candidate : '/'
}

type LoginButtonProps = {
  label?: string
}

export function LoginButton({ label = '카카오로 로그인' }: LoginButtonProps) {
  const location = useLocation()

  return (
    <a
      className={styles.primaryAction}
      href={OAUTH_LOGIN_PATH}
      onClick={() => rememberLoginReturnPath(toCurrentInternalPath(location))}
    >
      {label}
    </a>
  )
}

type ProtectedRouteProps = {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const location = useLocation()
  const { restoring, state } = useAuth()

  if (restoring) {
    return (
      <p className={styles.status} role="status">
        로그인 상태 확인 중…
      </p>
    )
  }

  if (state.status === 'authenticated') {
    return children
  }

  rememberLoginReturnPath(toCurrentInternalPath(location))
  return (
    <Navigate
      replace
      to={state.status === 'onboarding' ? '/consent' : '/login'}
    />
  )
}

export function AuthNavigation() {
  const { restoring, session, state } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutFailed, setLogoutFailed] = useState(false)

  if (restoring) {
    return (
      <span className={styles.headerStatus} role="status">
        로그인 확인 중…
      </span>
    )
  }

  if (state.status !== 'authenticated') {
    return <LoginButton />
  }

  const logout = async () => {
    setLoggingOut(true)
    setLogoutFailed(false)
    try {
      await session.logout()
    } catch {
      setLogoutFailed(true)
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className={styles.headerActions}>
      <Link className={styles.secondaryAction} to="/me/reviews">
        내 리뷰
      </Link>
      <button
        className={styles.textButton}
        disabled={loggingOut}
        onClick={() => void logout()}
        type="button"
      >
        {loggingOut ? '로그아웃 중…' : '로그아웃'}
      </button>
      {logoutFailed ? (
        <span className={styles.visuallyHidden} role="alert">
          로그아웃 요청을 완료하지 못했지만 이 기기의 로그인 정보는
          삭제했습니다.
        </span>
      ) : null}
    </div>
  )
}

type CallbackState = 'exchanging' | 'failed'

export function OAuthCallback() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const code = searchParams.get('code')
  const oauthError = searchParams.get('error')
  const invalidCallback = oauthError === 'oauth_failed' || !code?.trim()
  const [callbackState, setCallbackState] =
    useState<CallbackState>(invalidCallback ? 'failed' : 'exchanging')
  const started = useRef(false)

  useEffect(() => {
    if (started.current) {
      return
    }
    started.current = true

    if (invalidCallback || !code) {
      return
    }

    void session
      .exchange(code)
      .then((response) => {
        if (response.termsAgreed) {
          navigate(takeLoginReturnPath(), { replace: true })
        } else {
          navigate('/consent', { replace: true })
        }
      })
      .catch(() => setCallbackState('failed'))
  }, [code, invalidCallback, navigate, session])

  if (callbackState === 'failed') {
    return (
      <section className={styles.panel}>
        <p className={styles.eyebrow}>로그인</p>
        <h1>로그인을 완료하지 못했습니다</h1>
        <p>잠시 후 카카오 로그인을 다시 시작해 주세요.</p>
        <LoginButton label="다시 로그인" />
      </section>
    )
  }

  return (
    <section className={styles.panel}>
      <h1>로그인을 확인하고 있습니다</h1>
      <p className={styles.status} role="status">
        안전하게 로그인 정보를 교환하는 중입니다…
      </p>
    </section>
  )
}

const consentSchema = z.object({
  agreed: z.literal(true, { error: '필수 약관에 동의해 주세요.' }),
})

export function Consent() {
  const { restoring, session, state } = useAuth()
  const navigate = useNavigate()
  const [submitFailed, setSubmitFailed] = useState(false)
  const {
    handleSubmit,
    register,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<{ agreed: boolean }>({ defaultValues: { agreed: false } })

  useEffect(() => {
    if (!restoring && state.status === 'authenticated' && !isSubmitting) {
      navigate(takeLoginReturnPath(), { replace: true })
    }
  }, [isSubmitting, navigate, restoring, state.status])

  if (restoring) {
    return (
      <p className={styles.status} role="status">
        로그인 상태 확인 중…
      </p>
    )
  }

  if (state.status === 'authenticated') {
    return (
      <p className={styles.status} role="status">
        로그인 완료 후 이동 중…
      </p>
    )
  }

  if (state.status !== 'onboarding') {
    return (
      <section className={styles.panel}>
        <p className={styles.eyebrow}>필수 약관</p>
        <h1>로그인이 필요합니다</h1>
        <p>약관 동의를 계속하려면 카카오 로그인을 먼저 완료해 주세요.</p>
        <LoginButton />
      </section>
    )
  }

  const submit = async (values: { agreed: boolean }) => {
    const parsed = consentSchema.safeParse(values)
    if (!parsed.success) {
      setError('agreed', { message: parsed.error.issues[0]?.message })
      return
    }

    setSubmitFailed(false)
    try {
      await session.consent(CURRENT_TERMS_VERSION)
    } catch {
      setSubmitFailed(true)
    }
  }

  return (
    <section className={styles.panel}>
      <p className={styles.eyebrow}>필수 약관</p>
      <h1>서비스 이용 동의</h1>
      <p>필수 약관 버전 {CURRENT_TERMS_VERSION}</p>
      <p className={styles.notice}>
        카카오 로그인은 계정 식별 수단이며 라이더 신분이나 실제 방문을
        인증하지 않습니다.
      </p>
      <form className={styles.form} onSubmit={handleSubmit(submit)}>
        <label className={styles.checkboxLabel}>
          <input type="checkbox" {...register('agreed')} />
          필수 약관에 동의합니다
        </label>
        {errors.agreed ? (
          <p className={styles.error} role="alert">
            {errors.agreed.message}
          </p>
        ) : null}
        {submitFailed ? (
          <p className={styles.error} role="alert">
            동의를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : null}
        <button
          className={styles.primaryButton}
          disabled={isSubmitting}
          type="submit"
        >
          {isSubmitting ? '처리 중…' : '동의하고 계속'}
        </button>
      </form>
    </section>
  )
}

export function LoginPageContent() {
  return (
    <section className={styles.panel}>
      <p className={styles.eyebrow}>로그인</p>
      <h1>리뷰를 작성하려면 로그인해 주세요</h1>
      <p>카카오 계정으로 Rider Voice에 로그인합니다.</p>
      <LoginButton />
    </section>
  )
}
