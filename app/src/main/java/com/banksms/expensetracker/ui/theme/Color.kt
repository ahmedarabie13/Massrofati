package com.banksms.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// --- Rizeq (رزق) Brand Colors — Nile, Gold & Papyrus ---
// A warm Egyptian palette: lapis-lazuli Nile blue, pharaoh gold, Nile-water
// teal, terracotta clay — on papyrus-cream surfaces (light) and warm ink
// charcoals (dark).

val RizeqLapis = Color(0xFF1E6FB8)          // Vivid lapis-lazuli Nile blue (light primary)
val RizeqLapisDeep = Color(0xFF14518C)        // Deep lapis (dark primary container)
val RizeqLapisLight = Color(0xFFD2E7FB)      // Pale lapis (light container)

val RizeqDahab = Color(0xFFC2911B)           // Egyptian pharaoh gold
val RizeqDahabDeep = Color(0xFF8F6B10)       // Darker gold for contrast
val RizeqDahabLight = Color(0xFFFBE9C2)      // Soft gold container

val RizeqNile = Color(0xFF0E9A6A)            // Nile-water teal (income / positive)
val RizeqNileBright = Color(0xFF3CBD8C)      // Bright nile for dark mode
val RizeqNileLight = Color(0xFFCBEADD)       // Pale nile container

val RizeqTerracotta = Color(0xFFC0522E)      // Terracotta clay (expense / error)
val RizeqTerracottaLight = Color(0xFFF8DED2) // Pale terracotta container

val RizeqAmethyst = Color(0xFF8B5CF6)        // Amethyst (occasional accents)

// --- Dark Mode Surfaces (Warm Nile Ink) ---
val ObsidianBlack = Color(0xFF14100A)         // Deep warm ink canvas
val MidnightSlate = Color(0xFF1E1810)          // Elevated card base surface
val CharcoalCard = Color(0xFF2A2317)           // Inner container & chip surface
val BorderDark = Color(0xFF3E3527)             // Hair-line crisp border
val TextPrimaryDark = Color(0xFFF6F1E6)
val TextSecondaryDark = Color(0xFFCDC2AE)
val TextMutedDark = Color(0xFF9C907B)

// --- Light Mode Surfaces (Warm Papyrus) ---
val AlabasterLight = Color(0xFFFAF6EC)         // Papyrus canvas
val PureWhite = Color(0xFFFFFCF5)              // Warm white surface
val SlateLight = Color(0xFFF1EAD9)             // Elevated chip container
val BorderLight = Color(0xFFE1D7C3)            // Crisp subtle light border
val TextPrimaryLight = Color(0xFF231B10)
val TextSecondaryLight = Color(0xFF5D513C)
val TextMutedLight = Color(0xFF978B76)

// --- Semantic Financial Indicators ---
val ExpenseCoral = Color(0xFFC3452B)           // Terracotta rose for spending
val ExpenseCoralLight = Color(0xFFFBEAE4)
val ExpenseCoralDark = Color(0xFF8F2E16)

val IncomeEmerald = Color(0xFF0E9A6A)          // Nile-teal green for income
val IncomeEmeraldLight = Color(0xFFE2F6EE)
val IncomeEmeraldDark = Color(0xFF08794F)

val TransferCyan = Color(0xFF3B82C4)           // Nile-blue for transfers
val TransferCyanLight = Color(0xFFEAF3FB)

// Backward-compatible aliases
val PrimaryBlue = RizeqLapis
val PrimaryBlueDark = RizeqLapisDeep
val SecondaryTeal = RizeqDahab
val TertiaryIndigo = RizeqNile

val ExpenseRed = ExpenseCoral
val ExpenseRedLight = ExpenseCoralLight
val ExpenseRedDark = ExpenseCoralDark

val IncomeGreen = IncomeEmerald
val IncomeGreenLight = IncomeEmeraldLight
val IncomeGreenDark = IncomeEmeraldDark

val TransferBlue = TransferCyan
val TransferBlueLight = TransferCyanLight

val NeutralSurfaceDark = ObsidianBlack
val NeutralSurfaceVariantDark = MidnightSlate
val NeutralCardDark = CharcoalCard

val NeutralSurfaceLight = AlabasterLight
val NeutralSurfaceVariantLight = SlateLight
val NeutralCardLight = PureWhite

// Category Colors - Harmonized for the Rizeq design system
val CategoryColors = mapOf(
    "Food & Dining" to Color(0xFFD97706),        // Spiced Tangerine
    "Shopping & Groceries" to Color(0xFFA855F7), // Modern Purple
    "Transportation" to Color(0xFF0E7490),       // Nile-teal Blue
    "Bills & Utilities" to Color(0xFFC2911B),    // Pharaoh Gold
    "Entertainment" to Color(0xFFC3452B),       // Terracotta Rose
    "Entertainment & Subscriptions" to Color(0xFFC3452B),
    "ATM / Cash" to Color(0xFF0E9A6A),           // Nile Water
    "Salary" to Color(0xFF0E9A6A),               // Nile Green
    "Income / Deposits" to Color(0xFF0E9A6A),    // Rizeq Nile
    "Health & Medical" to Color(0xFFDC2626),     // Crimson Red
    "Travel & Flights" to Color(0xFF0284C7),     // Sky Blue
    "Transfers" to Color(0xFF3B82C4),            // Nile Blue
    "General" to Color(0xFF8A7B63)               // Warm Sand
)

// --- Dribbble Expense Tracker palette (shot 27020063) ---
// Clean white-card language: purple primary, coral/blue/green/orange chart
// accents, near-black ink text on a light-grey page.
val DribbblePurple = Color(0xFF7C6CF5)
val DribbblePurpleDeep = Color(0xFF6A58F0)
val DribbblePurpleLight = Color(0xFFB9AEFF)
val DribbblePurplePale = Color(0xFFEFEBFF)
val DribbbleHeaderTop = Color(0xFFA58FFF)
val DribbbleHeaderBottom = Color(0xFF755BF2)
val DribbbleCoral = Color(0xFFFF6A5B)
val DribbbleCoralPale = Color(0xFFFFE9E4)
val DribbbleBlue = Color(0xFF2FA8FF)
val DribbbleBluePale = Color(0xFFE4F3FF)
val DribbbleGreen = Color(0xFF2FCC71)
val DribbbleGreenPale = Color(0xFFE1F7EA)
val DribbbleOrange = Color(0xFFFF8A1E)
val DribbbleOrangePale = Color(0xFFFFEDDA)
val DribbbleAmountRed = Color(0xFFF43F5E)
val DribbbleInk = Color(0xFF141414)
val DribbbleGray = Color(0xFF8E8E93)
val DribbblePage = Color(0xFFF6F6F8)
val DribbbleBorder = Color(0xFFE9E9EC)

// Fallback slice palette for charts (category colors take precedence).
val DribbbleDonutPalette = listOf(
    DribbblePurple,
    DribbbleBlue,
    DribbbleGreen,
    DribbbleOrange,
    DribbbleCoral,
    DribbblePurpleLight,
    Color(0xFF7CD4FD),
    Color(0xFF98E6B8)
)