import { render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router-dom'

import { App } from '@/app/App'
import { appRoutes } from '@/app/router'

function renderRoute(path: string) {
  const router = createMemoryRouter(appRoutes, {
    initialEntries: [path],
  })

  return render(<App router={router} />)
}

describe('application shell', () => {
  it('renders the home route inside accessible navigation', () => {
    renderRoute('/')

    expect(
      screen.getByRole('navigation', { name: '주요 메뉴' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈' })).toHaveAttribute('href', '/')
    expect(
      screen.getByRole('heading', { level: 1, name: 'Rider Voice' }),
    ).toBeInTheDocument()
  })

  it('renders a not-found route with a way back home', () => {
    renderRoute('/없는-페이지')

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
