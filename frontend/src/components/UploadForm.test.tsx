import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { UploadForm } from './UploadForm'

const accepted = { jobId: 'j1', status: 'QUEUED', filename: 'customers.csv', sizeBytes: 32 }

// The client only reads ok/status/json, so a plain stub is enough here.
const httpResponse = (ok: boolean, status: number, body: unknown) =>
  ({ ok, status, json: async () => body }) as unknown as Response

const csvFile = () =>
  new File(['id,name,email,amount\n1,Alice,alice@example.com,10.00\n'], 'customers.csv', {
    type: 'text/csv',
  })
const fileField = () => screen.getByLabelText('CSV file')
const submitButton = () => screen.getByRole('button', { name: 'Upload' })

describe('UploadForm', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uploads the chosen file and reports the accepted job', async () => {
    const onAccepted = vi.fn()
    const fetchMock = vi.fn().mockResolvedValue(httpResponse(true, 202, accepted))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<UploadForm onAccepted={onAccepted} />)

    const file = csvFile()
    await user.upload(fileField(), file)
    await user.click(submitButton())

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/imports')
    expect(init.method).toBe('POST')
    expect(init.headers).toBeUndefined()
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.body as FormData).get('file')).toBe(file)
    await waitFor(() => expect(onAccepted).toHaveBeenCalledWith(accepted))
  })

  it('asks for a file and does not call the API when none is chosen', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<UploadForm onAccepted={vi.fn()} />)

    await user.click(submitButton())

    expect(await screen.findByText('choose a CSV file first')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('refuses a file over the 50MB limit without sending it', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<UploadForm onAccepted={vi.fn()} />)

    const file = csvFile()
    Object.defineProperty(file, 'size', { value: 50 * 1024 * 1024 + 1 })
    await user.upload(fileField(), file)
    await user.click(submitButton())

    expect(await screen.findByText('file exceeds the 50MB upload limit')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("shows the server's error message when the upload is rejected", async () => {
    const onAccepted = vi.fn()
    const fetchMock = vi.fn().mockResolvedValue(
      httpResponse(false, 400, {
        error: 'Bad Request',
        message: 'file is empty',
        timestamp: '2026-01-01T00:00:00Z',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<UploadForm onAccepted={onAccepted} />)

    await user.upload(fileField(), csvFile())
    await user.click(submitButton())

    expect(await screen.findByText('file is empty')).toBeInTheDocument()
    expect(submitButton()).toBeEnabled()
    expect(onAccepted).not.toHaveBeenCalled()
  })
})
