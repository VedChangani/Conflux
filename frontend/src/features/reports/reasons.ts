import type { ReportReason } from './types'

export const REASON_COPY: Record<ReportReason, { label: string; hint: string }> = {
  SPAM: { label: 'Spam', hint: 'Unwanted promotion or repetitive content.' },
  SCAM_OR_FRAUD: { label: 'Scam or fraud', hint: 'An attempt to deceive, or to get money or personal data.' },
  HARASSMENT: { label: 'Harassment', hint: 'Abuse, threats or targeted attacks.' },
  INAPPROPRIATE_CONTENT: { label: 'Inappropriate content', hint: 'Offensive, explicit or otherwise unsuitable material.' },
  MISLEADING_INFORMATION: {
    label: 'Misleading information',
    hint: 'False claims about a project, its progress or the people behind it.',
  },
  OTHER: { label: 'Something else', hint: 'Tell us more in the details below.' },
}
