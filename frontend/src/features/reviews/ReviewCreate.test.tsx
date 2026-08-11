import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  MemoryRouter,
  Route,
  Routes,
} from 'react-router-dom'

import { ApiClient } from '@/shared/api/client'

import { ReviewCreate } from './ReviewCreate'

const NOW = new Date('2026-07-29T03:00:00Z')

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const createClient = (fetchFn: typeof fetch) =>
  new ApiClient({
    fetchFn,
    getBearerToken: () => 'access-token',
  })

const renderWizard = (
  entry: string,
  fetchFn: typeof fetch,
) =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/reviews/new"
          element={
            <ReviewCreate client={createClient(fetchFn)} now={() => NOW} />
          }
        />
        <Route
          path="/restaurants/:restaurantId"
          element={<h1>음식점 상세 도착</h1>}
        />
      </Routes>
    </MemoryRouter>,
  )

const chooseRequiredReviewValues = async (
  comment = '  픽업 동선이 좋았습니다.  ',
) => {
  await userEvent.selectOptions(
    screen.getByRole('combobox', { name: '방문 연월' }),
    '2026-07',
  )

  for (const name of [
    '픽업 공간 청결',
    '포장 안정성',
    '주문 준비 상태',
    '주문 확인·전달 정확성',
    '직원 응대',
    '라이더 존중',
  ]) {
    await userEvent.click(
      within(screen.getByRole('group', { name })).getByRole('radio', {
        name: '좋음',
      }),
    )
  }

  if (comment) {
    await userEvent.type(
      screen.getByRole('textbox', { name: '자유 의견 (선택)' }),
      comment,
    )
  }
  await userEvent.click(screen.getByRole('button', { name: '최종 확인' }))
  await userEvent.click(screen.getByRole('button', { name: '리뷰 제출' }))
}

const createReviewResult = (restaurantId: number) => ({
  reviewId: 101,
  restaurant: {
    restaurantId,
    name: '선택 음식점',
    address: '서울 강남구 테헤란로 1',
  },
  visitMonth: '2026-07',
  ratings: {},
  comment: null,
  commentModerationStatus: 'PUBLISHED',
  visibilityStatus: 'ACTIVE',
  createdAt: '2026-07-29T03:00:00Z',
  updatedAt: '2026-07-29T03:00:00Z',
})

describe('review create target mapping', () => {
  it('maps an INTERNAL search candidate to EXISTING and moves to the response restaurant', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (input) => {
      const url = String(input)
      if (url.startsWith('/api/v1/restaurants/search')) {
        return jsonResponse({
          externalSearchStatus: 'AVAILABLE',
          candidates: [
            {
              candidateType: 'INTERNAL',
              restaurantId: 17,
              kakaoPlaceId: null,
              name: '등록된 음식점',
              address: '서울 강남구 테헤란로 1',
              aggregationStatus: 'COLLECTING',
              contributorCount: 2,
            },
          ],
        })
      }
      return jsonResponse(createReviewResult(17), 201)
    })
    renderWizard('/reviews/new', fetchFn)

    await userEvent.type(
      screen.getByRole('searchbox', { name: '음식점명 또는 주소' }),
      '  강남   분식  ',
    )
    await userEvent.click(screen.getByRole('button', { name: '음식점 검색' }))
    await userEvent.click(
      await screen.findByRole('button', { name: '등록된 음식점 선택' }),
    )
    await chooseRequiredReviewValues('  ' + '가'.repeat(200) + '  ')

    await screen.findByRole('heading', { name: '음식점 상세 도착' })
    const createCall = fetchFn.mock.calls.find(
      ([input]) => String(input) === '/api/v1/reviews',
    )
    expect(createCall?.[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          restaurantTarget: { type: 'EXISTING', restaurantId: 17 },
          visitMonth: '2026-07',
          pickupSpaceCleanliness: 'GOOD',
          packagingStability: 'GOOD',
          orderReadiness: 'GOOD',
          handoffAccuracy: 'GOOD',
          staffInteraction: 'GOOD',
          riderRespect: 'GOOD',
          comment: '가'.repeat(200),
        }),
      }),
    )
  })

  it('preserves a passed KAKAO query and place ID in the KAKAO payload', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(createReviewResult(27), 201))
    const parameters = new URLSearchParams({
      targetType: 'KAKAO',
      query: '강남 분식',
      kakaoPlaceId: 'kakao-place-27',
      name: '등록 전 음식점',
      address: '서울 강남구 역삼로 1',
    })
    renderWizard(`/reviews/new?${parameters}`, fetchFn)

    expect(
      screen.getByRole('heading', { name: '선택한 음식점을 확인해 주세요' }),
    ).toBeInTheDocument()
    expect(screen.getByText('등록 전 음식점')).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: '이 음식점으로 계속' }),
    )
    await chooseRequiredReviewValues()

    const [, init] = fetchFn.mock.calls[0]
    expect(JSON.parse(String(init?.body))).toEqual(
      expect.objectContaining({
        restaurantTarget: {
          type: 'KAKAO',
          query: '강남 분식',
          kakaoPlaceId: 'kakao-place-27',
        },
      }),
    )
  })

  it('uses MANUAL_EXISTING_LOCATION when the verified address candidate has an existing pickup ID', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (input) => {
      if (String(input).startsWith('/api/v1/addresses/search')) {
        return jsonResponse({
          query: '서울 강남구 테헤란로 1',
          candidates: [
            {
              standardAddress: '서울 강남구 테헤란로 1',
              lotNumberAddress: '서울 강남구 역삼동 1',
              latitude: 37.1,
              longitude: 127.1,
              existingPickupLocationId: 20,
            },
          ],
        })
      }
      return jsonResponse(createReviewResult(30), 201)
    })
    renderWizard('/reviews/new', fetchFn)

    await userEvent.click(
      screen.getByRole('button', { name: '카카오에 없는 브랜드 등록' }),
    )
    await userEvent.type(
      screen.getByRole('searchbox', { name: '픽업 장소 주소' }),
      '서울 강남구 테헤란로 1',
    )
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    await userEvent.click(
      await screen.findByRole('button', {
        name: '서울 강남구 테헤란로 1 선택',
      }),
    )
    await userEvent.type(
      screen.getByRole('textbox', { name: '배달 브랜드명' }),
      '  새 배달 브랜드  ',
    )
    await userEvent.click(
      screen.getByRole('checkbox', { name: '배달의민족' }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: '방문 정보 입력으로' }),
    )
    await chooseRequiredReviewValues('')

    const createCall = fetchFn.mock.calls.find(
      ([input]) => String(input) === '/api/v1/reviews',
    )
    expect(JSON.parse(String(createCall?.[1]?.body))).toEqual(
      expect.objectContaining({
        restaurantTarget: {
          type: 'MANUAL_EXISTING_LOCATION',
          pickupLocationId: 20,
          name: '새 배달 브랜드',
          platforms: ['BAEMIN'],
        },
        comment: null,
      }),
    )
  })

  it('uses the provider-selected standard address in MANUAL_ADDRESS without exposing an editable replacement', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (input) => {
      if (String(input).startsWith('/api/v1/addresses/search')) {
        return jsonResponse({
          query: '서울 강남구 테헤란로 1',
          candidates: [
            {
              standardAddress: '서울특별시 강남구 테헤란로 1',
              lotNumberAddress: null,
              latitude: 37.1,
              longitude: 127.1,
              existingPickupLocationId: null,
            },
          ],
        })
      }
      return jsonResponse(createReviewResult(31), 201)
    })
    renderWizard('/reviews/new', fetchFn)

    await userEvent.click(
      screen.getByRole('button', { name: '카카오에 없는 브랜드 등록' }),
    )
    await userEvent.type(
      screen.getByRole('searchbox', { name: '픽업 장소 주소' }),
      '서울 강남구 테헤란로 1',
    )
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    await userEvent.click(
      await screen.findByRole('button', {
        name: '서울특별시 강남구 테헤란로 1 선택',
      }),
    )

    expect(
      screen.queryByRole('textbox', { name: '선택한 표준 주소' }),
    ).not.toBeInTheDocument()
    await userEvent.type(
      screen.getByRole('textbox', { name: '배달 브랜드명' }),
      '새 주소 브랜드',
    )
    await userEvent.type(
      screen.getByRole('textbox', { name: '상세 픽업 위치 (선택)' }),
      '  지하 1층 픽업대  ',
    )
    await userEvent.click(
      screen.getByRole('checkbox', { name: '쿠팡이츠' }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: '방문 정보 입력으로' }),
    )
    await chooseRequiredReviewValues('')

    const createCall = fetchFn.mock.calls.find(
      ([input]) => String(input) === '/api/v1/reviews',
    )
    expect(JSON.parse(String(createCall?.[1]?.body))).toEqual(
      expect.objectContaining({
        restaurantTarget: {
          type: 'MANUAL_ADDRESS',
          addressQuery: '서울 강남구 테헤란로 1',
          selectedStandardAddress: '서울특별시 강남구 테헤란로 1',
          detailAddress: '지하 1층 픽업대',
          name: '새 주소 브랜드',
          platforms: ['COUPANG_EATS'],
        },
      }),
    )
  })
})

describe('review create validation and failures', () => {
  it('offers only the Seoul current and previous months and requires all six ratings', async () => {
    const fetchFn = vi.fn<typeof fetch>()
    renderWizard(
      '/reviews/new?targetType=EXISTING&restaurantId=17&name=등록된%20음식점',
      fetchFn,
    )

    await userEvent.click(
      screen.getByRole('button', { name: '이 음식점으로 계속' }),
    )
    expect(
      within(screen.getByRole('combobox', { name: '방문 연월' }))
        .getAllByRole('option')
        .map((option) => option.getAttribute('value')),
    ).toEqual(['', '2026-07', '2026-06'])
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: '방문 연월' }),
      '2026-07',
    )
    await userEvent.click(screen.getByRole('button', { name: '최종 확인' }))

    expect(screen.getAllByText('평가를 선택해 주세요.')).toHaveLength(6)
    expect(fetchFn).not.toHaveBeenCalled()
    expect(
      within(screen.getByRole('group', { name: '직원 응대' })).getByRole(
        'radio',
        { name: '관찰하지 못함' },
      ),
    ).toBeInTheDocument()
  })

  it('validates the trimmed 200-character comment before confirmation', async () => {
    renderWizard(
      '/reviews/new?targetType=EXISTING&restaurantId=17&name=등록된%20음식점',
      vi.fn<typeof fetch>(),
    )
    await userEvent.click(
      screen.getByRole('button', { name: '이 음식점으로 계속' }),
    )
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: '방문 연월' }),
      '2026-07',
    )
    for (const name of [
      '픽업 공간 청결',
      '포장 안정성',
      '주문 준비 상태',
      '주문 확인·전달 정확성',
      '직원 응대',
      '라이더 존중',
    ]) {
      await userEvent.click(
        within(screen.getByRole('group', { name })).getByRole('radio', {
          name: '관찰하지 못함',
        }),
      )
    }
    await userEvent.type(
      screen.getByRole('textbox', { name: '자유 의견 (선택)' }),
      `  ${'가'.repeat(201)}  `,
    )
    await userEvent.click(screen.getByRole('button', { name: '최종 확인' }))

    expect(
      screen.getByText('의견은 공백을 정리한 뒤 200자 이하여야 합니다.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('의견은 제출 직후 공개되며 신고 시 숨겨질 수 있습니다.'),
    ).toBeInTheDocument()
  })

  it('validates address, brand, platform, and detail address fields with Zod rules', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        query: '서울 강남구 테헤란로 1',
        candidates: [
          {
            standardAddress: '서울 강남구 테헤란로 1',
            lotNumberAddress: null,
            latitude: 37.1,
            longitude: 127.1,
            existingPickupLocationId: null,
          },
        ],
      }),
    )
    renderWizard('/reviews/new', fetchFn)

    await userEvent.click(
      screen.getByRole('button', { name: '카카오에 없는 브랜드 등록' }),
    )
    await userEvent.type(
      screen.getByRole('searchbox', { name: '픽업 장소 주소' }),
      '가',
    )
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    expect(
      screen.getByText('주소 검색어는 공백을 정리한 뒤 2~100자여야 합니다.'),
    ).toBeInTheDocument()
    expect(fetchFn).not.toHaveBeenCalled()

    await userEvent.clear(
      screen.getByRole('searchbox', { name: '픽업 장소 주소' }),
    )
    await userEvent.type(
      screen.getByRole('searchbox', { name: '픽업 장소 주소' }),
      '서울 강남구 테헤란로 1',
    )
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    await userEvent.click(
      await screen.findByRole('button', {
        name: '서울 강남구 테헤란로 1 선택',
      }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: '방문 정보 입력으로' }),
    )
    expect(screen.getByText('브랜드명을 입력해 주세요.')).toBeInTheDocument()
    expect(
      screen.getByText('플랫폼을 하나 이상 선택해 주세요.'),
    ).toBeInTheDocument()

    await userEvent.type(
      screen.getByRole('textbox', { name: '배달 브랜드명' }),
      '가'.repeat(256),
    )
    await userEvent.type(
      screen.getByRole('textbox', { name: '상세 픽업 위치 (선택)' }),
      '나'.repeat(256),
    )
    await userEvent.click(
      screen.getByRole('checkbox', { name: '기타 플랫폼' }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: '방문 정보 입력으로' }),
    )
    expect(
      screen.getByText('브랜드명은 255자 이하여야 합니다.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('상세 픽업 위치는 255자 이하여야 합니다.'),
    ).toBeInTheDocument()
  })

  it.each([
    [
      409,
      'REVIEW_COOLDOWN',
      '같은 음식점에 활성 리뷰가 있으면 새로 작성할 수 없으며, 삭제·제외된 경우 최초 작성 시각부터 90일 뒤 다시 작성할 수 있습니다.',
    ],
    [
      503,
      'EXTERNAL_PROVIDER_UNAVAILABLE',
      '음식점 또는 주소를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.',
    ],
    [400, 'VALIDATION_FAILED', '입력 내용을 확인해 주세요.'],
    [401, 'INVALID_ACCESS_TOKEN', '로그인이 만료되었습니다. 다시 로그인해 주세요.'],
    [
      500,
      'INTERNAL_ERROR',
      '리뷰를 제출하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    ],
  ])(
    'shows a safe message for submit status %s',
    async (status, code, expectedMessage) => {
      const fetchFn = vi
        .fn<typeof fetch>()
        .mockResolvedValue(
          jsonResponse({ code, detail: 'provider-secret-token' }, status),
        )
      renderWizard(
        '/reviews/new?targetType=EXISTING&restaurantId=17&name=등록된%20음식점',
        fetchFn,
      )

      await userEvent.click(
        screen.getByRole('button', { name: '이 음식점으로 계속' }),
      )
      await chooseRequiredReviewValues('')

      expect(await screen.findByRole('alert')).toHaveTextContent(
        expectedMessage,
      )
      expect(document.body).not.toHaveTextContent('provider-secret-token')
    },
  )
})
