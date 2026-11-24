import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const httpResponse = (body: unknown) =>
  ({ ok: true, status: 200, json: async () => body }) as unknown as Response

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('moves from upload to the job view and back for the next file', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          httpResponse({ jobId: 'j1', status: 'QUEUED', filename: 'customers.csv', sizeBytes: 9 }),
        )
        .mockResolvedValue(
          httpResponse({
            jobId: 'j1',
            status: 'COMPLETED',
            filename: 'customers.csv',
            sizeBytes: 9,
            rowsProcessed: 1,
            rowsSucceeded: 1,
            rowsFailed: 0,
            errorMessage: null,
            createdAt: '2026-01-01T00:00:00Z',
            startedAt: '2026-01-01T00:00:01Z',
            finishedAt: '2026-01-01T00:00:02Z',
          }),
        ),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.upload(screen.getByLabelText('CSV file'), new File(['id'], 'customers.csv'))
    await user.click(screen.getByRole('button', { name: 'Upload' }))
    expect(await screen.findByText('COMPLETED')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Upload another file' }))
    expect(screen.getByLabelText('CSV file')).toBeInTheDocument()
  })
})
