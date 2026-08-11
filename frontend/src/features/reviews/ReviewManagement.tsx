import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'

import { apiSession } from '@/features/auth/AuthFlow'
import { ApiClient } from '@/shared/api/client'
import { ApiError } from '@/shared/api/errors'
import type { components } from '@/shared/api/generated'

import {
  RATING_OPTIONS,
  REVIEW_RATING_FIELDS,
  ReviewCommentField,
  ReviewRatingFields,
  reviewFieldsSchema,
  type ReviewRating,
  type ReviewRatingKey,
  type ReviewRatings,
} from './ReviewFields'
import styles from './ReviewManagement.module.css'

type MyReviewListResponse = components['schemas']['MyReviewListResponse']
type Review = components['schemas']['ReviewResponse']
type UpdateReviewRequest = components['schemas']['UpdateReviewRequest']

type ClientProps = {
  client?: ApiClient
}

const MY_REVIEW_PAGE_SIZE = 20
const MY_REVIEWS_QUERY_KEY = ['my-reviews'] as const

const COMMENT_STATUS_LABELS = {
  NONE: '의견 없음',
  PENDING: '이전 의견 상태',
  PUBLISHED: '의견 공개',
  REJECTED: '관리자에 의해 숨김',
  HIDDEN_REPORTED: '신고로 의견 숨김',
} as const

const VISIBILITY_STATUS_LABELS = {
  ACTIVE: '공개됨',
  EXCLUDED: '공개 제외',
} as const

const issueMessages = (error: z.ZodError): Record<string, string> =>
  Object.fromEntries(
    error.issues.map((issue) => [String(issue.path[0] ?? 'form'), issue.message]),
  )

const ratingLabel = (rating?: ReviewRating): string =>
  RATING_OPTIONS.find(({ value }) => value === rating)?.label ?? '정보 없음'

const visitMonthLabel = (visitMonth?: string): string => {
  const match = /^(\d{4})-(\d{2})$/.exec(visitMonth ?? '')
  return match ? `${match[1]}년 ${Number(match[2])}월` : '방문 연월 정보 없음'
}

const dateTimeLabel = (value?: string): string => {
  if (!value) return '정보 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '정보 없음'
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(date)
}

const actionErrorMessage = (
  error: unknown,
  action: '수정' | '삭제',
): string => {
  if (error instanceof ApiError) {
    if (error.status === 404) {
      return '소유하지 않았거나 더 이상 활성 상태인 리뷰가 아닙니다.'
    }
    if (error.status === 409) {
      return `리뷰 상태가 변경되어 ${action}할 수 없습니다. 내 리뷰 목록을 다시 확인해 주세요.`
    }
    if (error.status === 401) {
      return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
    }
    if (error.status === 400 || error.status === 422) {
      return '입력 내용을 확인해 주세요.'
    }
  }
  return `리뷰를 ${action}하지 못했습니다. 잠시 후 다시 시도해 주세요.`
}

const listErrorMessage = (error: unknown): string =>
  error instanceof ApiError && error.status === 401
    ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
    : '내 리뷰를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

const reviewIdOf = (review: Review): number | null =>
  typeof review.reviewId === 'number' && review.reviewId > 0
    ? review.reviewId
    : null

async function findReview(client: ApiClient, reviewId: number): Promise<Review | null> {
  let cursor: string | undefined
  const visitedCursors = new Set<string>()

  do {
    const page: MyReviewListResponse = await client.request(
      '/api/v1/users/me/reviews',
      {
        method: 'get',
        query: { cursor, size: MY_REVIEW_PAGE_SIZE },
        auth: 'access',
      },
    )
    const review = page.items?.find((item) => item.reviewId === reviewId)
    if (review) return review

    const nextCursor = page.nextCursor ?? undefined
    if (!nextCursor || visitedCursors.has(nextCursor)) return null
    visitedCursors.add(nextCursor)
    cursor = nextCursor
  } while (cursor)

  return null
}

function ReviewCard({
  deleting,
  onDelete,
  review,
}: {
  deleting: boolean
  onDelete: (review: Review) => void
  review: Review
}) {
  const reviewId = reviewIdOf(review)
  const restaurantName = review.restaurant?.name ?? '음식점 정보 없음'

  return (
    <article aria-label={`${restaurantName} 리뷰`} className={styles.card}>
      <header className={styles.cardHeader}>
        <div>
          <h2>{restaurantName}</h2>
          <p>{review.restaurant?.address ?? '주소 정보 없음'}</p>
        </div>
        <div className={styles.badges}>
          <span>
            {review.visibilityStatus
              ? VISIBILITY_STATUS_LABELS[review.visibilityStatus]
              : '공개 상태 정보 없음'}
          </span>
          <span>
            {review.commentModerationStatus
              ? COMMENT_STATUS_LABELS[review.commentModerationStatus]
              : '의견 상태 정보 없음'}
          </span>
        </div>
      </header>
      <p className={styles.visitMonth}>{visitMonthLabel(review.visitMonth)} 방문</p>
      <dl className={styles.ratings}>
        {REVIEW_RATING_FIELDS.map(([key, label]) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>{ratingLabel(review.ratings?.[key])}</dd>
          </div>
        ))}
      </dl>
      <section aria-label="자유 의견" className={styles.comment}>
        <h3>자유 의견</h3>
        <p>{review.comment || '작성한 의견이 없습니다.'}</p>
      </section>
      <p className={styles.timestamps}>
        생성 {dateTimeLabel(review.createdAt)} · 수정 {dateTimeLabel(review.updatedAt)}
      </p>
      {reviewId ? (
        <div className={styles.actions}>
          <Link className={styles.secondaryButton} to={`/reviews/${reviewId}/edit`}>
            수정
          </Link>
          <button
            className={styles.dangerButton}
            disabled={deleting}
            onClick={() => onDelete(review)}
            type="button"
          >
            삭제
          </button>
        </div>
      ) : null}
    </article>
  )
}

export function MyReviews({ client = apiSession.client }: ClientProps) {
  const queryClient = useQueryClient()
  const [pendingDelete, setPendingDelete] = useState<Review | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const reviews = useInfiniteQuery<MyReviewListResponse>({
    queryKey: MY_REVIEWS_QUERY_KEY,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      client.request('/api/v1/users/me/reviews', {
        method: 'get',
        query: {
          cursor: typeof pageParam === 'string' ? pageParam : undefined,
          size: MY_REVIEW_PAGE_SIZE,
        },
        auth: 'access',
      }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    retry: false,
  })
  const items = reviews.data?.pages.flatMap((page) => page.items ?? []) ?? []
  const deletion = useMutation({
    mutationFn: async (review: Review) => {
      const reviewId = reviewIdOf(review)
      if (!reviewId) throw new ApiError(404, 'REVIEW_NOT_FOUND')
      await client.request('/api/v1/reviews/{reviewId}', {
        method: 'delete',
        path: { reviewId },
        auth: 'access',
      })
      return review
    },
    onSuccess: async (review) => {
      setPendingDelete(null)
      const restaurantId = review.restaurant?.restaurantId
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: MY_REVIEWS_QUERY_KEY }),
      ]
      if (restaurantId) {
        invalidations.push(
          queryClient.invalidateQueries({
            queryKey: ['public-restaurant-detail', restaurantId],
          }),
          queryClient.invalidateQueries({
            queryKey: ['public-restaurant-reviews', restaurantId],
          }),
        )
      }
      await Promise.all(invalidations)
    },
    onError: (error) => setDeleteError(actionErrorMessage(error, '삭제')),
  })

  return (
    <section className={styles.page}>
      <div className={styles.heading}>
        <p className={styles.eyebrow}>내 리뷰</p>
        <h1>작성한 리뷰를 관리하세요</h1>
        <p>활성 리뷰를 수정하거나 삭제할 수 있으며 삭제된 리뷰는 목록에서 숨겨집니다.</p>
      </div>

      {reviews.isPending ? (
        <p className={styles.status} role="status">내 리뷰를 불러오는 중…</p>
      ) : null}
      {reviews.isError ? (
        <p className={styles.error} role="alert">{listErrorMessage(reviews.error)}</p>
      ) : null}
      {reviews.isSuccess && items.length === 0 ? (
        <p className={styles.empty}>작성한 리뷰가 없습니다.</p>
      ) : null}
      {deleteError ? <p className={styles.error} role="alert">{deleteError}</p> : null}

      {items.length > 0 ? (
        <div className={styles.list}>
          {items.map((review, index) => (
            <ReviewCard
              deleting={deletion.isPending}
              key={review.reviewId ?? `review-${index}`}
              onDelete={(selected) => {
                setDeleteError(null)
                setPendingDelete(selected)
              }}
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

      {pendingDelete ? (
        <div aria-label="리뷰 삭제 확인" className={styles.dialog} role="alertdialog">
          <h2>리뷰를 삭제할까요?</h2>
          <p>삭제한 리뷰 내용은 복구할 수 없습니다.</p>
          <p className={styles.warning}>
            삭제하면 작성 시각부터 90일이 지난 뒤 같은 음식점에 다시 작성할 수 있습니다.
          </p>
          <div className={styles.actions}>
            <button
              className={styles.secondaryButton}
              disabled={deletion.isPending}
              onClick={() => setPendingDelete(null)}
              type="button"
            >
              취소
            </button>
            <button
              className={styles.dangerButton}
              disabled={deletion.isPending}
              onClick={() => deletion.mutate(pendingDelete)}
              type="button"
            >
              {deletion.isPending ? '삭제 중…' : '리뷰 삭제 확정'}
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}

const toEditableRatings = (review: Review): ReviewRatings | null => {
  const parsed = reviewFieldsSchema.omit({ comment: true }).safeParse(review.ratings)
  return parsed.success ? parsed.data : null
}

function ReviewEditForm({ client, review }: { client: ApiClient; review: Review }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const reviewId = reviewIdOf(review)
  const editableRatings = toEditableRatings(review)
  const [ratings, setRatings] = useState<Partial<ReviewRatings>>(editableRatings ?? {})
  const [comment, setComment] = useState(review.comment ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const update = useMutation({
    mutationFn: (body: UpdateReviewRequest) => {
      if (!reviewId) throw new ApiError(404, 'REVIEW_NOT_FOUND')
      return client.request('/api/v1/reviews/{reviewId}', {
        method: 'patch',
        path: { reviewId },
        body,
        auth: 'access',
      })
    },
    onSuccess: async () => {
      const restaurantId = review.restaurant?.restaurantId
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: MY_REVIEWS_QUERY_KEY }),
        queryClient.invalidateQueries({ queryKey: ['my-review-edit', reviewId] }),
        ...(restaurantId
          ? [
              queryClient.invalidateQueries({ queryKey: ['public-restaurant-detail', restaurantId] }),
              queryClient.invalidateQueries({ queryKey: ['public-restaurant-reviews', restaurantId] }),
            ]
          : []),
      ])
      navigate('/me/reviews', { replace: true })
    },
    onError: (error) => setSubmitError(actionErrorMessage(error, '수정')),
  })

  const submit = () => {
    const parsed = reviewFieldsSchema.safeParse({ ...ratings, comment })
    if (!parsed.success) {
      setErrors(issueMessages(parsed.error))
      return
    }
    setErrors({})
    setSubmitError(null)
    update.mutate(parsed.data)
  }

  return (
    <section className={styles.page}>
      <div className={styles.heading}>
        <p className={styles.eyebrow}>내 리뷰</p>
        <h1>리뷰 수정</h1>
        <p>방문 연월과 음식점은 변경할 수 없습니다.</p>
      </div>
      <div className={styles.readOnlySummary}>
        <div>
          <span>음식점</span>
          <strong>{review.restaurant?.name ?? '음식점 정보 없음'}</strong>
          <small>{review.restaurant?.address ?? '주소 정보 없음'}</small>
        </div>
        <div>
          <span>방문 연월</span>
          <strong>{visitMonthLabel(review.visitMonth)}</strong>
        </div>
      </div>
      <ReviewRatingFields
        errors={errors}
        onChange={(key: ReviewRatingKey, value: ReviewRating) =>
          setRatings((current) => ({ ...current, [key]: value }))
        }
        ratings={ratings}
      />
      <ReviewCommentField
        comment={comment}
        error={errors.comment}
        notice="수정한 의견은 즉시 공개됩니다. 신고로 숨겨진 의견은 처리 전까지 계속 숨겨집니다."
        onChange={setComment}
      />
      {submitError ? <p className={styles.error} role="alert">{submitError}</p> : null}
      <div className={styles.actions}>
        <Link className={styles.secondaryButton} to="/me/reviews">취소</Link>
        <button
          className={styles.primaryButton}
          disabled={update.isPending || !editableRatings}
          onClick={submit}
          type="button"
        >
          {update.isPending ? '저장 중…' : '변경사항 저장'}
        </button>
      </div>
    </section>
  )
}

export function ReviewEdit({ client = apiSession.client }: ClientProps) {
  const { reviewId: reviewIdParameter } = useParams()
  const reviewId = Number(reviewIdParameter)
  const validReviewId = Number.isSafeInteger(reviewId) && reviewId > 0
  const review = useQuery({
    queryKey: ['my-review-edit', reviewId],
    queryFn: () => findReview(client, reviewId),
    enabled: validReviewId,
    retry: false,
  })

  if (!validReviewId) {
    return <p className={styles.error} role="alert">올바른 리뷰 주소가 아닙니다.</p>
  }
  if (review.isPending) {
    return <p className={styles.status} role="status">리뷰를 불러오는 중…</p>
  }
  if (review.isError) {
    return <p className={styles.error} role="alert">{listErrorMessage(review.error)}</p>
  }
  if (!review.data) {
    return (
      <section className={styles.errorPanel}>
        <h1>리뷰를 수정할 수 없습니다</h1>
        <p>소유하지 않았거나 더 이상 활성 상태인 리뷰가 아닙니다.</p>
        <Link className={styles.secondaryButton} to="/me/reviews">내 리뷰로 돌아가기</Link>
      </section>
    )
  }

  return <ReviewEditForm client={client} review={review.data} />
}
