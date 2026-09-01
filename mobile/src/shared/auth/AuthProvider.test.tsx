import { parsePendingIntent } from '@/shared/auth/pendingIntent';

describe('pending authentication intent', () => {
  it('supports the four allow-listed intent kinds', () => {
    const intents = [
      { kind: 'activity' },
      { kind: 'existingReview', restaurantId: 1, place: '브랜드' },
      { kind: 'kakaoReview', query: '강남 분식', kakaoPlaceId: 'kakao-1', place: '브랜드' },
      { kind: 'manualReview', query: '강남 분식' },
    ];

    expect(intents.map((intent) => parsePendingIntent(JSON.stringify(intent))?.kind)).toEqual([
      'activity',
      'existingReview',
      'kakaoReview',
      'manualReview',
    ]);
  });
});
