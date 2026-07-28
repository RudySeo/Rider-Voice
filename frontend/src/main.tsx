import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from '@/app/App'
import { appRouter } from '@/app/router'
import '@/shared/styles/global.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Rider Voice frontend root element was not found.')
}

createRoot(rootElement).render(
  <StrictMode>
    <App router={appRouter} />
  </StrictMode>,
)
