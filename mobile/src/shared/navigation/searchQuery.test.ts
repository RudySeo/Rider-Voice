import { routeSearchQuery } from '@/shared/navigation/searchQuery';

describe('search route query', () => {
  it('normalizes a single route value and ignores array values', () => {
    expect(routeSearchQuery('  강남 분식  ')).toBe('강남 분식');
    expect(routeSearchQuery(['강남', '분식'])).toBe('');
    expect(routeSearchQuery(undefined)).toBe('');
  });
});
