import { z } from 'zod';

/**
 * Environment variable schema with validation
 *
 * Validates at build time to ensure all required variables are present.
 * Vite replaces import.meta.env references with actual values during build.
 */
const envSchema = z.object({
  VITE_API_BASE_URL: z.string().url().optional().default('http://localhost:8080'),
  DEV: z.boolean(),
  PROD: z.boolean(),
  MODE: z.enum(['development', 'production', 'test']),
});

const envValidation = envSchema.safeParse(import.meta.env);

if (!envValidation.success) {
  console.error('❌ Environment validation failed:', envValidation.error.format());
  if (import.meta.env.MODE !== 'test') {
    throw new Error('Invalid environment configuration. Check your .env file.');
  }
}

/**
 * Type-safe environment variables
 *
 * Usage:
 * ```typescript
 * import { env } from '@/lib/env';
 *
 * const apiBaseUrl = env.VITE_API_BASE_URL; // Type-safe!
 * ```
 */
const testFallback = {
  VITE_API_BASE_URL: 'http://localhost:8080',
  DEV: false,
  PROD: false,
  MODE: 'test',
} as const;

export const env = envValidation.success ? envValidation.data : testFallback;
