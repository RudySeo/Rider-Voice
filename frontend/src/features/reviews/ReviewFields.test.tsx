import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import {
  ReviewCommentField,
  ReviewRatingFields,
  reviewFieldsSchema,
} from './ReviewFields'

describe('shared review fields', () => {
  it('renders all rating choices and reports a changed rating', async () => {
    const onChange = vi.fn()
    render(
      <ReviewRatingFields
        onChange={onChange}
        ratings={{ pickupSpaceCleanliness: 'GOOD' }}
      />,
    )

    const cleanliness = screen.getByRole('group', { name: '픽업 공간 청결' })
    expect(within(cleanliness).getAllByRole('radio')).toHaveLength(5)
    expect(within(cleanliness).getByRole('radio', { name: '좋음' })).toBeChecked()

    await userEvent.click(
      within(cleanliness).getByRole('radio', { name: '매우 좋음' }),
    )
    expect(onChange).toHaveBeenCalledWith('pickupSpaceCleanliness', 'VERY_GOOD')
  })

  it('shares the comment notice and trimmed 200-character validation', async () => {
    const onChange = vi.fn()
    render(
      <ReviewCommentField
        comment="기존 의견"
        notice="즉시 공개됩니다."
        onChange={onChange}
      />,
    )

    expect(screen.getByText('즉시 공개됩니다.')).toBeInTheDocument()
    await userEvent.type(
      screen.getByRole('textbox', { name: '자유 의견 (선택)' }),
      ' 수정',
    )
    expect(onChange).toHaveBeenCalled()

    const base = {
      pickupSpaceCleanliness: 'GOOD',
      packagingStability: 'GOOD',
      orderReadiness: 'GOOD',
      handoffAccuracy: 'GOOD',
      staffInteraction: 'GOOD',
      riderRespect: 'GOOD',
    }
    expect(
      reviewFieldsSchema.parse({ ...base, comment: `  ${'가'.repeat(200)}  ` })
        .comment,
    ).toBe('가'.repeat(200))
    expect(
      reviewFieldsSchema.safeParse({ ...base, comment: '가'.repeat(201) }).success,
    ).toBe(false)
  })
})
