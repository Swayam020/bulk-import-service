import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { JobStatus } from './JobStatus'
import type { ImportJobView } from '../api/types'

const httpResponse = (ok: boolean, status: number, body: unknown) =>
  ({ ok, status, json: async () => body }) as unknown as Response

const job = (overrides: Partial<ImportJobView>): ImportJobView => ({
  jobId: 'j1',
  status: 'RUNNING',
  filename: 'customers.csv',
  sizeBytes: 1024,
  rowsProcessed: 0,
  rowsSucceeded: 0,
  rowsFailed: 0,
  errorMessage: null,
  createdAt: '2026-01-01T00:00:00Z',
  startedAt: '2026-01-01T00:00:01Z',
  finishedAt: null,
  ...overrides,
})

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

describe('JobStatus', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('polls until the import finishes, then stops and offers the error report', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(httpResponse(true, 200, job({ rowsProcessed: 500, rowsSucceeded: 500 })))
      .mockResolvedValue(
        httpResponse(
          true,
          200,
          job({ status: 'COMPLETED', rowsProcessed: 1500, rowsSucceeded: 1498, rowsFailed: 2 }),
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    render(<JobStatus jobId="j1" pollMs={0} />)

    expect(await screen.findByText('COMPLETED')).toBeInTheDocument()
    expect(screen.getByText('1,500')).toBeInTheDocument()
    expect(screen.getByText('1,498')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Download error report' })).toHaveAttribute(
      'href',
      'http://localhost:8080/api/imports/j1/errors',
    )
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/imports/j1')

    await sleep(50)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it("shows why a failed import failed", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        httpResponse(
          true,
          200,
          job({ status: 'FAILED', errorMessage: 'line 1: header must be id,name,email,amount' }),
        ),
      ),
    )
    render(<JobStatus jobId="j1" pollMs={0} />)

    expect(await screen.findByText('line 1: header must be id,name,email,amount')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Download error report' })).not.toBeInTheDocument()
  })

  it('says there is nothing to download when every row succeeded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        httpResponse(true, 200, job({ status: 'COMPLETED', rowsProcessed: 3, rowsSucceeded: 3 })),
      ),
    )
    render(<JobStatus jobId="j1" pollMs={0} />)

    expect(await screen.findByText('No failed rows.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Download error report' })).not.toBeInTheDocument()
  })

  it("shows the server's message when the job cannot be read", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        httpResponse(false, 404, {
          error: 'Not Found',
          message: 'import job j1 not found',
          timestamp: '2026-01-01T00:00:00Z',
        }),
      ),
    )
    render(<JobStatus jobId="j1" pollMs={0} />)

    expect(await screen.findByText('import job j1 not found')).toBeInTheDocument()
  })
})
