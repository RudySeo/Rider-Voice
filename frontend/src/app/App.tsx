import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ComponentProps } from 'react'
import { RouterProvider } from 'react-router-dom'

type AppProps = {
  router: ComponentProps<typeof RouterProvider>['router']
}

export function App({ router }: AppProps) {
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
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
