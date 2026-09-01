import { requestJson } from '@/shared/api/client';
import { experienceLabel, groupSearchResults, searchRestaurants } from '@/shared/api/restaurants';
import { mockSearchResponse } from '@/shared/api/mockData';

jest.mock('@/shared/api/client', () => ({
  requestJson: jest.fn(),
}));

const requestJsonMock = jest.mocked(requestJson);

describe('restaurant search presentation helpers', () => {
  beforeEach(() => requestJsonMock.mockReset());

  it('binds the submitted query to the backend search request', async () => {
    requestJsonMock.mockResolvedValue(mockSearchResponse);

    await expect(searchRestaurants('강남 분식 & 김밥')).resolves.toBe(mockSearchResponse);

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/restaurants/search?query=%EA%B0%95%EB%82%A8%20%EB%B6%84%EC%8B%9D%20%26%20%EA%B9%80%EB%B0%A5');
  });

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
