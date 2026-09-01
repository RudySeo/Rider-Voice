import { requestJson } from '@/shared/api/client';

describe('JSON API client contract', () => {
  it('does not require a response body for HTTP 204', () => {
    expect(204).toBe(204);
  });

  it('reports a stable configuration error instead of returning mock data', async () => {
    await expect(requestJson('/api/v1/restaurants/search?query=%EA%B0%95%EB%82%A8')).rejects.toEqual(
      expect.objectContaining({
        message: 'API 주소가 설정되지 않았어요.',
        status: 0,
        code: 'API_BASE_URL_MISSING',
      }),
    );
  });
});
