import { describe, expect, it } from 'vitest'
import { ApiError } from '../../services/apiClient'
import type { ProfileFormValues } from './types'
import { isHttpUrl, isValidUsername, serverFieldErrors, toProfileUpdate, validateField, validateProfile } from './validation'

const VALUES: ProfileFormValues = {
  displayName: 'Ada Lovelace',
  bio: '',
  location: '',
  websiteUrl: '',
  githubUrl: '',
  linkedinUrl: '',
}

describe('usernames', () => {
  it.each(['ada', 'alice-anders', 'bob_99', 'Alice', '  ada  ', 'a'.repeat(30)])('accepts %j', (value) => {
    expect(isValidUsername(value)).toBe(true)
  })

  it.each(['', 'ab', 'a'.repeat(31), 'ada.lovelace', 'ada lovelace', 'ádá', '../admin', 'ada%20'])('rejects %j', (value) => {
    expect(isValidUsername(value)).toBe(false)
  })
})

describe('URLs', () => {
  it.each(['https://example.com', 'http://example.com/me?x=1#y', 'HTTPS://Example.com', 'https://localhost:8080/a'])(
    'accepts %s',
    (value) => {
      expect(isHttpUrl(value)).toBe(true)
    },
  )

  it.each([
    'example.com',
    'www.example.com',
    'ftp://example.com',
    'javascript:alert(1)',
    'mailto:ada@example.com',
    'http:example.com',
    'https://',
    'https://user:pass@example.com',
    'https://exa mple.com',
    '//example.com',
  ])('rejects %s', (value) => {
    expect(isHttpUrl(value)).toBe(false)
  })
})

describe('field validation', () => {
  it('requires a display name', () => {
    expect(validateField('displayName', '   ')).toBe('Enter a display name.')
    expect(validateField('displayName', ' Ada ')).toBeNull()
  })

  it('checks lengths after trimming, as the backend does', () => {
    expect(validateField('displayName', ` ${'a'.repeat(100)} `)).toBeNull()
    expect(validateField('displayName', 'a'.repeat(101))).toBe('Display name can be at most 100 characters (1 too many).')
    expect(validateField('bio', 'b'.repeat(500))).toBeNull()
    expect(validateField('bio', 'b'.repeat(502))).toBe('Bio can be at most 500 characters (2 too many).')
    expect(validateField('location', 'l'.repeat(121))).toBe('Location can be at most 120 characters (1 too many).')
    expect(validateField('websiteUrl', `https://example.com/${'p'.repeat(240)}`)).toMatch(/at most 255 characters/)
  })

  it('allows blank optional fields', () => {
    expect(validateProfile(VALUES)).toEqual({})
  })

  it('requires full http(s) addresses for links', () => {
    expect(validateField('githubUrl', 'github.com/ada')).toMatch(/starting with http:\/\/ or https:\/\//)
    expect(validateField('githubUrl', ' https://github.com/ada ')).toBeNull()
  })
})

describe('the PUT body', () => {
  it('always has exactly the six editable fields, trimmed, with blanks as null', () => {
    const update = toProfileUpdate({
      ...VALUES,
      displayName: '  Ada Lovelace ',
      bio: '  Line one\n\nLine two  ',
      location: '   ',
      websiteUrl: ' https://ada.dev ',
    })

    expect(update).toEqual({
      displayName: 'Ada Lovelace',
      bio: 'Line one\n\nLine two',
      location: null,
      websiteUrl: 'https://ada.dev',
      githubUrl: null,
      linkedinUrl: null,
    })
    expect(Object.keys(update).sort()).toEqual(['bio', 'displayName', 'githubUrl', 'linkedinUrl', 'location', 'websiteUrl'])
  })

  it('never carries anything else, whatever the values object holds', () => {
    const tainted = { ...VALUES, id: '1', username: 'root', email: 'x@y.z', role: 'ADMIN', status: 'ACTIVE', password: 'p' }

    const update = toProfileUpdate(tainted as ProfileFormValues)

    for (const key of ['id', 'username', 'email', 'role', 'status', 'password', 'passwordHash']) {
      expect(update).not.toHaveProperty(key)
    }
  })
})

describe('backend field errors', () => {
  it('maps known fields to the form’s own wording and ignores unknown ones', () => {
    const error = new ApiError(400, {
      status: 400,
      detail: 'Invalid request content.',
      errors: [
        { field: 'websiteUrl', message: 'must be a valid http:// or https:// URL' },
        { field: 'githubUrl', message: 'some rule only the server knows' },
        { field: 'role', message: 'is not allowed' },
      ],
    })

    const errors = serverFieldErrors(error, { ...VALUES, websiteUrl: 'ftp://ada.dev', githubUrl: 'https://github.com/ada' })

    expect(errors).toEqual({
      websiteUrl: 'Enter a full web address starting with http:// or https://, e.g. https://example.com.',
      githubUrl: 'Check this value: GitHub wasn’t accepted.',
    })
  })
})
