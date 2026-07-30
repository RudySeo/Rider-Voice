import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ComponentProps } from 'react'
import { RouterProvider } from 'react-router-dom'

import {
  apiSession,
  AuthProvider,
  type AuthSession,
} from '@/features/auth/AuthFlow'

type AppProps = {
  router: ComponentProps<typeof RouterProvider>['router']
  session?: AuthSession
}

export function App({ router, session = apiSession }: AppProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
          },
        },
      }),
  )

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider session={session}>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  )
}
