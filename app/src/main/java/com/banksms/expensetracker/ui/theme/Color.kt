package com.banksms.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// --- Masari Luxury Brand Colors ---
val MasariEmerald = Color(0xFF00E599) // Vivid Neon Mint Emerald
val MasariEmeraldDark = Color(0xFF059669)
val MasariEmeraldLight = Color(0xFFA7F3D0)
val MasariMint = Color(0xFF10B981)
val MasariCyan = Color(0xFF38BDF8) // Electric Sky Cyan
val MasariCyanDark = Color(0xFF0284C7)
val MasariGold = Color(0xFFF59E0B) // Radiant Amber Gold
val MasariPurple = Color(0xFF8B5CF6) // Royal Violet

// --- Dark Mode Surfaces (Deep Obsidian & Midnight Slate) ---
val ObsidianBlack = Color(0xFF090D16) // Ultra-deep midnight canvas
val MidnightSlate = Color(0xFF111726) // Elevated card base surface
val CharcoalCard = Color(0xFF182236)  // Inner container & chip surface
val BorderDark = Color(0xFF24324A)    // Hair-line crisp border
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextMutedDark = Color(0xFF64748B)

// --- Light Mode Surfaces (Crisp Alabaster & Pure White) ---
val AlabasterLight = Color(0xFFF8FAFC) // Clean light canvas
val PureWhite = Color(0xFFFFFFFF)     // Crisp white surface
val SlateLight = Color(0xFFF1F5F9)    // Elevated chip container
val BorderLight = Color(0xFFE2E8F0)   // Crisp subtle light border
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextMutedLight = Color(0xFF94A3B8)

// --- Semantic Financial Indicators ---
val ExpenseCoral = Color(0xFFF43F5E) // Modern Rose Coral
val ExpenseCoralLight = Color(0xFFFFF1F2)
val ExpenseCoralDark = Color(0xFFBE123C)

val IncomeEmerald = Color(0xFF10B981) // Emerald Mint
val IncomeEmeraldLight = Color(0xFFECFDF5)
val IncomeEmeraldDark = Color(0xFF047857)

val TransferCyan = Color(0xFF0EA5E9)
val TransferCyanLight = Color(0xFFF0F9FF)

// Backward-compatible aliases
val PrimaryBlue = MasariEmerald
val PrimaryBlueDark = MasariEmeraldDark
val SecondaryTeal = MasariCyan
val TertiaryIndigo = MasariMint

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

// Category Colors - Harmonized for Masari Design System
val CategoryColors = mapOf(
    "Food & Dining" to Color(0xFFFB923C),        // Vibrant Tangerine
    "Shopping & Groceries" to Color(0xFFA855F7), // Modern Purple
    "Transportation" to Color(0xFF38BDF8),       // Sky Blue
    "Bills & Utilities" to Color(0xFFFBBF24),    // Amber Gold
    "Entertainment" to Color(0xFFF43F5E),       // Electric Rose
    "Entertainment & Subscriptions" to Color(0xFFF43F5E),
    "ATM / Cash" to Color(0xFF14B8A6),           // Teal Mint
    "Salary" to Color(0xFF10B981),               // Mint Green
    "Income / Deposits" to Color(0xFF00E599),    // Masari Emerald
    "Health & Medical" to Color(0xFFEF4444),     // Crimson Red
    "Travel & Flights" to Color(0xFF06B6D4),     // Ocean Blue
    "Transfers" to Color(0xFF6366F1),            // Indigo
    "General" to Color(0xFF64748B)               // Slate
)
