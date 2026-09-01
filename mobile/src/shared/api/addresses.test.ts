import { parseAddressSearchResponse } from '@/shared/api/addresses';

describe('address search response', () => {
  it('keeps the verified address and existing pickup location ID', () => {
    expect(parseAddressSearchResponse({
      query: '테헤란로 1',
      candidates: [{
        standardAddress: '서울 강남구 테헤란로 1',
        lotNumberAddress: null,
        latitude: 37.5,
        longitude: 127,
        existingPickupLocationId: 10,
      }],
    }).candidates[0]).toEqual({
      standardAddress: '서울 강남구 테헤란로 1',
      lotNumberAddress: null,
      latitude: 37.5,
      longitude: 127,
      existingPickupLocationId: 10,
    });
  });

  it('rejects malformed provider data', () => {
    expect(() => parseAddressSearchResponse({ query: '주소', candidates: [{}] })).toThrow(
      '주소 검색 응답 형식이 올바르지 않아요.',
    );
  });
});
