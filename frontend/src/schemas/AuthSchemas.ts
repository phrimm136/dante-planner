import { z } from 'zod';

/**
 * User schema for authentication
 * Only email and username are returned (minimal privacy impact)
 * Tokens are stored in HttpOnly cookies (not in response)
 */
export const UserSchema = z.object({
  email: z.string().email({ message: 'Invalid email format' }),
  usernameEpithet: z.string(),
  usernameSuffix: z.string(),
}).strict();

export type User = z.infer<typeof UserSchema>;
