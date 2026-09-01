import { parsePendingIntent } from '@/shared/auth/pendingIntent';

describe('pending authentication intent', () => {
  it('accepts only allow-listed intent shapes', () => {
    expect(parsePendingIntent(JSON.stringify({ kind: 'activity' }))).toEqual({ kind: 'activity' });
    expect(parsePendingIntent(JSON.stringify({ kind: 'manualReview', query: '강남 분식' }))).toEqual({
      kind: 'manualReview',
      query: '강남 분식',
    });
    expect(parsePendingIntent(JSON.stringify({ kind: 'admin', token: 'secret' }))).toBeNull();
    expect(parsePendingIntent(JSON.stringify({ kind: 'existingReview', restaurantId: -1, place: '가게' }))).toBeNull();
    expect(parsePendingIntent('not-json')).toBeNull();
  });
});
