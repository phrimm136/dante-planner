export type {
  AnnouncementSpec,
  AnnouncementI18nEntry,
  AnnouncementI18n,
} from '../schemas/AnnouncementSchemas'

export interface Announcement {
  id: string
  date: string
  formattedDate: string
  title: string
  body: string
  permanent: boolean
}
