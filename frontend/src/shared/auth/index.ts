export {
  useAuthQuery,
  useAuthQueryNonBlocking,
  useLogout,
  authQueryKeys,
} from './hooks/useAuthQuery'
export { clearSessionOnUnauthorized } from './lib/clearSessionOnUnauthorized'
export { startGoogleLogin } from './hooks/useGoogleLogin'
export { useLogoutEverywhere } from './hooks/useLogoutEverywhere'
export { UserSchema, UserRoleSchema, USER_ROLES, isStaff } from './schemas/AuthSchemas'
export type { User, UserRole } from './schemas/AuthSchemas'
