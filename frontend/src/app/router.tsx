import { createBrowserRouter, type RouteObject } from 'react-router-dom'

import { HomePage } from '@/pages/HomePage'
import { NotFoundPage } from '@/pages/NotFoundPage'

import { AppShell } from './AppShell'

export const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
]

export const appRouter = createBrowserRouter(appRoutes)
