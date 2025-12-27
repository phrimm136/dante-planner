import { z } from 'zod';

/**
 * Environment variable schema with validation
 *
 * Validates at build time to ensure all required variables are present.
 * Vite replaces import.meta.env references with actual values during build.
 */
const envSchema = z.object({
  VITE_GOOGLE_CLIENT_ID: z.string().min(1, 'Google Client ID is required'),
  VITE_API_BASE_URL: z.string().url().optional().default('http://localhost:8080'),
  DEV: z.boolean(),
  PROD: z.boolean(),
  MODE: z.enum(['development', 'production', 'test']),
});

const envValidation = envSchema.safeParse(import.meta.env);

if (!envValidation.success) {
  console.error('❌ Environment validation failed:', envValidation.error.format());
  throw new Error('Invalid environment configuration. Check your .env file.');
}

/**
 * Type-safe environment variables
 *
 * Usage:
 * ```typescript
 * import { env } from '@/lib/env';
 *
 * const clientId = env.VITE_GOOGLE_CLIENT_ID; // Type-safe!
 * ```
 */
export const env = envValidation.data;
