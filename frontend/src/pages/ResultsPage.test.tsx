import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ResultsPage } from './ResultsPage'

function renderResults(state: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/results/c1', state }]}>
      <Routes>
        <Route path="/results/:id" element={<ResultsPage />} />
        <Route path="/" element={<div>composer home</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ResultsPage', () => {
  it('redirects to the composer when navigated to without router state', () => {
    renderResults(null)
    expect(screen.getByText('composer home')).toBeInTheDocument()
  })

  it('renders one lane per provider and streams it to completion', async () => {
    renderResults({ providers: ['CLAUDE'] })
    expect(screen.getByRole('region', { name: 'Claude' })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByText('concluído · 1 modelos')).toBeInTheDocument(),
    )
  })
})
