import { createBrowserRouter, type RouteObject } from 'react-router-dom'

import {
  Consent,
  LoginPageContent,
  OAuthCallback,
  ProtectedRoute,
} from '@/features/auth/AuthFlow'
import { RestaurantDetailPage } from '@/features/restaurants/PublicDiscovery'
import { ReviewCreate } from '@/features/reviews/ReviewCreate'
import {
  MyReviews,
  ReviewEdit,
} from '@/features/reviews/ReviewManagement'
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
        path: 'reviews/new',
        element: (
          <ProtectedRoute>
            <ReviewCreate />
          </ProtectedRoute>
        ),
      },
      {
        path: 'me/reviews',
        element: (
          <ProtectedRoute>
            <MyReviews />
          </ProtectedRoute>
        ),
      },
      {
        path: 'reviews/:reviewId/edit',
        element: (
          <ProtectedRoute>
            <ReviewEdit />
          </ProtectedRoute>
        ),
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
]

export const appRouter = createBrowserRouter(appRoutes)
