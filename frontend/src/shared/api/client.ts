import type { paths } from './generated'
import { ApiError, apiErrorFromResponse } from './errors'

type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete'
type ApiPath = keyof paths
type Operation<Path extends ApiPath, Method extends HttpMethod> =
  Method extends keyof paths[Path] ? NonNullable<paths[Path][Method]> : never
type QueryParameters<Value> = Value extends {
  parameters: { query?: infer Query }
}
  ? Query
  : never
type PathParameters<Value> = Value extends {
  parameters: { path?: infer Parameters }
}
  ? Parameters
  : never
type JsonRequestBody<Value> = Value extends {
  requestBody: { content: { 'application/json': infer Body } }
}
  ? Body
  : never
type SuccessStatus = 200 | 201 | 202 | 204
type JsonResponse<Value> = Value extends {
  content: { 'application/json': infer Body }
}
  ? Body
  : undefined
type SuccessResponse<Value> = Value extends {
  responses: infer Responses
}
  ? {
      [Status in keyof Responses]: Status extends SuccessStatus
        ? JsonResponse<Responses[Status]>
        : never
    }[keyof Responses]
  : never

export type AuthMode = 'none' | 'access'

export type ApiRequestOptions<
  Path extends ApiPath,
  Method extends HttpMethod,
> = {
  method: Method
  query?: QueryParameters<Operation<Path, Method>>
  path?: PathParameters<Operation<Path, Method>>
  body?: JsonRequestBody<Operation<Path, Method>>
  auth?: AuthMode
  headers?: HeadersInit
}

type RuntimeRequestOptions = {
  method: HttpMethod
  query?: unknown
  path?: unknown
  body?: unknown
  auth?: AuthMode
  headers?: HeadersInit
}

type ApiClientOptions = {
  baseUrl?: string
  fetchFn?: typeof fetch
  getBearerToken?: (auth: Exclude<AuthMode, 'none'>) => string | null
  refreshSession?: () => Promise<void>
}

const NO_REFRESH_PATHS = new Set<ApiPath>([
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
])

const appendQuery = (url: URL, query: unknown): void => {
  if (typeof query !== 'object' || query === null) {
    return
  }

  for (const [key, rawValue] of Object.entries(query)) {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue]
    for (const value of values) {
      if (value !== undefined && value !== null) {
        url.searchParams.append(key, String(value))
      }
    }
  }
}

const interpolatePath = (path: string, parameters: unknown): string => {
  if (typeof parameters !== 'object' || parameters === null) {
    return path
  }

  return Object.entries(parameters).reduce(
    (result, [key, value]) =>
      result.replace(`{${key}}`, encodeURIComponent(String(value))),
    path,
  )
}

const hasJsonContent = (response: Response): boolean =>
  response.headers.get('Content-Type')?.includes('json') ?? false

export class ApiClient {
  private readonly baseUrl: string
  private readonly fetchFn: typeof fetch
  private readonly getBearerToken?: ApiClientOptions['getBearerToken']
  private readonly refreshSession?: ApiClientOptions['refreshSession']
  private refreshPromise: Promise<void> | null = null

  constructor(options: ApiClientOptions = {}) {
    this.baseUrl = options.baseUrl ?? window.location.origin
    this.fetchFn =
      options.fetchFn ?? ((input, init) => window.fetch(input, init))
    this.getBearerToken = options.getBearerToken
    this.refreshSession = options.refreshSession
  }

  async request<Path extends ApiPath, Method extends HttpMethod>(
    path: Path,
    options: ApiRequestOptions<Path, Method>,
  ): Promise<SuccessResponse<Operation<Path, Method>>> {
    return this.execute(
      path,
      options as RuntimeRequestOptions,
      false,
    ) as Promise<SuccessResponse<Operation<Path, Method>>>
  }

  private async execute(
    path: ApiPath,
    options: RuntimeRequestOptions,
    retried: boolean,
  ): Promise<unknown> {
    const response = await this.send(path, options)
    const canRefresh =
      response.status === 401 &&
      !retried &&
      options.auth === 'access' &&
      !NO_REFRESH_PATHS.has(path) &&
      this.refreshSession !== undefined

    if (canRefresh) {
      await this.refreshOnce()
      return this.execute(path, options, true)
    }

    if (!response.ok) {
      throw await apiErrorFromResponse(response)
    }
    if (response.status === 204) {
      return undefined
    }
    if (!hasJsonContent(response)) {
      throw new ApiError(response.status, 'INVALID_API_RESPONSE')
    }
    return response.json()
  }

  private async send(
    path: ApiPath,
    options: RuntimeRequestOptions,
  ): Promise<Response> {
    const resolvedPath = interpolatePath(path, options.path)
    const url = new URL(resolvedPath, this.baseUrl)
    appendQuery(url, options.query)
    const headers = new Headers(options.headers)
    headers.set('Accept', 'application/json, application/problem+json')

    if (options.body !== undefined) {
      headers.set('Content-Type', 'application/json')
    }

    const auth = options.auth ?? 'none'
    if (auth !== 'none') {
      const token = this.getBearerToken?.(auth)
      if (token) {
        headers.set('Authorization', `Bearer ${token}`)
      }
    }

    return this.fetchFn(
      this.baseUrl === window.location.origin
        ? `${url.pathname}${url.search}`
        : url.toString(),
      {
        method: options.method.toUpperCase(),
        headers,
        credentials: 'include',
        body:
          options.body === undefined ? undefined : JSON.stringify(options.body),
      },
    )
  }

  private async refreshOnce(): Promise<void> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.refreshSession!().finally(() => {
        this.refreshPromise = null
      })
    }
    return this.refreshPromise
  }
}
