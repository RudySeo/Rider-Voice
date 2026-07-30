import { ApiError, safeErrorMessage } from './errors'

describe('safe API errors', () => {
  it('uses stable error code and status without exposing provider detail', () => {
    const message = safeErrorMessage('EXTERNAL_PROVIDER_UNAVAILABLE', 503)
    const error = new ApiError(503, 'EXTERNAL_PROVIDER_UNAVAILABLE')

    expect(message).toBe('외부 검색 서비스를 일시적으로 사용할 수 없습니다.')
    expect(error).toMatchObject({
      status: 503,
      code: 'EXTERNAL_PROVIDER_UNAVAILABLE',
      message,
    })
  })
})
