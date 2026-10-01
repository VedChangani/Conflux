import { formatDateTime } from '../../lib/dates'
import { ReportButton } from '../reports/ReportButton'
import type { Message } from './types'

function excerpt(content: string): string {
  const text = content.replace(/\s+/g, ' ').trim()
  return text.length > 90 ? `${text.slice(0, 90).trimEnd()}…` : text
}

export function MessageItem({ message, own }: { message: Message; own: boolean }) {
  return (
    <li className="message" data-own={own}>
      <div className="message-bubble">
        <p className="message-meta">
          <span className="message-sender">{message.sender.displayName}</span>
          {own && <span className="you-tag">You</span>}
          <time className="message-time" dateTime={message.createdAt}>
            {formatDateTime(message.createdAt)}
          </time>
          {!own && (
            <ReportButton
              compact
              className="message-report"
              target={{
                type: 'MESSAGE',
                id: message.id,
                description: `Message from ${message.sender.displayName}: “${excerpt(message.content)}”`,
              }}
              accessibleLabel={`Report message from ${message.sender.displayName}`}
            />
          )}
        </p>
        <p className="message-content">{message.content}</p>
      </div>
    </li>
  )
}
