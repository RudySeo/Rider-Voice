import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { render, screen } from '@testing-library/react'

import { appRoutes } from './router'

describe('appRoutes', () => {
  it('registers the public restaurant detail route', () => {
    expect(appRoutes[0]?.children).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ path: 'restaurants/:restaurantId' }),
        expect.objectContaining({ path: 'reviews/new' }),
      ]),
    )
  })

  it('uses the catch-all page for an unknown path', () => {
    const router = createMemoryRouter(appRoutes, {
      initialEntries: ['/unknown'],
    })

    render(<RouterProvider router={router} />)

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: '페이지를 찾을 수 없습니다',
      }),
    ).toBeInTheDocument()
  })
})
