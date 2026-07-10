import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import OcrStatusBadge from './OcrStatusBadge'

describe('OcrStatusBadge', () => {
  it('renders the human label for each known status', () => {
    const cases = {
      pending: 'OCR pending',
      processing: 'OCR processing',
      completed: 'OCR completed',
      failed: 'OCR failed',
      not_required: 'OCR not required',
    }
    for (const [status, label] of Object.entries(cases)) {
      const { unmount } = render(<OcrStatusBadge status={status} />)
      expect(screen.getByText(label)).toBeInTheDocument()
      unmount()
    }
  })

  it('is case-insensitive on the status value', () => {
    render(<OcrStatusBadge status="COMPLETED" />)
    expect(screen.getByText('OCR completed')).toBeInTheDocument()
  })

  it('falls back to the raw status for an unknown value', () => {
    render(<OcrStatusBadge status="weird_state" />)
    expect(screen.getByText('weird_state')).toBeInTheDocument()
  })

  it('shows "Unknown" when no status is provided', () => {
    render(<OcrStatusBadge status={undefined} />)
    expect(screen.getByText('Unknown')).toBeInTheDocument()
  })
})
