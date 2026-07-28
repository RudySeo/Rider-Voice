import { render, screen } from '@testing-library/react'

import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('identifies the prototype and its verification boundary', () => {
    render(<HomePage />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'Rider Voice' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/라이더 신분과 실제 방문 여부가 인증되지 않은/),
    ).toBeInTheDocument()
  })
})
