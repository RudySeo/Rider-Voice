import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { NotFoundPage } from './NotFoundPage'

describe('NotFoundPage', () => {
  it('offers a link back to the home route', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: '페이지를 찾을 수 없습니다',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '홈으로 돌아가기' }),
    ).toHaveAttribute('href', '/')
  })
})
