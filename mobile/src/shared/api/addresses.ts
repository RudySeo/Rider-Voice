import { requestJson } from '@/shared/api/client';
import type { components } from '@/shared/api/generated';

type AddressSearchWireResponse = components['schemas']['AddressSearchResponse'];
type AddressSearchWireCandidate = components['schemas']['AddressSearchCandidateResponse'];

export type AddressSearchCandidate = {
  standardAddress: string;
  lotNumberAddress: string | null;
  latitude: number;
  longitude: number;
  existingPickupLocationId: number | null;
};

export type AddressSearchResponse = {
  query: string;
  candidates: AddressSearchCandidate[];
};

function parseCandidate(candidate: AddressSearchWireCandidate): AddressSearchCandidate {
  if (
    typeof candidate.standardAddress !== 'string'
    || typeof candidate.latitude !== 'number'
    || typeof candidate.longitude !== 'number'
  ) {
    throw new Error('주소 검색 응답 형식이 올바르지 않아요.');
  }
  const existingPickupLocationId = candidate.existingPickupLocationId ?? null;
  if (existingPickupLocationId !== null && (!Number.isInteger(existingPickupLocationId) || existingPickupLocationId <= 0)) {
    throw new Error('주소 검색 응답 형식이 올바르지 않아요.');
  }
  return {
    standardAddress: candidate.standardAddress,
    lotNumberAddress: candidate.lotNumberAddress ?? null,
    latitude: candidate.latitude,
    longitude: candidate.longitude,
    existingPickupLocationId,
  };
}

export function parseAddressSearchResponse(response: AddressSearchWireResponse): AddressSearchResponse {
  if (typeof response.query !== 'string' || !Array.isArray(response.candidates)) {
    throw new Error('주소 검색 응답 형식이 올바르지 않아요.');
  }
  return {
    query: response.query,
    candidates: response.candidates.map(parseCandidate),
  };
}

export async function searchAddresses(query: string): Promise<AddressSearchResponse> {
  const response = await requestJson<AddressSearchWireResponse>(
    `/api/v1/addresses/search?query=${encodeURIComponent(query)}`,
  );
  return parseAddressSearchResponse(response);
}
