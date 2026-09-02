import { useInfiniteQuery } from '@tanstack/react-query';
import { fireEvent, render } from '@testing-library/react-native';

import PublicReviewListScreen from '@/app/restaurant/[id]/reviews';
import type { PublicReview } from '@/shared/api/types';

jest.mock('expo-router', () => ({ useLocalSearchParams: () => ({ id: '7' }), router: { back: jest.fn() } }));
jest.mock('@tanstack/react-query', () => ({ useInfiniteQuery: jest.fn() }));

const review: PublicReview = {
  reviewId: 1,
  visitMonth: '2026-08',
  comment: '첫 번째 경험',
  createdAt: '2026-08-01T00:00:00Z',
  ratings: {
    pickupSpaceCleanliness: 'GOOD', packagingStability: 'GOOD', orderReadiness: 'GOOD',
    handoffAccuracy: 'GOOD', staffInteraction: 'GOOD', riderRespect: 'GOOD',
  },
  authorActivity: { activityMonths: 2, publicReviewCount: 1 },
};

describe('public review list route', () => {
  it('shows public reviews and requests the next page from the cursor list', async () => {
    const fetchNextPage = jest.fn();
    jest.mocked(useInfiniteQuery).mockReturnValue({
      data: { pages: [{ items: [review], nextCursor: 'next-page' }], pageParams: [undefined] },
      isPending: false,
      isError: false,
      hasNextPage: true,
      isFetchingNextPage: false,
      fetchNextPage,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useInfiniteQuery>);
    const view = await render(<PublicReviewListScreen />);

    expect(view.getByText('첫 번째 경험')).toBeTruthy();
    fireEvent.press(view.getByText('더 보기'));

    expect(fetchNextPage).toHaveBeenCalledTimes(1);
  }, 15_000);
});
