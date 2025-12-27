/**
 * OAuth 2.0 PKCE (Proof Key for Code Exchange) utilities
 *
 * PKCE enhances OAuth security by preventing authorization code interception attacks.
 * Even with a confidential client (client_secret), PKCE adds an additional layer of security.
 *
 * @see https://datatracker.ietf.org/doc/html/rfc7636
 */

/**
 * Base64 URL-encode a buffer
 *
 * Converts standard Base64 to URL-safe Base64:
 * - Replace + with -
 * - Replace / with _
 * - Remove = padding
 */
function base64URLEncode(buffer: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...buffer));
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

/**
 * Generate a cryptographically random code verifier
 *
 * Code verifier is a high-entropy cryptographic random string
 * using unreserved characters [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
 *
 * Length: 43-128 characters (we use 43 for 32 bytes = 256 bits of entropy)
 *
 * @returns Base64 URL-encoded random string
 */
export function generateCodeVerifier(): string {
  const array = new Uint8Array(32); // 32 bytes = 256 bits
  crypto.getRandomValues(array);
  return base64URLEncode(array);
}

/**
 * Generate code challenge from code verifier using SHA-256
 *
 * Code challenge is sent to OAuth provider during authorization request.
 * Provider stores it and later verifies the code_verifier matches.
 *
 * Method: S256 (SHA-256 hashing)
 *
 * @param verifier - The code verifier to hash
 * @returns Base64 URL-encoded SHA-256 hash of verifier
 */
export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return base64URLEncode(new Uint8Array(hash));
}

/**
 * Generate a cryptographically random OAuth state parameter
 *
 * State parameter prevents CSRF attacks by ensuring the authorization
 * response corresponds to the authorization request initiated by the user.
 *
 * @returns Random UUID string
 */
export function generateState(): string {
  return crypto.randomUUID();
}

/**
 * In-memory storage for OAuth parameters
 *
 * SSR-safe: Uses in-memory storage instead of sessionStorage
 * Popup flow keeps parent window context, so this works for OAuth callback
 *
 * Note: Values are cleared after use and on page reload (security)
 */
const oauthStorage = new Map<string, { state: string; codeVerifier: string }>();

/**
 * Store OAuth state and code verifier in memory
 *
 * Note: In-memory storage is used for SSR compatibility:
 * 1. OAuth popup flow keeps parent window context (same memory space)
 * 2. Values automatically cleared on page reload
 * 3. Not persisted anywhere (security)
 *
 * @param state - OAuth state parameter
 * @param codeVerifier - PKCE code verifier
 */
export function storeOAuthParams(state: string, codeVerifier: string): void {
  oauthStorage.set(state, { state, codeVerifier });
}

/**
 * Retrieve and validate OAuth state from memory
 *
 * @param receivedState - State parameter received from OAuth callback
 * @returns Stored OAuth params if valid, null otherwise
 */
export function validateAndGetOAuthParams(receivedState: string | null): {
  state: string;
  codeVerifier: string;
} | null {
  if (!receivedState) {
    console.error('No state parameter received');
    return null;
  }

  const stored = oauthStorage.get(receivedState);
  if (!stored) {
    console.error('No saved state found - possible CSRF attack or page reload');
    return null;
  }

  if (receivedState !== stored.state) {
    console.error('State mismatch - possible CSRF attack');
    return null;
  }

  // Clear state after validation (one-time use)
  oauthStorage.delete(receivedState);
  return stored;
}
