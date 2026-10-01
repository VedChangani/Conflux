import { ApiError } from '../../services/apiClient'

export type ManageAction = 'create' | 'update' | 'publish' | 'archive'

export const MY_LISTINGS_PAGE_SIZE = 12

export const canEdit = (status: string) => status === 'DRAFT' || status === 'PUBLISHED'
export const canPublish = (status: string) => status === 'DRAFT'
export const canArchive = (status: string) => status === 'DRAFT' || status === 'PUBLISHED'

export function ownerStatusSummary(status: string): string {
  switch (status) {
    case 'DRAFT':
      return 'A private draft: only you can see it. Publish it when it’s ready for the marketplace.'
    case 'PUBLISHED':
      return 'Live on the marketplace. Changes you save appear there right away.'
    case 'ARCHIVED':
      return 'Archived: off the marketplace for good. It can no longer be edited or published.'
    case 'SUSPENDED':
      return 'Suspended by Conflux moderators: hidden from the marketplace, and it can’t be edited or archived.'
    default:
      return 'This listing can’t be changed right now.'
  }
}

const CONFLICT: Record<ManageAction, string> = {
  create: 'The listing couldn’t be created because of a conflict. Please try again.',
  update: 'This listing can no longer be edited: it has been archived or suspended.',
  publish: 'Only drafts can be published. The listing’s current status is shown.',
  archive: 'This listing can’t be archived in its current state. Its current status is shown.',
}

const RATE_LIMITED: Record<ManageAction, string> = {
  create: 'You’ve created several listings recently. Please wait a while before creating another.',
  update: 'You’ve saved changes too often recently. Please wait a while and try again.',
  publish: 'You’ve published several listings recently. Please wait a while and try again.',
  archive: 'You’ve archived several listings recently. Please wait a while and try again.',
}

export function manageErrorMessage(error: unknown, action: ManageAction, hasFieldErrors = false): string {
  const saving = action === 'create' || action === 'update'
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return saving
          ? 'Unable to reach the server. Your listing wasn’t saved; check your connection and try again.'
          : 'Unable to reach the server. Check your connection and try again.'
      case 400:
        return hasFieldErrors
          ? 'Please correct the highlighted fields.'
          : 'Some details weren’t accepted. Check the form and try again.'
      case 403:
        return action === 'create'
          ? 'Your account is suspended, so you can’t create listings.'
          : 'Your account is suspended, so your listings can’t be changed.'
      case 404:
        return 'This listing doesn’t exist, or it isn’t one of yours.'
      case 409:
        return CONFLICT[action]
      case 429:
        return RATE_LIMITED[action]
    }
  }
  return saving
    ? 'Your listing wasn’t saved because something went wrong. Please try again.'
    : 'Something went wrong, so nothing was changed. Please try again.'
}
