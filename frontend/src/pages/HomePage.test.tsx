import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('identifies the prototype and its verification boundary', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(
      screen.getByRole('heading', { level: 1, name: 'Rider Voice' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/라이더 신분과 실제 방문 여부가 인증되지 않은/),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 2, name: '음식점 찾기' }),
    ).toBeInTheDocument()
  })
})
