import { requestJson } from '@/shared/api/client';

describe('JSON API client contract', () => {
  afterEach(() => jest.restoreAllMocks());

  it('does not require a response body for HTTP 204', async () => {
    jest.spyOn(global, 'fetch').mockResolvedValue({ ok: true, status: 204 } as Response);

    await expect(requestJson('/api/v1/reviews/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('calls the default local API instead of returning fixed search data', async () => {
    const payload = { items: [], externalSearchAvailable: true };
    jest.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => payload,
    } as Response);

    await expect(requestJson('/api/v1/restaurants/search?query=%EA%B0%95%EB%82%A8')).resolves.toEqual(payload);
    expect(fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/restaurants/search?query=%EA%B0%95%EB%82%A8',
      expect.objectContaining({ headers: expect.objectContaining({ Accept: 'application/json' }) }),
    );
  });
});
