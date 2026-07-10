import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Breadcrumb from './Breadcrumb'

const renderTrail = (entries) =>
  render(
    <MemoryRouter>
      <Breadcrumb entries={entries} />
    </MemoryRouter>,
  )

describe('Breadcrumb', () => {
  it('renders nothing for an empty trail', () => {
    const { container } = renderTrail([])
    expect(container).toBeEmptyDOMElement()
  })

  it('shows the root folder as "Root"', () => {
    renderTrail([{ id: 'root', name: '/' }])
    expect(screen.getByText('Root')).toBeInTheDocument()
  })

  it('links every entry except the last (current) one', () => {
    renderTrail([
      { id: 'root', name: '/' },
      { id: 'dept', name: 'Finance' },
      { id: 'case', name: 'Case 42' },
    ])
    // Ancestors are links; the current folder is plain text.
    expect(screen.getByRole('link', { name: 'Root' })).toHaveAttribute('href', '/folders/root')
    expect(screen.getByRole('link', { name: 'Finance' })).toHaveAttribute('href', '/folders/dept')
    expect(screen.queryByRole('link', { name: 'Case 42' })).toBeNull()
    expect(screen.getByText('Case 42')).toBeInTheDocument()
  })
})
