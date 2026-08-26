import { requestJson } from '@/shared/api/client';
import { getRestaurantReviews } from '@/shared/api/restaurants';

jest.mock('@/shared/api/client', () => ({ requestJson: jest.fn(), usesMockApi: false }));

describe('public review pagination', () => {
  it('preserves the response cursor and sends it for the next page', async () => {
    const response = { items: [], nextCursor: 'next-page' };
    jest.mocked(requestJson).mockResolvedValue(response);

    await expect(getRestaurantReviews(7, 'cursor value')).resolves.toEqual(response);
    expect(requestJson).toHaveBeenCalledWith('/api/v1/restaurants/7/reviews?cursor=cursor%20value');
  });
});
