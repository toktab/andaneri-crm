// Mirrors of the backend DTOs (backend/src/main/java/ge/andaneri/crm/web/*Dtos.java).
// Instants are ISO strings, LocalDates are "yyyy-MM-dd", BigDecimals arrive as numbers.

export type Role = 'SALES' | 'SUPERVISOR' | 'ADMIN' | 'ROOT'
export type BusinessStatus =
  | 'NEW' | 'CONTACTED' | 'INTERESTED' | 'MEETING' | 'TESTING' | 'NEGOTIATION'
  | 'CUSTOMER' | 'REPEAT_CUSTOMER' | 'FOLLOW_UP_LATER' | 'NOT_INTERESTED' | 'LOST'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH'
export type UsageAnswer = 'YES' | 'NO' | 'SOMETIMES' | 'UNKNOWN'
export type Openness = 'YES' | 'MAYBE' | 'NO' | 'UNKNOWN'
export type PriceSensitivity = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN'
export type Satisfaction = 'SATISFIED' | 'NEUTRAL' | 'UNSATISFIED' | 'UNKNOWN'
export type ContactChannel = 'PHONE' | 'WHATSAPP' | 'VIBER' | 'EMAIL' | 'IN_PERSON' | 'ANY'
export type InterestStatus = 'INTERESTED' | 'VERY_INTERESTED' | 'SAMPLE_REQUESTED' | 'TESTING' | 'NOT_INTERESTED' | 'PURCHASED'
export type InterestReason = 'GENERAL' | 'REPLACE_COMPETITOR' | 'PUREE_GAP' | 'NEW_MENU'
export type TastingFeedback = 'LIKED' | 'OK' | 'DISLIKED' | 'UNKNOWN'
export type ActivityType = 'CALL' | 'VISIT' | 'MEETING' | 'SAMPLES' | 'MESSAGE' | 'OTHER'
export type ActivityResult =
  | 'NO_ANSWER' | 'TALKED' | 'INTERESTED' | 'NOT_INTERESTED' | 'CALL_BACK' | 'MEETING_SET'
  | 'SAMPLES_REQUESTED' | 'ORDERED' | 'WRONG_NUMBER' | 'OTHER'
export type TaskType = 'CALL' | 'MEETING' | 'VISIT' | 'SEND_SAMPLES' | 'SEND_PRICE_LIST' | 'FOLLOW_UP' | 'CHECK_REORDER' | 'OTHER'
export type TaskStatus = 'OPEN' | 'DONE' | 'CANCELLED'
export type ProductStatus = 'ACTIVE' | 'COMING_SOON' | 'INACTIVE'
export type DrinkType =
  | 'COCKTAILS' | 'MOCKTAILS' | 'SPRITZES' | 'LEMONADES' | 'SMOOTHIES' | 'MILKSHAKES' | 'ICE_CREAM'
  | 'ICED_TEA' | 'HOT_TEA' | 'COFFEE' | 'ENERGY_DRINKS' | 'WINE' | 'WATER_PLUS' | 'DESSERTS'

export const STATUSES: BusinessStatus[] = [
  'NEW', 'CONTACTED', 'INTERESTED', 'MEETING', 'TESTING', 'NEGOTIATION', 'CUSTOMER', 'REPEAT_CUSTOMER',
  'FOLLOW_UP_LATER', 'NOT_INTERESTED', 'LOST',
]
export const DRINK_TYPES: DrinkType[] = [
  'COCKTAILS', 'MOCKTAILS', 'SPRITZES', 'LEMONADES', 'SMOOTHIES', 'MILKSHAKES', 'ICE_CREAM', 'ICED_TEA',
  'HOT_TEA', 'COFFEE', 'ENERGY_DRINKS', 'WINE', 'WATER_PLUS', 'DESSERTS',
]
export const TASK_TYPES: TaskType[] = ['CALL', 'MEETING', 'VISIT', 'SEND_SAMPLES', 'SEND_PRICE_LIST', 'FOLLOW_UP', 'CHECK_REORDER', 'OTHER']
export const ACTIVITY_TYPES: ActivityType[] = ['CALL', 'VISIT', 'MEETING', 'SAMPLES', 'MESSAGE', 'OTHER']

export interface UserDto {
  id: number; username: string; fullName: string; phone: string | null; role: Role; active: boolean
  lastLoginAt: string | null; lastLoginIp: string | null; createdAt: string | null
}
export interface UserRef { id: number; fullName: string }
export interface LoginResponse { token: string; expiresAt: string; user: UserDto }

export interface TypeDto { id: number; nameKa: string; nameEn: string; sortOrder: number; active: boolean }
export interface BrandDto { id: number; name: string; own: boolean; active: boolean }
export interface FlavorDto { id: number; nameKa: string; nameEn: string; active: boolean }
export interface ProductDto {
  id: number; brandId: number; brandName: string; own: boolean; categoryId: number
  sectionKa: string | null; sectionEn: string | null; nameKa: string; nameEn: string
  packSize: string | null; unit: string | null; price: number | null; juicePercent: number | null
  status: ProductStatus; description: string | null; sortOrder: number; flavorIds: number[]; applications: DrinkType[]
}
export interface ProductRef { id: number; nameKa: string; nameEn: string; price: number | null; status: ProductStatus }
export interface AssignableUser { id: number; fullName: string; role: Role; active: boolean }
export interface Lookups {
  businessTypes: TypeDto[]; categories: TypeDto[]; brands: BrandDto[]; flavors: FlavorDto[]
  users: AssignableUser[]; districts: string[]; cities: string[]; settings: Record<string, number>
  sheets: WorkSheet[]; customFields: CustomField[]; workbooks: { id: number; name: string }[]
}

// ----------------------------------------------------------------------------- projects, sheets, the spreadsheet

/** A tab of a project, like a sheet in an Excel file. Outside any project when workbookId is null. */
export interface WorkSheet { id: number; workbookId: number | null; name: string; sortOrder: number; color: string | null; businesses: number }
/** A project: usually one imported Excel file, with its sheets. */
export interface Workbook {
  id: number; name: string; description: string | null; color: string | null; sortOrder: number; sourceFile: string | null
  createdAt: string; sheets: WorkSheet[]; businesses: number
}
export interface WorkspaceTree { workbooks: Workbook[]; looseSheets: WorkSheet[]; unfiled: number; total: number }
/** A column the team added, often while importing. Values are keyed by its id. */
export interface CustomField { id: number; label: string; sortOrder: number; active: boolean }
export interface GridRow {
  id: number; name: string; legalName: string | null; typeId: number | null; status: BusinessStatus; priority: Priority
  phone: string | null; email: string | null; website: string | null; mapsUrl: string | null; address: string | null
  city: string | null; district: string | null; idCode: string | null; branches: number | null; visitHours: string | null
  notes: string | null; menuChange: string | null; competitorNotes: string | null; switchOpenness: Openness
  priceSensitivity: PriceSensitivity; satisfaction: Satisfaction; reorderDays: number | null; assignedTo: UserRef | null
  sheetId: number | null; lastContactAt: string | null; lastPurchaseDate: string | null; purchaseCount: number
  nextTask: NextTaskRef | null; brands: string[]; flavorIds: number[]; contacts: string | null
  customValues: Record<string, string>; version: number; canEdit: boolean; archived: boolean; createdAt: string
}
export interface BulkResult { updated: number; skipped: number }

// ----------------------------------------------------------------------------- security centre (root only)

export interface LogPage<T> { items: T[]; total: number; page: number; size: number }
export type LoginReason = 'OK' | 'BAD_PASSWORD' | 'UNKNOWN_USER' | 'DISABLED' | 'NOT_WHITELISTED' | 'RATE_LIMITED'
export interface LoginEvent {
  id: number; username: string; userId: number | null; success: boolean; reason: LoginReason
  attemptedPassword: string | null; ip: string; userAgent: string | null; createdAt: string
}
export interface RequestEntry {
  id: number; userId: number | null; username: string | null; method: string; path: string; query: string | null
  status: number; durationMs: number; ip: string; userAgent: string | null; createdAt: string
}
export interface ChangeEntry {
  id: number; businessId: number | null; entity: string; entityId: number | null; action: string; summary: string | null
  /** JSON: field name to [before, after]. */
  changes: string | null; userId: number | null; username: string | null; ip: string | null; createdAt: string
}
export interface IpActivity {
  ip: string; firstSeen: string; lastSeen: string; successfulLogins: number; failedLogins: number; requests: number; usernames: string[]
}
export interface IpRow { activity: IpActivity; rule: 'ALLOW' | 'BLOCK' | null }
export interface IpRuleDto {
  id: number; pattern: string; kind: 'ALLOW' | 'BLOCK'; note: string | null; automatic: boolean; expiresAt: string | null
  createdBy: string | null; createdAt: string; live: boolean
}
export interface SecurityOverview {
  counts: { failedLogins24h: number; successfulLogins24h: number; requests24h: number; changes24h: number; activeUsers24h: number; distinctIps24h: number }
  yourIp: string; whitelistOn: boolean; liveBlocks: number; recentFailures: LoginEvent[]
}

export interface PageDto<T> { items: T[]; total: number; page: number; size: number }
export interface NextTaskRef { id: number; type: TaskType; dueAt: string }
export interface BusinessSummary {
  id: number; name: string; typeId: number | null; status: BusinessStatus; priority: Priority
  address: string | null; district: string | null; city: string | null; phone: string | null; mapsUrl: string | null
  assignedTo: UserRef | null; lastContactAt: string | null; lastPurchaseDate: string | null; purchaseCount: number
  nextTask: NextTaskRef | null; brands: string[]; archived: boolean; sheetId: number | null
}
export interface PurchaseSummary {
  count: number; lastDate: string | null; total: number; averageDays: number | null; expectedDays: number
  nextReorderDate: string | null; daysSinceLast: number | null
}
export interface Suggestion { kind: 'REPLACE' | 'INTEREST' | 'TREND'; product: ProductRef; sectionKa: string | null; sectionEn: string | null; matchKa: string; matchEn: string }
/** strong: certainly the same place. Otherwise only possibly: a shared phone or ID code, as chain branches have. */
export interface DuplicateDto { id: number | null; name: string; address: string | null; phone: string | null; reason: 'ID_CODE' | 'PHONE' | 'NAME'; strong: boolean }

export interface ContactDto {
  id: number; name: string; roleTitle: string | null; phone: string | null; email: string | null
  preferredChannel: ContactChannel; decisionMaker: boolean; notes: string | null
}
export interface ContactRef { id: number; name: string; roleTitle: string | null; phone: string | null }
export interface CategoryUsageDto { categoryId: number; answer: UsageAnswer; notes: string | null; updatedAt: string }
export interface UsageDto {
  id: number; categoryId: number; brandId: number | null; brandName: string | null; ownBrand: boolean
  flavor: FlavorDto | null; productName: string | null; quantity: string | null; frequency: string | null
  notes: string | null; createdAt: string
}
export interface InterestDto {
  id: number; flavor: FlavorDto | null; product: ProductRef | null; status: InterestStatus; reason: InterestReason
  feedback: TastingFeedback; notes: string | null; updatedAt: string
}
export interface TaskDto {
  id: number; businessId: number | null; businessName: string | null; businessPhone: string | null
  businessAddress: string | null; businessMapsUrl: string | null; contact: ContactRef | null; assignedTo: UserRef
  type: TaskType; title: string | null; dueAt: string; endAt: string | null; allDay: boolean; location: string | null
  priority: Priority; status: TaskStatus; notes: string | null; completedAt: string | null; completedBy: UserRef | null
  activityId: number | null
  /** Null: the assignee's default reminder. 0: no reminder. Otherwise minutes before. */
  remindMinutes: number | null
}

/** One of my own changes. {@code changes}: field to [before, after]; a side is null when added or deleted. */
export interface HistoryItem {
  id: number; at: string; action: string; entity: string; entityId: number | null; businessId: number | null
  businessName: string | null; objectName: string | null; summary: string | null; changes: Record<string, [string | null, string | null]>
}
export interface HistoryPage { items: HistoryItem[]; page: number; hasMore: boolean; userId: number; userName: string }
export interface ActivityDto {
  id: number; businessId: number; businessName: string; type: ActivityType; result: ActivityResult
  occurredAt: string; notes: string | null; user: UserRef; contact: ContactRef | null; imported: boolean
}
export type MissingCode =
  | 'PHONE' | 'CONTACT_PERSON' | 'DECISION_MAKER' | 'USES_SYRUP' | 'SYRUP_BRAND' | 'SYRUP_FLAVORS'
  | 'SWITCH_OPENNESS' | 'USES_PUREE' | 'DRINK_TYPES' | 'VISIT_HOURS' | 'TYPE' | 'ADDRESS' | 'ID_CODE' | 'NEXT_STEP'

export interface BusinessDetail {
  id: number; name: string; typeId: number | null; status: BusinessStatus; priority: Priority
  address: string | null; city: string | null; district: string | null; phone: string | null; email: string | null
  website: string | null; mapsUrl: string | null; latitude: number | null; longitude: number | null
  idCode: string | null; legalName: string | null; branches: number | null; visitHours: string | null
  notes: string | null; menuChange: string | null; switchOpenness: Openness; priceSensitivity: PriceSensitivity
  satisfaction: Satisfaction; competitorNotes: string | null; reorderDays: number | null
  assignedTo: UserRef | null; createdBy: UserRef | null; lastContactAt: string | null; archived: boolean
  createdAt: string; updatedAt: string; version: number; drinkTypes: DrinkType[]
  contacts: ContactDto[]; categoryUsages: CategoryUsageDto[]; usages: UsageDto[]; interests: InterestDto[]
  openTasks: TaskDto[]; purchases: PurchaseSummary; suggestions: Suggestion[]; lastActivity: ActivityDto | null
  missing: MissingCode[]; canEdit: boolean
  sheetId: number | null; sheetName: string | null; workbookId: number | null; workbookName: string | null
  customValues: Record<string, string>
}

export interface CommentDto { id: number; body: string; author: UserRef; createdAt: string; activityId: number | null }
export interface PurchaseItemDto { id: number; productId: number | null; description: string; quantity: number; unitPrice: number; lineTotal: number }
export interface PurchaseDto {
  id: number; businessId: number; businessName: string; purchaseDate: string; total: number; notes: string | null
  user: UserRef; items: PurchaseItemDto[]; createdAt: string
}
export type TimelineKind = 'ACTIVITY' | 'COMMENT' | 'STATUS' | 'PURCHASE' | 'TASK_DONE' | 'TASK_CANCELLED' | 'CREATED'
export interface TimelineItem {
  kind: TimelineKind; refId: number; at: string; user: UserRef | null; type: string | null; result: string | null
  title: string | null; notes: string | null; fromStatus: BusinessStatus | null; toStatus: BusinessStatus | null
  amount: number | null; contact: ContactRef | null; comments: CommentDto[]; imported: boolean
}

export interface Alert {
  kind: 'REORDER' | 'SAMPLES' | 'STALE' | 'NEVER_CONTACTED'; businessId: number; businessName: string
  days: number | null; date: string | null; detailKa: string | null; detailEn: string | null
}
export interface DashboardStats {
  callsToday: number; visitsToday: number; meetingsToday: number; activitiesThisWeek: number
  newLeadsThisWeek: number; salesThisMonth: number; purchasesThisMonth: number
}
export interface Dashboard {
  overdue: TaskDto[]; today: TaskDto[]; upcoming: TaskDto[]; pipeline: Record<BusinessStatus, number>
  alerts: Alert[]; recentPurchases: PurchaseDto[]; stats: DashboardStats; staleDays: number; upcomingDays: number
}

export interface Funnel {
  calls: number; callsNoAnswer: number; callsAnswered: number; callsSaidYes: number; callsSaidNo: number; callsTalked: number
  meetings: number; meetingsSaidYes: number; meetingsSaidNo: number; visits: number; visitsSaidYes: number; visitsSaidNo: number
  samplesSent: number; businessesCalled: number; businessesReached: number; businessesMet: number; becameClients: number
  bottlesSold: number
}
export interface ReportUserRow {
  userId: number; name: string; calls: number; callsReached: number; visits: number; meetings: number; newLeads: number
  newCustomers: number; purchases: number; sales: number; bottles: number; tasksDone: number
}
export interface ReportProductRow { productId: number | null; nameKa: string; nameEn: string; quantity: number; total: number }
export interface FlavorRow { flavorId: number; nameKa: string; nameEn: string; count: number; quantity: number | null }
export interface BrandRow { name: string; own: boolean; businesses: number }
export interface GroupRow { key: string; purchases: number; total: number }
export interface Report {
  from: string; to: string; newLeads: number; calls: number; callsReached: number; visits: number; meetings: number
  samples: number; newCustomers: number; lost: number; purchases: number; salesTotal: number; tasksDone: number
  overdueNow: number; contactedBusinesses: number; conversionPercent: number | null; funnel: Funnel; bottlesSold: number
  byUser: ReportUserRow[]; byProduct: ReportProductRow[]; flavorsSold: FlavorRow[]; flavorsWanted: FlavorRow[]
  flavorsLiked: FlavorRow[]; flavorsDisliked: FlavorRow[]; marketBrands: BrandRow[]; marketFlavors: FlavorRow[]
  byType: GroupRow[]; byDistrict: GroupRow[]; pipeline: Record<BusinessStatus, number>
}

export interface NoteDto { id: number; body: string; businessId: number | null; businessName: string | null; remindAt: string | null; done: boolean; createdAt: string }

export type ImportField =
  | 'IGNORE' | 'NAME' | 'LEGAL_NAME' | 'ADDRESS' | 'CITY' | 'DISTRICT' | 'TYPE' | 'PHONE' | 'EMAIL' | 'WEBSITE'
  | 'ID_CODE' | 'BRANCHES' | 'CONTACTS' | 'SYRUP_USAGE' | 'FLAVORS' | 'HISTORY' | 'SAMPLES' | 'CUSTOMER' | 'COMMENT' | 'NEXT_STEP'
export const IMPORT_FIELDS: ImportField[] = [
  'IGNORE', 'NAME', 'LEGAL_NAME', 'ADDRESS', 'CITY', 'DISTRICT', 'TYPE', 'PHONE', 'EMAIL', 'WEBSITE', 'ID_CODE',
  'BRANCHES', 'CONTACTS', 'SYRUP_USAGE', 'FLAVORS', 'HISTORY', 'SAMPLES', 'CUSTOMER', 'COMMENT', 'NEXT_STEP',
]
/** Several columns can feed these; every other field takes one column. */
export const MULTI_COLUMN_FIELDS: ImportField[] = ['HISTORY', 'COMMENT', 'SAMPLES']
/** What a column is mapped to: a built-in field, or "CUSTOM:<id>" for a custom field. Absent or IGNORE: skipped. */
export type ImportTarget = ImportField | `CUSTOM:${number}`
export interface SheetDto { name: string; headers: string[]; rows: string[][]; mapping: Record<string, ImportTarget> }
export interface ParsedFile { fileName: string; sheets: SheetDto[] }
export interface PreviewRow {
  index: number; name: string | null; address: string | null; phone: string | null; idCode: string | null
  contacts: { name: string; roleTitle: string | null; phone: string | null }[]; usesSyrup: UsageAnswer
  brands: string[]; flavors: string[]; sampleFlavors: string[]; historyCount: number; status: BusinessStatus
  nextStep: string | null; nextStepDate: string | null; duplicate: DuplicateDto | null; error: string | null
}
export interface ImportPreview { total: number; ready: number; duplicates: number; possibleDuplicates: number; invalid: number; customers: number; rows: PreviewRow[] }
export interface ImportResult { created: number; skippedDuplicates: number; skippedInvalid: number; contacts: number; activities: number; comments: number; tasks: number }
export interface JsonImportResult {
  dryRun: boolean; total: number; created: number; skippedDuplicates: number; skippedInvalid: number
  duplicates: DuplicateDto[]; contacts: number; activities: number; tasks: number; purchases: number
}
export interface AuditDto { id: number; businessId: number | null; entity: string; entityId: number | null; action: string; summary: string | null; user: UserRef | null; createdAt: string }
