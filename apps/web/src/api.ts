import { EnharaApiClient } from '../../../packages/api-client/src'

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

export const api = new EnharaApiClient(API_URL)
