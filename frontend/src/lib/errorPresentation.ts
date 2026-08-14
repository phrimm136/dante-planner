/**
 * How a classified failure is shown to the user.
 *
 * Splitting presentation from `classifyAppError` keeps the vocabulary usable by
 * callers that react to a failure without reporting it, and leaves exactly one
 * place that decides what an error looks like.
 */

import { toast } from 'sonner'

import i18n from '@/lib/i18n'
import { linkifyText } from '@/components/ui/LinkifyText'
import { assertNever } from '@/lib/utils'

import { classifyAppError, validationAppError } from './apiErrorClassifier'
import type { AppError, RestrictionKind, UnavailableScope } from './apiErrorClassifier'

export interface ErrorPresentation {
  key: string
  params?: Record<string, string>
  severity: 'error' | 'warning'
  /** Append the contact-on-repeat description. Opt-in, per error. */
  supportHint: boolean
}

const CONTACT_KEY = 'common:errors.contactOnRepeat'
const GENERIC_KEY = 'common:errors.generic.message'
const QUOTA_KEY = 'planner:pages.plannerMD.save.quotaExceeded'
const RATE_LIMIT_KEY = 'common:errors.rateLimit.message'
const FORBIDDEN_KEY = 'common:errors.forbidden.message'
const RESOURCE_NOT_FOUND_KEY = 'common:errors.resourceNotFound.message'
const RETRYABLE_KEY = 'common:errors.retryable.message'

const RESTRICTION_KEY: Record<RestrictionKind, string> = {
  banned: 'common:moderation.banned',
  timedOut: 'common:moderation.timedOut',
}

/** `write` is null: the sync-paused banner already stands for it. */
const UNAVAILABLE_KEY: Record<UnavailableScope, string | null> = {
  service: 'common:errors.serviceUpdating',
  backend: 'common:errors.backendUnavailable',
  auth: 'common:errors.authUnavailable',
  write: null,
}

/** How an error is shown, or null when a dedicated surface owns it. */
export function presentError(error: AppError): ErrorPresentation | null {
  switch (error.kind) {
    case 'conflict':
      return null
    case 'validation':
      return { key: error.key, params: error.params, severity: 'error', supportHint: false }
    case 'restricted':
      return { key: RESTRICTION_KEY[error.reason], severity: 'error', supportHint: false }
    case 'unavailable': {
      const key = UNAVAILABLE_KEY[error.scope]
      return key === null ? null : { key, severity: 'warning', supportHint: false }
    }
    case 'rateLimit':
      return { key: RATE_LIMIT_KEY, severity: 'warning', supportHint: false }
    case 'retryable':
      return { key: RETRYABLE_KEY, severity: 'warning', supportHint: true }
    case 'forbidden':
      return { key: FORBIDDEN_KEY, severity: 'error', supportHint: false }
    case 'notFound':
      return { key: RESOURCE_NOT_FOUND_KEY, severity: 'error', supportHint: false }
    case 'quota':
      return { key: QUOTA_KEY, severity: 'error', supportHint: false }
    case 'unknown':
      return { key: GENERIC_KEY, severity: 'error', supportHint: true }
    default:
      return assertNever(error)
  }
}

function emit(presentation: ErrorPresentation): void {
  const message = i18n.t(presentation.key, presentation.params)
  const options = presentation.supportHint
    ? { description: linkifyText(i18n.t(CONTACT_KEY)) }
    : undefined

  if (presentation.severity === 'warning') {
    toast.warning(message, options)
    return
  }
  toast.error(message, options)
}

/** Report a failure that is already classified. */
export function showAppError(error: AppError): void {
  const presentation = presentError(error)
  if (presentation === null) return
  emit(presentation)
}

export function showError(error: unknown): void {
  showAppError(classifyAppError(error))
}

/** Only the unavailable family, for the query cache's deliberately narrow toasting. */
export function showUnavailable(error: unknown): void {
  const classified = classifyAppError(error)
  if (classified.kind !== 'unavailable') return

  const presentation = presentError(classified)
  if (presentation === null) return
  emit(presentation)
}

/** Report a failure under a message the caller chose, not one the classifier picked. */
export function showErrorMessage(key: string, params?: Record<string, string>): void {
  showAppError(validationAppError({ key, params }))
}

export function showSuccess(key: string, params?: Record<string, unknown>): void {
  toast.success(i18n.t(key, params))
}
