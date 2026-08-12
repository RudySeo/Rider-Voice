import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'

import { apiSession } from '@/features/auth/AuthFlow'
import { ApiClient } from '@/shared/api/client'
import { ApiError } from '@/shared/api/errors'
import type { components } from '@/shared/api/generated'

import styles from './ReviewCreate.module.css'
import {
  RATING_OPTIONS,
  REVIEW_RATING_FIELDS,
  ReviewCommentField,
  ReviewRatingFields,
  reviewFieldsSchema,
  type ReviewRating,
  type ReviewRatingKey,
} from './ReviewFields'

type RestaurantSearchResponse =
  components['schemas']['RestaurantSearchResponse']
type RestaurantSearchCandidate =
  components['schemas']['RestaurantSearchCandidateResponse']
type AddressSearchResponse = components['schemas']['AddressSearchResponse']
type AddressCandidate =
  components['schemas']['AddressSearchCandidateResponse']
type CreateReviewRequest = components['schemas']['CreateReviewRequest']
type RestaurantTarget =
  components['schemas']['RestaurantTargetRequest']
type DeliveryPlatform =
  components['schemas']['ManualAddressRestaurantTargetRequest']['platforms'][number]

type ReviewCreateProps = {
  client?: ApiClient
  now?: () => Date
}

type WizardStep = 'target' | 'manual' | 'review' | 'confirm'

type TargetSelection = {
  target: RestaurantTarget
  name: string
  address?: string
}

type SelectedAddress = {
  addressQuery: string
  candidate: AddressCandidate
}

type ReviewValues = Omit<CreateReviewRequest, 'restaurantTarget'>

const PLATFORM_OPTIONS = [
  ['BAEMIN', '배달의민족'],
  ['COUPANG_EATS', '쿠팡이츠'],
  ['YOGIYO', '요기요'],
  ['OTHER', '기타 플랫폼'],
] as const satisfies ReadonlyArray<readonly [DeliveryPlatform, string]>

const normalizeDisplayText = (value: string): string =>
  value
    .normalize('NFKC')
    .replace(/[\s\p{Z}]+/gu, ' ')
    .trim()

const querySchema = z
  .string()
  .transform(normalizeDisplayText)
  .refine((value) => value.length >= 2 && value.length <= 100, {
    message: '검색어는 공백을 정리한 뒤 2~100자여야 합니다.',
  })

const addressQuerySchema = z
  .string()
  .transform(normalizeDisplayText)
  .refine((value) => value.length >= 2 && value.length <= 100, {
    message: '주소 검색어는 공백을 정리한 뒤 2~100자여야 합니다.',
  })

const initialKakaoTargetSchema = z.object({
  query: z.string().refine(
    (value) => {
      const normalized = normalizeDisplayText(value)
      return normalized.length >= 2 && normalized.length <= 100
    },
    { message: 'invalid query' },
  ),
  kakaoPlaceId: z.string().trim().min(1).max(255),
})

const manualBrandSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, '브랜드명을 입력해 주세요.')
    .max(255, '브랜드명은 255자 이하여야 합니다.'),
  detailAddress: z
    .string()
    .trim()
    .max(255, '상세 픽업 위치는 255자 이하여야 합니다.'),
  platforms: z
    .array(z.enum(['BAEMIN', 'COUPANG_EATS', 'YOGIYO', 'OTHER']))
    .min(1, '플랫폼을 하나 이상 선택해 주세요.'),
})

const reviewSchema = (allowedVisitMonths: readonly string[]) =>
  reviewFieldsSchema.extend({
    visitMonth: z
      .string()
      .refine((value) => allowedVisitMonths.includes(value), {
        message: '방문 연월을 선택해 주세요.',
      }),
  })

const getSeoulMonthOptions = (
  now: Date,
): ReadonlyArray<{ value: string; label: string }> => {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
  }).formatToParts(now)
  const year = Number(parts.find((part) => part.type === 'year')?.value)
  const month = Number(parts.find((part) => part.type === 'month')?.value)

  return [0, 1].map((monthsAgo) => {
    const valueDate = new Date(Date.UTC(year, month - 1 - monthsAgo, 1))
    const valueYear = valueDate.getUTCFullYear()
    const valueMonth = String(valueDate.getUTCMonth() + 1).padStart(2, '0')
    return {
      value: `${valueYear}-${valueMonth}`,
      label: `${valueYear}년 ${Number(valueMonth)}월`,
    }
  })
}

const initialSelectionFromParameters = (
  parameters: URLSearchParams,
): TargetSelection | null => {
  const targetType = parameters.get('targetType')
  const name = parameters.get('name')?.trim() || '선택한 음식점'
  const address = parameters.get('address')?.trim() || undefined

  if (targetType === 'EXISTING') {
    const restaurantId = Number(parameters.get('restaurantId'))
    return Number.isSafeInteger(restaurantId) && restaurantId > 0
      ? {
          target: { type: 'EXISTING', restaurantId },
          name,
          address,
        }
      : null
  }

  if (targetType === 'KAKAO') {
    const parsed = initialKakaoTargetSchema.safeParse({
      query: parameters.get('query') ?? '',
      kakaoPlaceId: parameters.get('kakaoPlaceId') ?? '',
    })
    return parsed.success
      ? {
          target: {
            type: 'KAKAO',
            query: parsed.data.query,
            kakaoPlaceId: parsed.data.kakaoPlaceId,
          },
          name,
          address,
        }
      : null
  }

  return null
}

const issueMessages = (
  error: z.ZodError,
): Record<string, string> =>
  Object.fromEntries(
    error.issues.map((issue) => [String(issue.path[0] ?? 'form'), issue.message]),
  )

const submitErrorMessage = (error: unknown): string => {
  if (!(error instanceof ApiError)) {
    return '리뷰를 제출하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }
  if (error.status === 409) {
    return '같은 음식점에 활성 리뷰가 있으면 새로 작성할 수 없으며, 삭제·제외된 경우 최초 작성 시각부터 90일 뒤 다시 작성할 수 있습니다.'
  }
  if (error.status === 503) {
    return '음식점 또는 주소를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.'
  }
  if (error.status === 400 || error.status === 422) {
    return '입력 내용을 확인해 주세요.'
  }
  if (error.status === 401) {
    return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
  }
  return '리뷰를 제출하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function StepIndicator({ step }: { step: WizardStep }) {
  const activeNumber =
    step === 'target' ? 1 : step === 'manual' ? 2 : step === 'review' ? 3 : 4

  return (
    <ol aria-label="리뷰 작성 단계" className={styles.steps}>
      {['음식점 선택', '주소·브랜드', '방문·평가', '최종 확인'].map(
        (label, index) => (
          <li
            aria-current={activeNumber === index + 1 ? 'step' : undefined}
            className={
              activeNumber === index + 1 ? styles.activeStep : undefined
            }
            key={label}
          >
            <span>{index + 1}</span>
            {label}
          </li>
        ),
      )}
    </ol>
  )
}

function TargetSummary({ selection }: { selection: TargetSelection }) {
  return (
    <div className={styles.selectedCard}>
      <div>
        <strong>{selection.name}</strong>
        {selection.address ? <p>{selection.address}</p> : null}
      </div>
      <span>{selection.target.type === 'EXISTING' ? '등록 음식점' : '카카오 후보'}</span>
    </div>
  )
}

export function ReviewCreate({
  client = apiSession.client,
  now = () => new Date(),
}: ReviewCreateProps) {
  const navigate = useNavigate()
  const [searchParameters] = useSearchParams()
  const initialSelection = useMemo(
    () => initialSelectionFromParameters(searchParameters),
    [searchParameters],
  )
  const [step, setStep] = useState<WizardStep>(() =>
    initialSelection || searchParameters.get('mode') !== 'manual'
      ? 'target'
      : 'manual',
  )
  const [selection, setSelection] = useState<TargetSelection | null>(
    initialSelection,
  )
  const [restaurantQuery, setRestaurantQuery] = useState('')
  const [restaurantCandidates, setRestaurantCandidates] = useState<
    RestaurantSearchCandidate[]
  >([])
  const [restaurantSearchError, setRestaurantSearchError] = useState<
    string | null
  >(null)
  const [restaurantSearching, setRestaurantSearching] = useState(false)
  const [addressQuery, setAddressQuery] = useState('')
  const [addressCandidates, setAddressCandidates] = useState<
    AddressCandidate[]
  >([])
  const [selectedAddress, setSelectedAddress] =
    useState<SelectedAddress | null>(null)
  const [addressSearchError, setAddressSearchError] = useState<string | null>(
    null,
  )
  const [addressSearching, setAddressSearching] = useState(false)
  const [brandName, setBrandName] = useState('')
  const [detailAddress, setDetailAddress] = useState('')
  const [platforms, setPlatforms] = useState<DeliveryPlatform[]>([])
  const [manualErrors, setManualErrors] = useState<Record<string, string>>({})
  const [visitMonth, setVisitMonth] = useState('')
  const [ratings, setRatings] = useState<Partial<Record<
    ReviewRatingKey,
    ReviewRating
  >>>({})
  const [comment, setComment] = useState('')
  const [reviewErrors, setReviewErrors] = useState<Record<string, string>>({})
  const [reviewValues, setReviewValues] = useState<ReviewValues | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const monthOptions = useMemo(() => getSeoulMonthOptions(now()), [now])

  const searchRestaurants = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const parsed = querySchema.safeParse(restaurantQuery)
    if (!parsed.success) {
      setRestaurantSearchError(parsed.error.issues[0]?.message ?? '')
      return
    }

    setRestaurantQuery(parsed.data)
    setRestaurantSearchError(null)
    setRestaurantSearching(true)
    try {
      const response: RestaurantSearchResponse = await client.request(
        '/api/v1/restaurants/search',
        {
          method: 'get',
          query: { query: parsed.data },
          auth: 'none',
        },
      )
      setRestaurantCandidates(response.candidates?.slice(0, 20) ?? [])
      if (response.externalSearchStatus === 'UNAVAILABLE') {
        setRestaurantSearchError(
          '외부 검색은 현재 사용할 수 없습니다. 등록 음식점만 선택할 수 있습니다.',
        )
      }
    } catch {
      setRestaurantCandidates([])
      setRestaurantSearchError(
        '음식점 검색을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setRestaurantSearching(false)
    }
  }

  const chooseRestaurant = (candidate: RestaurantSearchCandidate) => {
    if (
      candidate.candidateType === 'INTERNAL' &&
      candidate.restaurantId !== null &&
      candidate.restaurantId !== undefined
    ) {
      setSelection({
        target: {
          type: 'EXISTING',
          restaurantId: candidate.restaurantId,
        },
        name: candidate.name ?? '등록된 음식점',
        address: candidate.address,
      })
      setStep('review')
      return
    }

    if (candidate.candidateType === 'KAKAO' && candidate.kakaoPlaceId) {
      setSelection({
        target: {
          type: 'KAKAO',
          query: restaurantQuery,
          kakaoPlaceId: candidate.kakaoPlaceId,
        },
        name: candidate.name ?? '카카오 음식점 후보',
        address: candidate.address,
      })
      setStep('review')
    }
  }

  const searchAddresses = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const parsed = addressQuerySchema.safeParse(addressQuery)
    if (!parsed.success) {
      setAddressSearchError(parsed.error.issues[0]?.message ?? '')
      return
    }

    setAddressQuery(parsed.data)
    setAddressSearchError(null)
    setAddressSearching(true)
    try {
      const response: AddressSearchResponse = await client.request(
        '/api/v1/addresses/search',
        {
          method: 'get',
          query: { query: parsed.data },
          auth: 'access',
        },
      )
      setAddressCandidates(response.candidates?.slice(0, 20) ?? [])
    } catch (error) {
      setAddressCandidates([])
      setAddressSearchError(
        error instanceof ApiError && error.status === 401
          ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
          : error instanceof ApiError && error.status === 503
            ? '주소를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.'
            : '주소 검색을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      )
    } finally {
      setAddressSearching(false)
    }
  }

  const continueManual = () => {
    if (!selectedAddress) {
      setManualErrors({ address: '검증된 주소 검색 결과를 선택해 주세요.' })
      return
    }
    const parsed = manualBrandSchema.safeParse({
      name: brandName,
      detailAddress,
      platforms,
    })
    if (!parsed.success) {
      setManualErrors(issueMessages(parsed.error))
      return
    }

    const pickupLocationId =
      selectedAddress.candidate.existingPickupLocationId
    const target: RestaurantTarget =
      pickupLocationId !== null && pickupLocationId !== undefined
        ? {
            type: 'MANUAL_EXISTING_LOCATION',
            pickupLocationId,
            name: parsed.data.name,
            platforms: parsed.data.platforms,
          }
        : {
            type: 'MANUAL_ADDRESS',
            addressQuery: selectedAddress.addressQuery,
            selectedStandardAddress:
              selectedAddress.candidate.standardAddress ?? '',
            detailAddress: parsed.data.detailAddress || null,
            name: parsed.data.name,
            platforms: parsed.data.platforms,
          }

    setSelection({
      target,
      name: parsed.data.name,
      address: selectedAddress.candidate.standardAddress,
    })
    setBrandName(parsed.data.name)
    setDetailAddress(parsed.data.detailAddress)
    setManualErrors({})
    setStep('review')
  }

  const continueToConfirmation = () => {
    const parsed = reviewSchema(monthOptions.map(({ value }) => value)).safeParse({
      visitMonth,
      ...ratings,
      comment,
    })
    if (!parsed.success) {
      setReviewErrors(issueMessages(parsed.error))
      return
    }

    setReviewValues(parsed.data)
    setComment(parsed.data.comment ?? '')
    setReviewErrors({})
    setSubmitError(null)
    setStep('confirm')
  }

  const submitReview = async () => {
    if (!selection || !reviewValues) {
      setSubmitError('입력 내용을 다시 확인해 주세요.')
      return
    }

    setSubmitting(true)
    setSubmitError(null)
    try {
      const response = await client.request('/api/v1/reviews', {
        method: 'post',
        body: {
          restaurantTarget: selection.target,
          visitMonth: reviewValues.visitMonth,
          pickupSpaceCleanliness: reviewValues.pickupSpaceCleanliness,
          packagingStability: reviewValues.packagingStability,
          orderReadiness: reviewValues.orderReadiness,
          handoffAccuracy: reviewValues.handoffAccuracy,
          staffInteraction: reviewValues.staffInteraction,
          riderRespect: reviewValues.riderRespect,
          comment: reviewValues.comment,
        },
        auth: 'access',
      })
      const restaurantId = response.restaurant?.restaurantId
      if (
        restaurantId === undefined ||
        !Number.isSafeInteger(restaurantId) ||
        restaurantId <= 0
      ) {
        throw new ApiError(500, 'INVALID_API_RESPONSE')
      }
      navigate(`/restaurants/${restaurantId}`, { replace: true })
    } catch (error) {
      setSubmitError(submitErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  const togglePlatform = (platform: DeliveryPlatform) => {
    setPlatforms((current) =>
      current.includes(platform)
        ? current.filter((value) => value !== platform)
        : [...current, platform],
    )
  }

  return (
    <section className={styles.page}>
      <div className={styles.heading}>
        <p className={styles.eyebrow}>공개 리뷰 작성</p>
        <h1>픽업 경험을 구조화해 공유해 주세요</h1>
        <p>
          카카오 로그인은 계정 식별 수단이며 라이더 신분이나 실제 방문을
          인증하지 않습니다.
        </p>
      </div>
      <StepIndicator step={step} />

      {step === 'target' ? (
        <div className={styles.panel}>
          {selection ? (
            <>
              <div className={styles.sectionHeading}>
                <h2>선택한 음식점을 확인해 주세요</h2>
                <p>리뷰를 작성할 배달 브랜드와 픽업 장소가 맞는지 확인합니다.</p>
              </div>
              <TargetSummary selection={selection} />
              <div className={styles.actions}>
                <button
                  className={styles.secondaryButton}
                  onClick={() => setSelection(null)}
                  type="button"
                >
                  다른 음식점 찾기
                </button>
                <button
                  className={styles.primaryButton}
                  onClick={() => setStep('review')}
                  type="button"
                >
                  이 음식점으로 계속
                </button>
              </div>
            </>
          ) : (
            <>
              <div className={styles.sectionHeading}>
                <h2>리뷰할 음식점을 선택해 주세요</h2>
                <p>내부 음식점과 서버가 확인한 카카오 후보에서 선택합니다.</p>
              </div>
              <form className={styles.searchForm} onSubmit={searchRestaurants}>
                <label htmlFor="review-restaurant-query">음식점명 또는 주소</label>
                <div className={styles.searchControls}>
                  <input
                    id="review-restaurant-query"
                    onChange={(event) => setRestaurantQuery(event.target.value)}
                    type="search"
                    value={restaurantQuery}
                  />
                  <button disabled={restaurantSearching} type="submit">
                    {restaurantSearching ? '검색 중…' : '음식점 검색'}
                  </button>
                </div>
              </form>
              {restaurantSearchError ? (
                <p className={styles.error} role="alert">
                  {restaurantSearchError}
                </p>
              ) : null}
              {restaurantCandidates.length > 0 ? (
                <ul className={styles.results}>
                  {restaurantCandidates.map((candidate, index) => (
                    <li
                      key={`${candidate.candidateType}-${candidate.restaurantId ?? candidate.kakaoPlaceId ?? index}`}
                    >
                      <div>
                        <strong>{candidate.name ?? '이름 없는 음식점'}</strong>
                        <p>{candidate.address ?? '주소 정보 없음'}</p>
                      </div>
                      <button
                        onClick={() => chooseRestaurant(candidate)}
                        type="button"
                      >
                        {candidate.name ?? '음식점'} 선택
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
              <div className={styles.manualPrompt}>
                <p>카카오 검색에 없는 배달 브랜드인가요?</p>
                <button
                  className={styles.secondaryButton}
                  onClick={() => setStep('manual')}
                  type="button"
                >
                  카카오에 없는 브랜드 등록
                </button>
              </div>
            </>
          )}
        </div>
      ) : null}

      {step === 'manual' ? (
        <div className={styles.panel}>
          <div className={styles.sectionHeading}>
            <h2>검증된 픽업 장소와 브랜드 정보를 입력해 주세요</h2>
            <p>
              주소는 직접 저장하지 않고 인증된 주소 검색 결과에서만
              선택합니다.
            </p>
          </div>
          {!selectedAddress ? (
            <>
              <form className={styles.searchForm} onSubmit={searchAddresses}>
                <label htmlFor="review-address-query">픽업 장소 주소</label>
                <div className={styles.searchControls}>
                  <input
                    id="review-address-query"
                    onChange={(event) => setAddressQuery(event.target.value)}
                    type="search"
                    value={addressQuery}
                  />
                  <button disabled={addressSearching} type="submit">
                    {addressSearching ? '검색 중…' : '주소 검색'}
                  </button>
                </div>
              </form>
              {addressSearchError ? (
                <p className={styles.error} role="alert">
                  {addressSearchError}
                </p>
              ) : null}
              {addressCandidates.length > 0 ? (
                <ul className={styles.results}>
                  {addressCandidates.map((candidate, index) => (
                    <li key={`${candidate.standardAddress ?? 'address'}-${index}`}>
                      <div>
                        <strong>
                          {candidate.standardAddress ?? '표준 주소 없음'}
                        </strong>
                        {candidate.lotNumberAddress ? (
                          <p>지번 {candidate.lotNumberAddress}</p>
                        ) : null}
                      </div>
                      <button
                        disabled={!candidate.standardAddress}
                        onClick={() =>
                          setSelectedAddress({
                            addressQuery,
                            candidate,
                          })
                        }
                        type="button"
                      >
                        {candidate.standardAddress ?? '주소'} 선택
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </>
          ) : (
            <>
              <div className={styles.selectedAddress}>
                <span>선택한 표준 주소</span>
                <strong>{selectedAddress.candidate.standardAddress}</strong>
                <button
                  className={styles.textButton}
                  onClick={() => setSelectedAddress(null)}
                  type="button"
                >
                  주소 다시 선택
                </button>
              </div>
              <div className={styles.field}>
                <label htmlFor="manual-brand-name">배달 브랜드명</label>
                <input
                  id="manual-brand-name"
                  maxLength={256}
                  onChange={(event) => setBrandName(event.target.value)}
                  type="text"
                  value={brandName}
                />
                {manualErrors.name ? (
                  <p className={styles.error}>{manualErrors.name}</p>
                ) : null}
              </div>
              {selectedAddress.candidate.existingPickupLocationId === null ||
              selectedAddress.candidate.existingPickupLocationId ===
                undefined ? (
                <div className={styles.field}>
                  <label htmlFor="manual-detail-address">
                    상세 픽업 위치 (선택)
                  </label>
                  <input
                    id="manual-detail-address"
                    maxLength={256}
                    onChange={(event) => setDetailAddress(event.target.value)}
                    type="text"
                    value={detailAddress}
                  />
                  {manualErrors.detailAddress ? (
                    <p className={styles.error}>
                      {manualErrors.detailAddress}
                    </p>
                  ) : null}
                </div>
              ) : null}
              <fieldset className={styles.platforms}>
                <legend>노출 플랫폼</legend>
                {PLATFORM_OPTIONS.map(([value, label]) => (
                  <label key={value}>
                    <input
                      checked={platforms.includes(value)}
                      onChange={() => togglePlatform(value)}
                      type="checkbox"
                    />
                    {label}
                  </label>
                ))}
                {manualErrors.platforms ? (
                  <p className={styles.error}>{manualErrors.platforms}</p>
                ) : null}
              </fieldset>
              {manualErrors.address ? (
                <p className={styles.error}>{manualErrors.address}</p>
              ) : null}
              <div className={styles.actions}>
                <button
                  className={styles.secondaryButton}
                  onClick={() => setStep('target')}
                  type="button"
                >
                  이전
                </button>
                <button
                  className={styles.primaryButton}
                  onClick={continueManual}
                  type="button"
                >
                  방문 정보 입력으로
                </button>
              </div>
            </>
          )}
        </div>
      ) : null}

      {step === 'review' && selection ? (
        <div className={styles.panel}>
          <div className={styles.sectionHeading}>
            <h2>방문 연월과 평가를 입력해 주세요</h2>
            <p>6개 항목 모두 실제로 관찰한 범위에 맞게 선택합니다.</p>
          </div>
          <TargetSummary selection={selection} />
          <div className={styles.field}>
            <label htmlFor="review-visit-month">방문 연월</label>
            <select
              id="review-visit-month"
              onChange={(event) => setVisitMonth(event.target.value)}
              value={visitMonth}
            >
              <option value="">선택해 주세요</option>
              {monthOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {reviewErrors.visitMonth ? (
              <p className={styles.error}>{reviewErrors.visitMonth}</p>
            ) : null}
          </div>
          <ReviewRatingFields
            errors={reviewErrors}
            onChange={(key, value) =>
              setRatings((current) => ({ ...current, [key]: value }))
            }
            ratings={ratings}
          />
          <ReviewCommentField
            comment={comment}
            error={reviewErrors.comment}
            notice="의견은 제출 직후 공개되며 신고 시 숨겨질 수 있습니다."
            onChange={setComment}
          />
          <div className={styles.actions}>
            <button
              className={styles.secondaryButton}
              onClick={() =>
                setStep(
                  selection.target.type.startsWith('MANUAL')
                    ? 'manual'
                    : 'target',
                )
              }
              type="button"
            >
              이전
            </button>
            <button
              className={styles.primaryButton}
              onClick={continueToConfirmation}
              type="button"
            >
              최종 확인
            </button>
          </div>
        </div>
      ) : null}

      {step === 'confirm' && selection && reviewValues ? (
        <div className={styles.panel}>
          <div className={styles.sectionHeading}>
            <h2>제출할 리뷰를 최종 확인해 주세요</h2>
            <p>구조화 평가와 자유 의견은 제출 직후 공개됩니다.</p>
          </div>
          <TargetSummary selection={selection} />
          <dl className={styles.confirmation}>
            <div>
              <dt>방문 연월</dt>
              <dd>{reviewValues.visitMonth}</dd>
            </div>
            {REVIEW_RATING_FIELDS.map(([key, label]) => (
              <div key={key}>
                <dt>{label}</dt>
                <dd>
                  {
                    RATING_OPTIONS.find(
                      ({ value }) => value === reviewValues[key],
                    )?.label
                  }
                </dd>
              </div>
            ))}
            <div>
              <dt>자유 의견</dt>
              <dd>{reviewValues.comment || '작성하지 않음'}</dd>
            </div>
          </dl>
          <aside className={styles.notice}>
            이 리뷰는 라이더 신분과 실제 방문 여부가 인증되지 않은 정보로
            공개됩니다.
          </aside>
          {submitError ? (
            <p className={styles.error} role="alert">
              {submitError}
            </p>
          ) : null}
          <div className={styles.actions}>
            <button
              className={styles.secondaryButton}
              disabled={submitting}
              onClick={() => setStep('review')}
              type="button"
            >
              입력 수정
            </button>
            <button
              className={styles.primaryButton}
              disabled={submitting}
              onClick={() => void submitReview()}
              type="button"
            >
              {submitting ? '제출 중…' : '리뷰 제출'}
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}
