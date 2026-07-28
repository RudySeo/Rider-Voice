import { createBrowserRouter, type RouteObject } from 'react-router-dom'

import {
  Consent,
  LoginPageContent,
  OAuthCallback,
} from '@/features/auth/AuthFlow'
import { RestaurantDetailPage } from '@/features/restaurants/PublicDiscovery'
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
        path: 'login',
        element: <LoginPageContent />,
      },
      {
        path: 'auth/callback',
        element: <OAuthCallback />,
      },
      {
        path: 'consent',
        element: <Consent />,
      },
      {
        path: 'restaurants/:restaurantId',
        element: <RestaurantDetailPage />,
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
]

export const appRouter = createBrowserRouter(appRoutes)
