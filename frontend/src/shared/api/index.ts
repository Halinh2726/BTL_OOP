import type { ApiClient } from './ApiClient';
import { createHttpApiClient } from './httpApiClient';
import { createMockApiClient } from './mock/mockApiClient';

export type { ApiClient, Idempotent } from './ApiClient';
export { ApiError, errorMessage, isApiError } from './ApiError';

/** VITE_API_MODE=mock -> dữ liệu giả lập; mặc định gọi backend thật. */
export const isMockMode = import.meta.env.VITE_API_MODE === 'mock';

export const api: ApiClient = isMockMode ? createMockApiClient() : createHttpApiClient();
