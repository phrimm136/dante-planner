// Public API of the user settings concept. Import from '@/shared/userSettings'.
export {
  userSettingsKeys,
  useUserSettingsQuery,
  useUpdateUserSettingsMutation,
} from './hooks/useUserSettings'
export { useFirstLoginStore } from './stores/useFirstLoginStore'
export {
  UserSettingsResponseSchema,
  UpdateUserSettingsRequestSchema,
} from './schemas/UserSettingsSchemas'
export type { UserSettingsResponse, UpdateUserSettingsRequest } from './schemas/UserSettingsSchemas'
