import { ApiError } from '../../services/apiClient'
import {
  PROFILE_FIELDS,
  PROFILE_LIMITS,
  type ProfileField,
  type ProfileFormValues,
  type ProfileUpdate,
  type UserProfile,
} from './types'

const USERNAME_PATTERN = /^[a-z0-9_-]{3,30}$/

/** The backend's username rule, applied after trimming and lower-casing (`User.isValidUsername`). */
export function isValidUsername(value: string): boolean {
  return USERNAME_PATTERN.test(value.trim().toLowerCase())
}

/**
 * An absolute http(s) URL with a host and no user info, as the backend's `@HttpUrl`
 * requires. The browser's URL parser is more forgiving than Java's `URI`, so whitespace and
 * scheme-relative forms such as `http:example.com` are rejected up front.
 */
export function isHttpUrl(value: string): boolean {
  if (/\s/.test(value) || !/^https?:\/\//i.test(value)) {
    return false
  }
  try {
    const url = new URL(value)
    return url.hostname !== '' && url.username === '' && url.password === ''
  } catch {
    return false
  }
}

const URL_FIELDS: ReadonlySet<ProfileField> = new Set(['websiteUrl', 'githubUrl', 'linkedinUrl'])

const LABELS: Record<ProfileField, string> = {
  displayName: 'Display name',
  bio: 'Bio',
  location: 'Location',
  websiteUrl: 'Website',
  githubUrl: 'GitHub',
  linkedinUrl: 'LinkedIn',
}

const numberFormat = new Intl.NumberFormat('en-US')

/** What is wrong with one field's value, or `null`. Lengths count the trimmed value, as the backend does. */
export function validateField(field: ProfileField, raw: string): string | null {
  const value = raw.trim()
  const limit = PROFILE_LIMITS[field]
  if (field === 'displayName' && value === '') {
    return 'Enter a display name.'
  }
  if (value.length > limit) {
    return `${LABELS[field]} can be at most ${numberFormat.format(limit)} characters (${numberFormat.format(value.length - limit)} too many).`
  }
  if (URL_FIELDS.has(field) && value !== '' && !isHttpUrl(value)) {
    return 'Enter a full web address starting with http:// or https://, e.g. https://example.com.'
  }
  return null
}

export type ProfileFieldErrors = Partial<Record<ProfileField, string>>

export function validateProfile(values: ProfileFormValues): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {}
  for (const field of PROFILE_FIELDS) {
    const error = validateField(field, values[field])
    if (error) {
      errors[field] = error
    }
  }
  return errors
}

/** The form, filled in from a profile; missing optional values become empty fields. */
export function formValuesOf(profile: UserProfile): ProfileFormValues {
  return {
    displayName: profile.displayName,
    bio: profile.bio ?? '',
    location: profile.location ?? '',
    websiteUrl: profile.websiteUrl ?? '',
    githubUrl: profile.githubUrl ?? '',
    linkedinUrl: profile.linkedinUrl ?? '',
  }
}

/**
 * The complete `PUT /profile` body: every editable field, trimmed, with blank optional
 * values as `null`. Built field by field, so nothing else can ever be sent.
 */
export function toProfileUpdate(values: ProfileFormValues): ProfileUpdate {
  const optional = (value: string) => value.trim() || null
  return {
    displayName: values.displayName.trim(),
    bio: optional(values.bio),
    location: optional(values.location),
    websiteUrl: optional(values.websiteUrl),
    githubUrl: optional(values.githubUrl),
    linkedinUrl: optional(values.linkedinUrl),
  }
}

/**
 * Field errors from a backend 400, in this form's own words: the backend's messages are
 * not shown. Fields the form does not know are left out.
 */
export function serverFieldErrors(error: unknown, values: ProfileFormValues): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {}
  if (error instanceof ApiError) {
    for (const { field } of error.fieldErrors) {
      if ((PROFILE_FIELDS as readonly string[]).includes(field)) {
        const name = field as ProfileField
        errors[name] = validateField(name, values[name]) ?? `Check this value: ${LABELS[name]} wasn’t accepted.`
      }
    }
  }
  return errors
}
