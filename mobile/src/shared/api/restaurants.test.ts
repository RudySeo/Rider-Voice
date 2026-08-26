import { experienceLabel, groupSearchResults } from '@/shared/api/restaurants';
import { mockSearchResponse } from '@/shared/api/mockData';

describe('restaurant search presentation helpers', () => {
  it('separates reviewed restaurants from Kakao-only places', () => {
    const groups = groupSearchResults(mockSearchResponse.candidates);

    expect(groups.reviewed).toHaveLength(2);
    expect(groups.kakao).toHaveLength(1);
    expect(groups.kakao[0].candidateType).toBe('KAKAO');
  });

  it('does not invent distance and exposes the review state instead', () => {
    expect(experienceLabel(mockSearchResponse.candidates[0])).toBe('8명의 경험');
    expect(experienceLabel(mockSearchResponse.candidates[2])).toBe('첫 리뷰 작성');
  });
});
