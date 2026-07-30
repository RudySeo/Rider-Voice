import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { AppShell } from './AppShell'

describe('AppShell', () => {
  it('provides navigation and a skip link', () => {
    render(
      <MemoryRouter>
        <AppShell />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('navigation', { name: '주요 메뉴' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: '본문으로 건너뛰기' }),
    ).toHaveAttribute('href', '#main-content')
  })
})
