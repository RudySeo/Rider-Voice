import type { components } from './generated'

type ProblemDetail = components['schemas']['ProblemDetail']

const CODE_MESSAGES: Readonly<Record<string, string>> = {
  INVALID_REFRESH_TOKEN: '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.',
  EXTERNAL_PROVIDER_UNAVAILABLE:
    '외부 검색 서비스를 일시적으로 사용할 수 없습니다.',
}

const STATUS_MESSAGES: Readonly<Record<number, string>> = {
  400: '입력 내용을 확인해 주세요.',
  401: '로그인이 필요하거나 세션이 만료되었습니다.',
  403: '이 작업을 수행할 권한이 없습니다.',
  404: '요청한 정보를 찾을 수 없습니다.',
  409: '현재 상태에서는 요청을 처리할 수 없습니다.',
  429: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
  503: '서비스를 일시적으로 사용할 수 없습니다.',
}

export const safeErrorMessage = (code: string, status: number): string =>
  CODE_MESSAGES[code] ??
  STATUS_MESSAGES[status] ??
  '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string) {
    super(safeErrorMessage(code, status))
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const isProblemDetail = (value: unknown): value is ProblemDetail =>
  typeof value === 'object' &&
  value !== null &&
  'code' in value &&
  typeof value.code === 'string'

export const apiErrorFromResponse = async (
  response: Response,
): Promise<ApiError> => {
  const problem = await response.json().catch(() => null)
  const code = isProblemDetail(problem) ? problem.code : 'HTTP_ERROR'
  return new ApiError(response.status, code)
}
