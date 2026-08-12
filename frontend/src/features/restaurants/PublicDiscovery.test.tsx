import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import {
  MemoryRouter,
  Route,
  Routes,
} from 'react-router-dom'

import { ApiClient } from '@/shared/api/client'

import {
  AggregationReport,
  RestaurantDetailPage,
  RestaurantSearch,
} from './PublicDiscovery'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const renderWithProviders = (children: ReactNode) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>,
  )
}

const createClient = (fetchFn: typeof fetch) => new ApiClient({ fetchFn })

describe('public restaurant search', () => {
  it('normalizes the query, rejects 1 and 101 characters, and exposes loading and empty states', async () => {
    let finishSearch: ((response: Response) => void) | undefined
    const fetchFn = vi.fn<typeof fetch>(
      () =>
        new Promise<Response>((resolve) => {
          finishSearch = resolve
        }),
    )
    renderWithProviders(<RestaurantSearch client={createClient(fetchFn)} />)

    const query = screen.getByRole('searchbox', {
      name: '음식점명 또는 주소',
    })
    const submit = screen.getByRole('button', { name: '검색' })

    await userEvent.type(query, '　A　')
    await userEvent.click(submit)
    expect(
      screen.getByText('검색어는 공백을 정리한 뒤 2~100자여야 합니다.'),
    ).toBeInTheDocument()
    expect(fetchFn).not.toHaveBeenCalled()

    await userEvent.clear(query)
    await userEvent.type(query, '가'.repeat(101))
    await userEvent.click(submit)
    expect(fetchFn).not.toHaveBeenCalled()

    await userEvent.clear(query)
    await userEvent.type(query, '　강남   분식　')
    await userEvent.click(submit)

    expect(screen.getByRole('status')).toHaveTextContent('검색 중')
    expect(fetchFn).toHaveBeenCalledWith(
      '/api/v1/restaurants/search?query=%EA%B0%95%EB%82%A8+%EB%B6%84%EC%8B%9D',
      expect.objectContaining({ method: 'GET' }),
    )

    finishSearch?.(
      jsonResponse({
        externalSearchStatus: 'AVAILABLE',
        candidates: [],
      }),
    )

    expect(
      await screen.findByText('검색 결과가 없습니다.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '검색 결과에 없는 브랜드 등록' }),
    ).toHaveAttribute('href', '/reviews/new?mode=manual')
  })

  it('shows a safe request error', async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        jsonResponse({ code: 'HTTP_ERROR', detail: 'provider-secret' }, 500),
      )
    renderWithProviders(<RestaurantSearch client={createClient(fetchFn)} />)

    await userEvent.type(
      screen.getByRole('searchbox', { name: '음식점명 또는 주소' }),
      '강남 분식',
    )
    await userEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '검색 결과를 불러오지 못했습니다.',
    )
    expect(document.body).not.toHaveTextContent('provider-secret')
  })

  it('keeps internal results and explains that only external search is unavailable', async () => {
    const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({
        externalSearchStatus: 'UNAVAILABLE',
        candidates: [
          {
            candidateType: 'INTERNAL',
            restaurantId: 10,
            kakaoPlaceId: null,
            name: '등록된 음식점',
            address: '서울 강남구 테헤란로 1',
            aggregationStatus: 'COLLECTING',
            contributorCount: 4,
          },
        ],
      }),
    )
    renderWithProviders(<RestaurantSearch client={createClient(fetchFn)} />)

    await userEvent.type(
      screen.getByRole('searchbox', { name: '음식점명 또는 주소' }),
      '강남 분식',
    )
    await userEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(
      await screen.findByText(
        '외부 음식점 검색은 현재 사용할 수 없습니다. 등록된 음식점 결과만 표시합니다.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '등록된 음식점 상세 보기' }),
    ).toHaveAttribute('href', '/restaurants/10')
  })

  it('limits available candidates to twenty and links each candidate type to its next action', async () => {
    const candidates = Array.from({ length: 21 }, (_, index) => ({
      candidateType: index === 1 ? 'KAKAO' : 'INTERNAL',
      restaurantId: index === 1 ? null : index + 10,
      kakaoPlaceId: index === 1 ? 'kakao-place-1' : null,
      name: `후보 ${index + 1}`,
      address: `서울 주소 ${index + 1}`,
      aggregationStatus:
        index === 0 ? 'COLLECTING' : index === 1 ? 'NO_REVIEWS' : 'PUBLISHED',
      contributorCount: index === 0 ? 4 : index === 1 ? 0 : 5,
    }))
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        jsonResponse({ externalSearchStatus: 'AVAILABLE', candidates }),
      )
    renderWithProviders(<RestaurantSearch client={createClient(fetchFn)} />)

    await userEvent.type(
      screen.getByRole('searchbox', { name: '음식점명 또는 주소' }),
      '강남 분식',
    )
    await userEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findAllByRole('listitem')).toHaveLength(20)
    expect(
      screen.getByRole('link', { name: '후보 1 상세 보기' }),
    ).toHaveAttribute('href', '/restaurants/10')
    const internalReviewAction = screen.getByRole('link', {
      name: '후보 1 리뷰 작성 시작',
    })
    const internalActionUrl = new URL(
      internalReviewAction.getAttribute('href') ?? '',
      window.location.origin,
    )
    expect(internalActionUrl.pathname).toBe('/reviews/new')
    expect(internalActionUrl.searchParams.get('targetType')).toBe('EXISTING')
    expect(internalActionUrl.searchParams.get('restaurantId')).toBe('10')
    expect(internalActionUrl.searchParams.get('name')).toBe('후보 1')
    expect(internalActionUrl.searchParams.get('address')).toBe('서울 주소 1')

    const kakaoAction = screen.getByRole('link', {
      name: '후보 2 리뷰 작성 시작',
    })
    const actionUrl = new URL(
      kakaoAction.getAttribute('href') ?? '',
      window.location.origin,
    )
    expect(actionUrl.pathname).toBe('/reviews/new')
    expect(actionUrl.searchParams.get('targetType')).toBe('KAKAO')
    expect(actionUrl.searchParams.get('query')).toBe('강남 분식')
    expect(actionUrl.searchParams.get('kakaoPlaceId')).toBe('kakao-place-1')
    expect(actionUrl.searchParams.get('name')).toBe('후보 2')
    expect(actionUrl.searchParams.get('address')).toBe('서울 주소 2')
    expect(screen.queryByText('후보 21')).not.toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '검색 결과에 없는 브랜드 등록' }),
    ).toHaveAttribute('href', '/reviews/new?mode=manual')
  })
})

describe('public aggregation reports', () => {
  it.each([
    ['NO_REVIEWS', 0, '아직 집계할 리뷰가 없습니다.'],
    ['COLLECTING', 4, '공개 기준 수집 중'],
    ['PUBLISHED', 5, '항목별 분포 공개'],
  ] as const)(
    'shows %s as a contributor state without converting it to a score',
    (status, contributorCount, expectedText) => {
      renderWithProviders(
        <AggregationReport
          kind="brand"
          report={{
            status,
            contributorCount,
            metrics: status === 'PUBLISHED' ? {} : null,
          }}
          title="브랜드 리포트"
        />,
      )

      expect(screen.getByText(expectedText)).toBeInTheDocument()
      expect(
        screen.getByText(`서로 다른 작성자 ${contributorCount}명`),
      ).toBeInTheDocument()
      expect(document.body).not.toHaveTextContent(/평균|별점|순위|종합 점수/)
    },
  )
})

describe('public restaurant detail', () => {
  it('loads public reviews by cursor and renders only the public response fields', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (input) => {
      const url = String(input)
      if (url === '/api/v1/restaurants/17') {
        return jsonResponse({
          restaurantId: 17,
          name: '폐업한 브랜드',
          status: 'CLOSED',
          pickupLocation: {
            pickupLocationId: 7,
            standardAddress: '서울 강남구 테헤란로 1',
            detailAddress: '지하 1층 픽업대',
          },
          brandReport: {
            status: 'PUBLISHED',
            contributorCount: 5,
            metrics: {
              packagingStability: {
                observedCount: 4,
                notObservedCount: 1,
                distribution: {
                  VERY_GOOD: 50,
                  GOOD: 25,
                  NEEDS_IMPROVEMENT: 25,
                  MAJOR_IMPROVEMENT: 0,
                },
              },
            },
          },
          pickupLocationReport: {
            status: 'COLLECTING',
            contributorCount: 4,
            metrics: null,
          },
          verificationStatus: 'UNVERIFIED',
          verificationNotice:
            '라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.',
          otherRestaurants: [{ name: '공개하면 안 되는 같은 장소 브랜드' }],
        })
      }
      if (url === '/api/v1/restaurants/17/reviews?size=20') {
        return jsonResponse({
          items: [
            {
              reviewId: 101,
              visitMonth: '2026-07',
              ratings: {
                pickupSpaceCleanliness: 'GOOD',
                packagingStability: 'VERY_GOOD',
                orderReadiness: 'GOOD',
                handoffAccuracy: 'GOOD',
                staffInteraction: 'NOT_OBSERVED',
                riderRespect: 'GOOD',
              },
              comment: '즉시 공개된 의견',
              rawComment: '공개하면 안 되는 원문',
              authorActivity: {
                activityMonths: 3,
                publicReviewCount: 8,
              },
              createdAt: '2026-07-25T03:00:00Z',
              verificationStatus: 'UNVERIFIED',
              verificationNotice:
                '라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.',
            },
          ],
          nextCursor: 'next-page',
        })
      }
      if (
        url ===
        '/api/v1/restaurants/17/reviews?cursor=next-page&size=20'
      ) {
        return jsonResponse({
          items: [
            {
              reviewId: 100,
              visitMonth: '2026-04',
              ratings: {
                pickupSpaceCleanliness: 'NOT_OBSERVED',
                packagingStability: 'GOOD',
                orderReadiness: 'GOOD',
                handoffAccuracy: 'GOOD',
                staffInteraction: 'GOOD',
                riderRespect: 'GOOD',
              },
              comment: null,
              rawComment: '숨겨진 의견 원문',
              authorActivity: {
                activityMonths: 2,
                publicReviewCount: 4,
              },
              createdAt: '2026-04-25T03:00:00Z',
              verificationStatus: 'UNVERIFIED',
              verificationNotice:
                '라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.',
            },
          ],
          nextCursor: null,
        })
      }
      return jsonResponse({ code: 'NOT_FOUND' }, 404)
    })
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/restaurants/17']}>
          <Routes>
            <Route
              path="/restaurants/:restaurantId"
              element={<RestaurantDetailPage client={createClient(fetchFn)} />}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(
      await screen.findByRole('heading', { level: 1, name: '폐업한 브랜드' }),
    ).toBeInTheDocument()
    expect(screen.getByText('폐업')).toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: '이 음식점 리뷰 작성' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getByText('서울 강남구 테헤란로 1 · 지하 1층 픽업대'),
    ).toBeInTheDocument()

    const brandReport = screen.getByRole('region', {
      name: '브랜드 리포트',
    })
    expect(within(brandReport).getByText('항목별 분포 공개')).toBeInTheDocument()
    expect(
      within(brandReport).getByText('서로 다른 작성자 5명'),
    ).toBeInTheDocument()
    expect(
      within(brandReport).getByText(/관찰하지 못함 1명/),
    ).toBeInTheDocument()
    expect(within(brandReport).getByText('매우 좋음')).toBeInTheDocument()
    expect(within(brandReport).getByText('50%')).toBeInTheDocument()

    const pickupReport = screen.getByRole('region', {
      name: '픽업 장소 리포트',
    })
    expect(within(pickupReport).getByText('공개 기준 수집 중')).toBeInTheDocument()
    expect(
      within(pickupReport).getByText('서로 다른 작성자 4명'),
    ).toBeInTheDocument()

    expect(
      await screen.findByText('즉시 공개된 의견'),
    ).toBeInTheDocument()
    expect(screen.queryByText('공개하면 안 되는 원문')).not.toBeInTheDocument()
    expect(
      screen.queryByText('공개하면 안 되는 같은 장소 브랜드'),
    ).not.toBeInTheDocument()
    expect(screen.getAllByText('미인증 정보').length).toBeGreaterThanOrEqual(2)
    expect(
      screen.getAllByText(
        '라이더 신분과 실제 방문 여부가 인증되지 않은 정보입니다.',
      ).length,
    ).toBeGreaterThanOrEqual(2)
    expect(document.body).not.toHaveTextContent(/인증됨|인증 배지/)

    await userEvent.click(
      screen.getByRole('button', { name: '리뷰 더 보기' }),
    )

    await waitFor(() =>
      expect(fetchFn).toHaveBeenCalledWith(
        '/api/v1/restaurants/17/reviews?cursor=next-page&size=20',
        expect.objectContaining({ method: 'GET' }),
      ),
    )
    expect(await screen.findByText('2026-04 방문')).toBeInTheDocument()
    expect(screen.queryByText('숨겨진 의견 원문')).not.toBeInTheDocument()
    expect(screen.getAllByText('UNVERIFIED')).toHaveLength(3)
    expect(
      screen.queryByRole('button', { name: '리뷰 더 보기' }),
    ).not.toBeInTheDocument()
  })
})
