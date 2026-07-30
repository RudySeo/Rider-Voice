import { screen } from '@testing-library/react'

describe('frontend entry point', () => {
  it('mounts the application into the root element', async () => {
    document.body.innerHTML = '<div id="root"></div>'

    await import('./main')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Rider Voice' }),
    ).toBeInTheDocument()
  })
})
