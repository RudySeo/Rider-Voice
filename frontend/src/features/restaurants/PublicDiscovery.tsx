import {
  useInfiniteQuery,
  useQuery,
} from '@tanstack/react-query'
import {
  FormEvent,
  useId,
  useState,
} from 'react'
import { Link, useParams } from 'react-router-dom'

import { apiSession } from '@/features/auth/AuthFlow'
import { ApiClient } from '@/shared/api/client'
import type { components } from '@/shared/api/generated'

import styles from './PublicDiscovery.module.css'

type RestaurantSearchResponse =
  components['schemas']['RestaurantSearchResponse']
type RestaurantSearchCandidate =
  components['schemas']['RestaurantSearchCandidateResponse']
type RestaurantDetailResponse =
  components['schemas']['RestaurantDetailResponse']
type PublicReviewListResponse =
  components['schemas']['PublicReviewListResponse']
type PublicReview =
  components['schemas']['PublicReviewListItemResponse']
type BrandReport = components['schemas']['RestaurantBrandReportResponse']
type PickupLocationReport =
  components['schemas']['RestaurantPickupLocationReportResponse']
type AggregateMetric =
  components['schemas']['RestaurantAggregateMetricResponse']

type ClientProps = {
  client?: ApiClient
}

type AggregationKind = 'brand' | 'pickupLocation'

type AggregationReportProps = {
  kind: AggregationKind
  report?: BrandReport | PickupLocationReport
  title: string
}

const SEARCH_LIMIT = 20
const PUBLIC_REVIEW_PAGE_SIZE = 20

const AGGREGATION_STATUS_LABELS = {
  NO_REVIEWS: '아직 집계할 리뷰가 없습니다.',
  COLLECTING: '공개 기준 수집 중',
  PUBLISHED: '항목별 분포 공개',
} as const

const SEARCH_AGGREGATION_LABELS = {
  NO_REVIEWS: '리뷰 없음',
  COLLECTING: '리포트 수집 중',
  PUBLISHED: '리포트 공개',
} as const

const REPORT_METRICS = {
  brand: [
    ['packagingStability', '포장 안정성'],
    ['orderReadiness', '주문 준비 상태'],
    ['handoffAccuracy', '주문 확인·전달 정확성'],
  ],
  pickupLocation: [
    ['pickupSpaceCleanliness', '픽업 공간 청결'],
    ['staffInteraction', '직원 응대'],
    ['riderRespect', '라이더 존중'],
  ],
} as const

const RATING_LABELS = {
  VERY_GOOD: '매우 좋음',
  GOOD: '좋음',
  NEEDS_IMPROVEMENT: '개선 필요',
  MAJOR_IMPROVEMENT: '큰 개선 필요',
  NOT_OBSERVED: '관찰하지 못함',
} as const

const REVIEW_RATING_FIELDS = [
  ['pickupSpaceCleanliness', '픽업 공간 청결'],
  ['packagingStability', '포장 안정성'],
  ['orderReadiness', '주문 준비 상태'],
  ['handoffAccuracy', '주문 확인·전달 정확성'],
  ['staffInteraction', '직원 응대'],
  ['riderRespect', '라이더 존중'],
] as const

const DISTRIBUTION_RATINGS = [
  'VERY_GOOD',
  'GOOD',
  'NEEDS_IMPROVEMENT',
  'MAJOR_IMPROVEMENT',
] as const

const normalizeSearchQuery = (value: string): string =>
  value
    .normalize('NFKC')
    .replace(/[\s\p{Z}]+/gu, ' ')
    .trim()

const isValidSearchQuery = (query: string): boolean =>
  query.length >= 2 && query.length <= 100

const toKakaoReviewPath = (
  query: string,
  candidate: RestaurantSearchCandidate,
): string => {
  const parameters = new URLSearchParams({
    targetType: 'KAKAO',
    query,
    kakaoPlaceId: candidate.kakaoPlaceId ?? '',
    name: candidate.name ?? '',
    address: candidate.address ?? '',
  })
  return `/reviews/new?${parameters.toString()}`
}

const toExistingReviewPath = ({
  address,
  name,
  restaurantId,
}: {
  address?: string
  name?: string
  restaurantId: number
}): string => {
  const parameters = new URLSearchParams({
    targetType: 'EXISTING',
    restaurantId: String(restaurantId),
    name: name ?? '',
    address: address ?? '',
  })
  return `/reviews/new?${parameters.toString()}`
}

const percentage = (value: number): string =>
  new Intl.NumberFormat('ko-KR', {
    maximumFractionDigits: 1,
  }).format(value)

function VerificationNotice({
  notice,
  status,
}: {
  notice?: string
  status?: 'UNVERIFIED'
}) {
  if (status !== 'UNVERIFIED' || !notice) {
    return null
  }

  return (
    <aside className={styles.verificationNotice}>
      <strong>미인증 정보</strong>
      <span className={styles.verificationCode}>UNVERIFIED</span>
      <p>{notice}</p>
    </aside>
  )
}

function SearchCandidate({
  candidate,
  query,
}: {
  candidate: RestaurantSearchCandidate
  query: string
}) {
  const status = candidate.aggregationStatus
  const contributorCount = candidate.contributorCount ?? 0

  return (
    <li className={styles.searchCard}>
      <div className={styles.searchCardBody}>
        <h3>{candidate.name ?? '이름 없는 음식점'}</h3>
        <p>{candidate.address ?? '주소 정보 없음'}</p>
        <p className={styles.metadata}>
          {status ? SEARCH_AGGREGATION_LABELS[status] : '집계 상태 확인 불가'}
          {' · '}
          서로 다른 작성자 {contributorCount}명
        </p>
      </div>
      {candidate.candidateType === 'INTERNAL' &&
      candidate.restaurantId !== null &&
      candidate.restaurantId !== undefined ? (
        <div className={styles.searchCardActions}>
          <Link
            aria-label={`${candidate.name ?? '음식점'} 상세 보기`}
            className={styles.secondaryAction}
            to={`/restaurants/${candidate.restaurantId}`}
          >
            상세 보기
          </Link>
          <Link
            aria-label={`${candidate.name ?? '음식점'} 리뷰 작성 시작`}
            className={styles.primaryAction}
            to={toExistingReviewPath({
              address: candidate.address,
              name: candidate.name,
              restaurantId: candidate.restaurantId,
            })}
          >
            리뷰 작성
          </Link>
        </div>
      ) : candidate.candidateType === 'KAKAO' && candidate.kakaoPlaceId ? (
        <Link
          aria-label={`${candidate.name ?? '음식점'} 리뷰 작성 시작`}
          className={styles.primaryAction}
          to={toKakaoReviewPath(query, candidate)}
        >
          리뷰 작성 시작
        </Link>
      ) : null}
    </li>
  )
}

export function RestaurantSearch({
  client = apiSession.client,
}: ClientProps) {
  const [input, setInput] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [request, setRequest] = useState<{
    attempt: number
    query: string
  } | null>(null)

  const search = useQuery<RestaurantSearchResponse>({
    queryKey: ['public-restaurant-search', request?.query, request?.attempt],
    queryFn: () =>
      client.request('/api/v1/restaurants/search', {
        method: 'get',
        query: { query: request!.query },
        auth: 'none',
      }),
    enabled: request !== null,
    retry: false,
  })

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalized = normalizeSearchQuery(input)

    if (!isValidSearchQuery(normalized)) {
      setValidationError(
        '검색어는 공백을 정리한 뒤 2~100자여야 합니다.',
      )
      return
    }

    setInput(normalized)
    setValidationError(null)
    setRequest((current) => ({
      query: normalized,
      attempt: (current?.attempt ?? 0) + 1,
    }))
  }

  const candidates = search.data?.candidates?.slice(0, SEARCH_LIMIT) ?? []

  return (
    <section
      aria-labelledby="restaurant-search-title"
      className={styles.searchSection}
    >
      <div className={styles.sectionHeading}>
        <p className={styles.eyebrow}>공개 음식점 검색</p>
        <h2 id="restaurant-search-title">음식점 찾기</h2>
        <p>배달 브랜드명이나 픽업 장소 주소로 검색할 수 있습니다.</p>
      </div>
      <form className={styles.searchForm} onSubmit={submitSearch}>
        <label htmlFor="restaurant-search-query">음식점명 또는 주소</label>
        <div className={styles.searchControls}>
          <input
            aria-describedby={
              validationError ? 'restaurant-search-error' : undefined
            }
            id="restaurant-search-query"
            onChange={(event) => setInput(event.target.value)}
            type="search"
            value={input}
          />
          <button disabled={search.isFetching} type="submit">
            검색
          </button>
        </div>
        {validationError ? (
          <p
            className={styles.error}
            id="restaurant-search-error"
            role="alert"
          >
            {validationError}
          </p>
        ) : null}
      </form>

      {search.isFetching ? (
        <p className={styles.status} role="status">
          검색 중…
        </p>
      ) : null}
      {search.isError ? (
        <p className={styles.error} role="alert">
          검색 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
        </p>
      ) : null}
      {search.data?.externalSearchStatus === 'UNAVAILABLE' ? (
        <p className={styles.outageNotice} role="status">
          외부 음식점 검색은 현재 사용할 수 없습니다. 등록된 음식점
          결과만 표시합니다.
        </p>
      ) : null}
      {search.isSuccess && candidates.length === 0 ? (
        <p className={styles.emptyState}>검색 결과가 없습니다.</p>
      ) : null}
      {candidates.length > 0 ? (
        <ul className={styles.searchResults}>
          {candidates.map((candidate, index) => (
            <SearchCandidate
              candidate={candidate}
              key={`${candidate.candidateType}-${candidate.restaurantId ?? candidate.kakaoPlaceId ?? index}`}
              query={request?.query ?? ''}
            />
          ))}
        </ul>
      ) : null}
    </section>
  )
}

function MetricDistribution({
  label,
  metric,
}: {
  label: string
  metric: AggregateMetric
}) {
  return (
    <section className={styles.metric}>
      <h3>{label}</h3>
      <p className={styles.metricCounts}>
        관찰 {metric.observedCount ?? 0}명
        {' · '}
        관찰하지 못함 {metric.notObservedCount ?? 0}명
      </p>
      <ul className={styles.distribution}>
        {DISTRIBUTION_RATINGS.map((rating) => (
          <li key={rating}>
            <span>{RATING_LABELS[rating]}</span>
            <strong>{percentage(metric.distribution?.[rating] ?? 0)}%</strong>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function AggregationReport({
  kind,
  report,
  title,
}: AggregationReportProps) {
  const headingId = useId()
  const status = report?.status ?? 'NO_REVIEWS'
  const metrics = report?.metrics as
    | Record<string, AggregateMetric | undefined>
    | null
    | undefined
  const availableMetrics = REPORT_METRICS[kind].flatMap(([key, label]) => {
    const metric = metrics?.[key]
    return metric ? [{ key, label, metric }] : []
  })

  return (
    <section
      aria-labelledby={headingId}
      className={styles.report}
      role="region"
    >
      <div className={styles.reportHeading}>
        <h2 id={headingId}>{title}</h2>
        <p>{AGGREGATION_STATUS_LABELS[status]}</p>
      </div>
      <p className={styles.contributors}>
        서로 다른 작성자 {report?.contributorCount ?? 0}명
      </p>
      {status === 'PUBLISHED' ? (
        availableMetrics.length > 0 ? (
          <div className={styles.metrics}>
            {availableMetrics.map(({ key, label, metric }) => (
              <MetricDistribution key={key} label={label} metric={metric} />
            ))}
          </div>
        ) : (
          <p className={styles.metadata}>공개된 항목 분포가 없습니다.</p>
        )
      ) : null}
    </section>
  )
}

function PublicReviewCard({ review }: { review: PublicReview }) {
  const ratings = review.ratings as
    | Record<string, keyof typeof RATING_LABELS | undefined>
    | undefined

  return (
    <article className={styles.reviewCard}>
      <header className={styles.reviewHeader}>
        <h3>{review.visitMonth ?? '방문 연월 미상'} 방문</h3>
        <span>공개 리뷰</span>
      </header>
      <dl className={styles.ratings}>
        {REVIEW_RATING_FIELDS.map(([key, label]) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>{ratings?.[key] ? RATING_LABELS[ratings[key]] : '정보 없음'}</dd>
          </div>
        ))}
      </dl>
      {review.comment ? (
        <blockquote className={styles.comment}>{review.comment}</blockquote>
      ) : null}
      <p className={styles.metadata}>
        작성 활동 {review.authorActivity?.activityMonths ?? 0}개월
        {' · '}
        공개 리뷰 {review.authorActivity?.publicReviewCount ?? 0}개
      </p>
      {review.createdAt ? (
        <time className={styles.metadata} dateTime={review.createdAt}>
          {new Intl.DateTimeFormat('ko-KR', {
            dateStyle: 'medium',
            timeZone: 'UTC',
          }).format(new Date(review.createdAt))}
        </time>
      ) : null}
      <VerificationNotice
        notice={review.verificationNotice}
        status={review.verificationStatus}
      />
    </article>
  )
}

function PublicReviewList({
  client,
  restaurantId,
}: {
  client: ApiClient
  restaurantId: number
}) {
  const reviews = useInfiniteQuery<PublicReviewListResponse>({
    queryKey: ['public-restaurant-reviews', restaurantId],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      client.request(
        '/api/v1/restaurants/{restaurantId}/reviews',
        {
          method: 'get',
          path: { restaurantId },
          query: {
            cursor:
              typeof pageParam === 'string' ? pageParam : undefined,
            size: PUBLIC_REVIEW_PAGE_SIZE,
          },
          auth: 'none',
        },
      ),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    retry: false,
  })
  const items = reviews.data?.pages.flatMap((page) => page.items ?? []) ?? []

  return (
    <section
      aria-labelledby="public-reviews-title"
      className={styles.reviewsSection}
    >
      <div className={styles.sectionHeading}>
        <h2 id="public-reviews-title">공개 리뷰</h2>
        <p>구조화 평가와 자유 의견은 즉시 공개되며 신고 시 의견이 숨겨질 수 있습니다.</p>
      </div>
      {reviews.isPending ? (
        <p className={styles.status} role="status">
          공개 리뷰를 불러오는 중…
        </p>
      ) : null}
      {reviews.isError ? (
        <p className={styles.error} role="alert">
          공개 리뷰를 불러오지 못했습니다.
        </p>
      ) : null}
      {reviews.isSuccess && items.length === 0 ? (
        <p className={styles.emptyState}>공개된 리뷰가 없습니다.</p>
      ) : null}
      {items.length > 0 ? (
        <div className={styles.reviewList}>
          {items.map((review, index) => (
            <PublicReviewCard
              key={review.reviewId ?? `review-${index}`}
              review={review}
            />
          ))}
        </div>
      ) : null}
      {reviews.hasNextPage ? (
        <button
          className={styles.loadMore}
          disabled={reviews.isFetchingNextPage}
          onClick={() => void reviews.fetchNextPage()}
          type="button"
        >
          {reviews.isFetchingNextPage ? '리뷰 불러오는 중…' : '리뷰 더 보기'}
        </button>
      ) : null}
    </section>
  )
}

export function RestaurantDetailPage({
  client = apiSession.client,
}: ClientProps) {
  const { restaurantId: restaurantIdParameter } = useParams()
  const restaurantId = Number(restaurantIdParameter)
  const validRestaurantId =
    Number.isSafeInteger(restaurantId) && restaurantId > 0

  const detail = useQuery<RestaurantDetailResponse>({
    queryKey: ['public-restaurant-detail', restaurantId],
    queryFn: () =>
      client.request('/api/v1/restaurants/{restaurantId}', {
        method: 'get',
        path: { restaurantId },
        auth: 'none',
      }),
    enabled: validRestaurantId,
    retry: false,
  })

  if (!validRestaurantId) {
    return (
      <p className={styles.error} role="alert">
        올바른 음식점 주소가 아닙니다.
      </p>
    )
  }

  if (detail.isPending) {
    return (
      <p className={styles.status} role="status">
        음식점 정보를 불러오는 중…
      </p>
    )
  }

  if (detail.isError) {
    return (
      <section className={styles.errorPanel}>
        <h1>음식점 정보를 불러오지 못했습니다</h1>
        <p>잠시 후 다시 시도하거나 검색으로 돌아가 주세요.</p>
        <Link className={styles.secondaryAction} to="/">
          음식점 검색으로 돌아가기
        </Link>
      </section>
    )
  }

  const restaurant = detail.data
  const address = [
    restaurant.pickupLocation?.standardAddress,
    restaurant.pickupLocation?.detailAddress,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <div className={styles.detailPage}>
      <section className={styles.detailHero}>
        <Link className={styles.backLink} to="/">
          ← 음식점 검색
        </Link>
        <div className={styles.detailTitle}>
          <div>
            <p className={styles.eyebrow}>배달 브랜드</p>
            <h1>{restaurant.name ?? '이름 없는 음식점'}</h1>
          </div>
          <span className={styles.restaurantStatus}>
            {restaurant.status === 'CLOSED'
              ? '폐업'
              : restaurant.status === 'ACTIVE'
                ? '영업 중'
                : '병합됨'}
          </span>
        </div>
        <section aria-labelledby="pickup-location-title">
          <h2 id="pickup-location-title">픽업 장소</h2>
          <address>{address || '주소 정보 없음'}</address>
        </section>
        <VerificationNotice
          notice={restaurant.verificationNotice}
          status={restaurant.verificationStatus}
        />
        {restaurant.status === 'ACTIVE' ? (
          <Link
            className={styles.primaryAction}
            to={toExistingReviewPath({
              address,
              name: restaurant.name,
              restaurantId,
            })}
          >
            이 음식점 리뷰 작성
          </Link>
        ) : null}
      </section>

      <div className={styles.reportGrid}>
        <AggregationReport
          kind="brand"
          report={restaurant.brandReport}
          title="브랜드 리포트"
        />
        <AggregationReport
          kind="pickupLocation"
          report={restaurant.pickupLocationReport}
          title="픽업 장소 리포트"
        />
      </div>

      <PublicReviewList client={client} restaurantId={restaurantId} />
    </div>
  )
}
