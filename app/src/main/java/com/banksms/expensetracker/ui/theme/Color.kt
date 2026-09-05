package com.banksms.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// --- Masari Luxury Brand Colors ---
val MasariEmerald = Color(0xFF00D084)
val MasariEmeraldDark = Color(0xFF059669)
val MasariEmeraldLight = Color(0xFFA7F3D0)
val MasariMint = Color(0xFF10B981)
val MasariCyan = Color(0xFF06B6D4)
val MasariCyanDark = Color(0xFF0891B2)
val MasariGold = Color(0xFFF59E0B)

// --- Dark Mode Surfaces (Deep Obsidian & Midnight Slate) ---
val ObsidianBlack = Color(0xFF0B0F19)
val MidnightSlate = Color(0xFF111827)
val CharcoalCard = Color(0xFF1F2937)
val BorderDark = Color(0xFF374151)
val TextPrimaryDark = Color(0xFFF9FAFB)
val TextSecondaryDark = Color(0xFF9CA3AF)
val TextMutedDark = Color(0xFF6B7280)

// --- Light Mode Surfaces (Crisp Alabaster & Pure White) ---
val AlabasterLight = Color(0xFFF8FAFC)
val PureWhite = Color(0xFFFFFFFF)
val SlateLight = Color(0xFFF1F5F9)
val BorderLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextMutedLight = Color(0xFF94A3B8)

// --- Semantic Financial Indicators ---
val ExpenseCoral = Color(0xFFF43F5E)
val ExpenseCoralLight = Color(0xFFFFF1F2)
val ExpenseCoralDark = Color(0xFFBE123C)

val IncomeEmerald = Color(0xFF10B981)
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
    "ATM / Cash" to Color(0xFF14B8A6),           // Teal Mint
    "Salary" to Color(0xFF10B981),               // Mint Green
    "Income / Deposits" to Color(0xFF00D084),    // Masari Emerald
    "Health & Medical" to Color(0xFFEF4444),     // Crimson Red
    "Transfers" to Color(0xFF6366F1),            // Indigo
    "General" to Color(0xFF64748B)               // Slate
)
