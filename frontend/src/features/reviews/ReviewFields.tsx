/* eslint-disable react-refresh/only-export-components */
import { z } from 'zod'

import type { components } from '@/shared/api/generated'

import styles from './ReviewFields.module.css'

type UpdateReviewRequest = components['schemas']['UpdateReviewRequest']

export type ReviewRatingKey = Exclude<keyof UpdateReviewRequest, 'comment'>
export type ReviewRating = UpdateReviewRequest[ReviewRatingKey]
export type ReviewRatings = Pick<UpdateReviewRequest, ReviewRatingKey>

export const RATING_VALUES = [
  'VERY_GOOD',
  'GOOD',
  'NEEDS_IMPROVEMENT',
  'MAJOR_IMPROVEMENT',
  'NOT_OBSERVED',
] as const satisfies readonly ReviewRating[]

export const RATING_OPTIONS: ReadonlyArray<{
  value: ReviewRating
  label: string
  description: string
}> = [
  { value: 'VERY_GOOD', label: '매우 좋음', description: '기대보다 매우 좋은 상태였습니다.' },
  { value: 'GOOD', label: '좋음', description: '전반적으로 원활하고 좋은 상태였습니다.' },
  { value: 'NEEDS_IMPROVEMENT', label: '개선 필요', description: '일부 불편이 있어 개선이 필요했습니다.' },
  { value: 'MAJOR_IMPROVEMENT', label: '큰 개선 필요', description: '픽업 과정에 큰 어려움이 있었습니다.' },
  { value: 'NOT_OBSERVED', label: '관찰하지 못함', description: '이번 방문에서는 해당 항목을 확인하지 못했습니다.' },
]

export const REVIEW_RATING_FIELDS = [
  ['pickupSpaceCleanliness', '픽업 공간 청결'],
  ['packagingStability', '포장 안정성'],
  ['orderReadiness', '주문 준비 상태'],
  ['handoffAccuracy', '주문 확인·전달 정확성'],
  ['staffInteraction', '직원 응대'],
  ['riderRespect', '라이더 존중'],
] as const satisfies ReadonlyArray<readonly [ReviewRatingKey, string]>

export const reviewFieldsSchema = z.object({
  pickupSpaceCleanliness: z.enum(RATING_VALUES, { error: '평가를 선택해 주세요.' }),
  packagingStability: z.enum(RATING_VALUES, { error: '평가를 선택해 주세요.' }),
  orderReadiness: z.enum(RATING_VALUES, { error: '평가를 선택해 주세요.' }),
  handoffAccuracy: z.enum(RATING_VALUES, { error: '평가를 선택해 주세요.' }),
  staffInteraction: z.enum(RATING_VALUES, { error: '평가를 선택해 주세요.' }),
  riderRespect: z.enum(RATING_VALUES, { error: '평가를 선택해 주세요.' }),
  comment: z
    .string()
    .trim()
    .max(200, '의견은 공백을 정리한 뒤 200자 이하여야 합니다.')
    .transform((value) => value || null),
})

type RatingFieldsProps = {
  errors?: Partial<Record<ReviewRatingKey, string>>
  onChange: (key: ReviewRatingKey, value: ReviewRating) => void
  ratings: Partial<ReviewRatings>
}

export function ReviewRatingFields({ errors = {}, onChange, ratings }: RatingFieldsProps) {
  return (
    <div className={styles.ratingGrid}>
      {REVIEW_RATING_FIELDS.map(([key, label]) => (
        <fieldset className={styles.ratingField} key={key}>
          <legend>{label}</legend>
          {RATING_OPTIONS.map((option) => (
            <label key={option.value}>
              <input
                aria-label={option.label}
                checked={ratings[key] === option.value}
                name={key}
                onChange={() => onChange(key, option.value)}
                type="radio"
                value={option.value}
              />
              <span>
                <strong>{option.label}</strong>
                <small>{option.description}</small>
              </span>
            </label>
          ))}
          {errors[key] ? <p className={styles.error}>{errors[key]}</p> : null}
        </fieldset>
      ))}
    </div>
  )
}

type CommentFieldProps = {
  comment: string
  error?: string
  notice: string
  onChange: (value: string) => void
}

export function ReviewCommentField({ comment, error, notice, onChange }: CommentFieldProps) {
  return (
    <div className={styles.field}>
      <label htmlFor="review-comment">자유 의견 (선택)</label>
      <textarea
        id="review-comment"
        maxLength={205}
        onChange={(event) => onChange(event.target.value)}
        rows={5}
        value={comment}
      />
      <p className={styles.helper}>
        <span>{notice}</span>
        <span>공백을 정리한 뒤 최대 200자입니다.</span>
      </p>
      {error ? <p className={styles.error}>{error}</p> : null}
    </div>
  )
}
