import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

import { ApiClient } from '@/shared/api/client'

import { MyReviews, ReviewEdit } from './ReviewManagement'

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

const currentReview = {
  reviewId: 101,
  restaurant: {
    restaurantId: 17,
    name: '활성 리뷰 음식점',
    address: '서울 강남구 테헤란로 1',
  },
  visitMonth: '2026-07',
  ratings: {
    pickupSpaceCleanliness: 'GOOD',
    packagingStability: 'VERY_GOOD',
    orderReadiness: 'NEEDS_IMPROVEMENT',
    handoffAccuracy: 'MAJOR_IMPROVEMENT',
    staffInteraction: 'NOT_OBSERVED',
    riderRespect: 'GOOD',
  },
  comment: '기존 공개 의견',
  commentModerationStatus: 'PUBLISHED',
  visibilityStatus: 'ACTIVE',
  createdAt: '2026-07-25T03:00:00Z',
  updatedAt: '2026-07-26T04:30:00Z',
} as const

const anotherReview = {
  ...currentReview,
  reviewId: 100,
  restaurant: {
    restaurantId: 18,
    name: '다른 리뷰 음식점',
    address: '서울 강남구 역삼로 2',
  },
  visitMonth: '2026-04',
  comment: null,
  commentModerationStatus: 'REJECTED',
  visibilityStatus: 'ACTIVE',
  createdAt: '2026-04-20T01:00:00Z',
  updatedAt: '2026-04-21T01:00:00Z',
} as const

const renderWithProviders = (
  children: ReactNode,
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  }),
  initialEntries: string[] = ['/'],
) => ({
  queryClient,
  ...render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
    </QueryClientProvider>,
  ),
})

const renderEdit = (
  fetchFn: typeof fetch,
  queryClient?: QueryClient,
) =>
  renderWithProviders(
    <Routes>
      <Route
        path="/reviews/:reviewId/edit"
        element={<ReviewEdit client={createClient(fetchFn)} />}
      />
      <Route path="/me/reviews" element={<h1>내 리뷰 목록 도착</h1>} />
    </Routes>,
    queryClient,
    ['/reviews/101/edit'],
  )

describe('my review list', () => {
  it('loads cursor pages and exposes actions for every active review', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (input) => {
      const url = String(input)
      if (url === '/api/v1/users/me/reviews?size=20') {
        return jsonResponse({ items: [currentReview, anotherReview], nextCursor: 'next' })
      }
      if (url === '/api/v1/users/me/reviews?cursor=next&size=20') {
        return jsonResponse({
          items: [{ ...anotherReview, reviewId: 99, restaurant: { ...anotherReview.restaurant, name: '두 번째 페이지 음식점' } }],
          nextCursor: null,
        })
      }
      throw new Error(`Unexpected request: ${url}`)
    })

    renderWithProviders(<MyReviews client={createClient(fetchFn)} />)

    expect(screen.getByRole('status')).toHaveTextContent('내 리뷰를 불러오는 중')
    const currentCard = await screen.findByRole('article', { name: '활성 리뷰 음식점 리뷰' })
    const anotherCard = screen.getByRole('article', { name: '다른 리뷰 음식점 리뷰' })

    expect(within(currentCard).getByText('2026년 7월 방문')).toBeInTheDocument()
    expect(within(currentCard).getByText('공개됨')).toBeInTheDocument()
    expect(within(currentCard).getByText('의견 공개')).toBeInTheDocument()
    expect(within(currentCard).getByText(/수정 2026\. 7\. 26\./)).toBeInTheDocument()
    expect(within(currentCard).getByText('관찰하지 못함')).toBeInTheDocument()
    expect(within(currentCard).getByRole('link', { name: '수정' })).toHaveAttribute(
      'href',
      '/reviews/101/edit',
    )
    expect(within(currentCard).getByRole('button', { name: '삭제' })).toBeInTheDocument()

    expect(within(anotherCard).getByText('공개됨')).toBeInTheDocument()
    expect(within(anotherCard).getByText('관리자에 의해 숨김')).toBeInTheDocument()
    expect(within(anotherCard).getByRole('link', { name: '수정' })).toBeInTheDocument()
    expect(within(anotherCard).getByRole('button', { name: '삭제' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '리뷰 더 보기' }))
    expect(await screen.findByText('두 번째 페이지 음식점')).toBeInTheDocument()
    expect(fetchFn).toHaveBeenLastCalledWith(
      '/api/v1/users/me/reviews?cursor=next&size=20',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('renders empty and safe error states without ProblemDetail secrets', async () => {
    const emptyFetch = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse({ items: [], nextCursor: null }),
    )
    const { unmount } = renderWithProviders(
      <MyReviews client={createClient(emptyFetch)} />,
    )
    expect(await screen.findByText('작성한 리뷰가 없습니다.')).toBeInTheDocument()
    unmount()

    const errorFetch = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          code: 'INVALID_ACCESS_TOKEN',
          detail: 'Bearer provider-token-secret',
        },
        401,
      ),
    )
    renderWithProviders(<MyReviews client={createClient(errorFetch)} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '로그인이 만료되었습니다. 다시 로그인해 주세요.',
    )
    expect(document.body).not.toHaveTextContent('provider-token-secret')
  })
})

describe('review edit', () => {
  it('reuses the rating and comment fields while keeping restaurant and visit month read-only', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (input, init) => {
      if (init?.method === 'PATCH') {
        return jsonResponse({
          ...currentReview,
          ratings: {
            ...currentReview.ratings,
            pickupSpaceCleanliness: 'VERY_GOOD',
          },
          comment: '수정한 의견',
          commentModerationStatus: 'PUBLISHED',
        })
      }
      return jsonResponse({ items: [currentReview], nextCursor: null })
    })
    renderEdit(fetchFn)

    expect(await screen.findByRole('heading', { name: '리뷰 수정' })).toBeInTheDocument()
    expect(screen.getByText('활성 리뷰 음식점')).toBeInTheDocument()
    expect(screen.getByText('2026년 7월')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '음식점' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '방문 연월' })).not.toBeInTheDocument()
    expect(
      screen.getByText('수정한 의견은 즉시 공개됩니다. 신고로 숨겨진 의견은 처리 전까지 계속 숨겨집니다.'),
    ).toBeInTheDocument()

    await userEvent.click(
      within(screen.getByRole('group', { name: '픽업 공간 청결' })).getByRole('radio', {
        name: '매우 좋음',
      }),
    )
    const comment = screen.getByRole('textbox', { name: '자유 의견 (선택)' })
    await userEvent.clear(comment)
    await userEvent.type(comment, '  수정한 의견  ')
    await userEvent.click(screen.getByRole('button', { name: '변경사항 저장' }))

    expect(await screen.findByRole('heading', { name: '내 리뷰 목록 도착' })).toBeInTheDocument()
    const patchCall = fetchFn.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(patchCall?.[0]).toBe('/api/v1/reviews/101')
    expect(JSON.parse(String(patchCall?.[1]?.body))).toEqual({
      pickupSpaceCleanliness: 'VERY_GOOD',
      packagingStability: 'VERY_GOOD',
      orderReadiness: 'NEEDS_IMPROVEMENT',
      handoffAccuracy: 'MAJOR_IMPROVEMENT',
      staffInteraction: 'NOT_OBSERVED',
      riderRespect: 'GOOD',
      comment: '수정한 의견',
    })
    expect(String(patchCall?.[1]?.body)).not.toContain('visitMonth')
    expect(String(patchCall?.[1]?.body)).not.toContain('restaurant')
  })

  it.each([
    [404, 'REVIEW_NOT_FOUND', '소유하지 않았거나 더 이상 활성 상태인 리뷰가 아닙니다.'],
    [409, 'REVIEW_CONFLICT', '리뷰 상태가 변경되어 수정할 수 없습니다. 내 리뷰 목록을 다시 확인해 주세요.'],
    [401, 'INVALID_ACCESS_TOKEN', '로그인이 만료되었습니다. 다시 로그인해 주세요.'],
    [500, 'PROVIDER_TOKEN_LEAK', '리뷰를 수정하지 못했습니다. 잠시 후 다시 시도해 주세요.'],
  ] as const)(
    'shows a safe %s update failure',
    async (status, code, expected) => {
      const fetchFn = vi.fn<typeof fetch>(async (_input, init) =>
        init?.method === 'PATCH'
          ? jsonResponse({ code, detail: 'provider-token-secret' }, status)
          : jsonResponse({ items: [currentReview], nextCursor: null }),
      )
      renderEdit(fetchFn)

      await screen.findByRole('heading', { name: '리뷰 수정' })
      await userEvent.click(screen.getByRole('button', { name: '변경사항 저장' }))

      expect(await screen.findByRole('alert')).toHaveTextContent(expected)
      expect(document.body).not.toHaveTextContent('provider-token-secret')
      expect(document.body).not.toHaveTextContent('PROVIDER_TOKEN_LEAK')
    },
  )
})

describe('review delete', () => {
  it('requires explicit confirmation, explains the 90-day state, and invalidates related caches', async () => {
    let deleted = false
    const fetchFn = vi.fn<typeof fetch>(async (_input, init) => {
      if (init?.method === 'DELETE') {
        deleted = true
        return jsonResponse({ reviewId: 101, deletedAt: '2026-07-29T03:00:00Z' })
      }
      return jsonResponse({ items: deleted ? [] : [currentReview], nextCursor: null })
    })
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    renderWithProviders(
      <MyReviews client={createClient(fetchFn)} />,
      queryClient,
    )

    await screen.findByRole('article', { name: '활성 리뷰 음식점 리뷰' })
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))

    const confirmation = screen.getByRole('alertdialog', { name: '리뷰 삭제 확인' })
    expect(within(confirmation).getByText(
      '삭제하면 작성 시각부터 90일이 지난 뒤 같은 음식점에 다시 작성할 수 있습니다.',
    )).toBeInTheDocument()
    expect(fetchFn).toHaveBeenCalledTimes(1)

    await userEvent.click(within(confirmation).getByRole('button', { name: '리뷰 삭제 확정' }))
    await waitFor(() => expect(fetchFn.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['my-reviews'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['public-restaurant-detail', 17] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['public-restaurant-reviews', 17] })
  })

  it('uses a general 404 message and never renders ProblemDetail provider data', async () => {
    const fetchFn = vi.fn<typeof fetch>(async (_input, init) =>
      init?.method === 'DELETE'
        ? jsonResponse({ code: 'REVIEW_NOT_FOUND', detail: 'provider-secret-token' }, 404)
        : jsonResponse({ items: [currentReview], nextCursor: null }),
    )
    renderWithProviders(<MyReviews client={createClient(fetchFn)} />)

    await screen.findByRole('article', { name: '활성 리뷰 음식점 리뷰' })
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '리뷰 삭제 확정' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '소유하지 않았거나 더 이상 활성 상태인 리뷰가 아닙니다.',
    )
    expect(document.body).not.toHaveTextContent('provider-secret-token')
  })
})
