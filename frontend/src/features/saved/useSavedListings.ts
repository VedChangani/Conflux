import { useContext } from 'react'
import { SavedListingsContext, type SavedListingsContextValue } from './savedContext'

export function useSavedListings(): SavedListingsContextValue {
  const context = useContext(SavedListingsContext)
  if (!context) {
    throw new Error('useSavedListings must be used within a SavedListingsProvider.')
  }
  return context
}
