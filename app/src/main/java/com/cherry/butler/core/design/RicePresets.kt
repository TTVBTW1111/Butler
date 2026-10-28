package com.cherry.butler.core.design

/**
 * One-tap looks from the ricing world (fork feature): each fills the Custom look with its
 * palette's own colours, so every colour stays editable under Custom colours afterwards.
 * Keys are [CustomToken.key].
 */
data class RicePreset(val name: String, val blurb: String, val colors: CustomColors)

object RicePresets {
    private fun preset(
        name: String, blurb: String, accent: Long, ground: Long,
        chrome: Long, cards: Long, raised: Long, lines: Long, faint: Long,
        text: Long, textMed: Long, textLow: Long, onAccent: Long,
        danger: Long, success: Long, warn: Long, speech: Long, thought: Long,
        corners: Float = 1f,
    ) = RicePreset(
        name, blurb,
        CustomColors(
            accent = accent, ground = ground, corners = corners,
            overrides = mapOf(
                "chrome" to chrome, "cards" to cards, "raised" to raised, "card_edges" to lines,
                "lines" to lines, "faint_lines" to faint,
                "text" to text, "text_med" to textMed, "text_low" to textLow, "on_accent" to onAccent,
                "danger" to danger, "success" to success, "warn" to warn,
                "speech" to speech, "thought" to thought,
            ),
        ),
    )

    val all: List<RicePreset> = listOf(
        preset("Catppuccin Mocha", "Soft pastels on deep purple-grey",
            accent = 0xFFCBA6F7, ground = 0xFF1E1E2E, chrome = 0xFF181825, cards = 0xFF313244, raised = 0xFF45475A,
            lines = 0xFF45475A, faint = 0xFF313244, text = 0xFFCDD6F4, textMed = 0xFFBAC2DE, textLow = 0xFF7F849C,
            onAccent = 0xFF1E1E2E, danger = 0xFFF38BA8, success = 0xFFA6E3A1, warn = 0xFFF9E2AF, speech = 0xFF89B4FA, thought = 0xFFF5C2E7, corners = 1.4f),
        preset("Nord", "Arctic blues, calm and cold",
            accent = 0xFF88C0D0, ground = 0xFF2E3440, chrome = 0xFF272C36, cards = 0xFF3B4252, raised = 0xFF434C5E,
            lines = 0xFF4C566A, faint = 0xFF3B4252, text = 0xFFECEFF4, textMed = 0xFFD8DEE9, textLow = 0xFF8891A4,
            onAccent = 0xFF2E3440, danger = 0xFFBF616A, success = 0xFFA3BE8C, warn = 0xFFEBCB8B, speech = 0xFF81A1C1, thought = 0xFFB48EAD),
        preset("Dracula", "Neon purple and pink on night",
            accent = 0xFFBD93F9, ground = 0xFF282A36, chrome = 0xFF21222C, cards = 0xFF343746, raised = 0xFF44475A,
            lines = 0xFF44475A, faint = 0xFF343746, text = 0xFFF8F8F2, textMed = 0xFFD6D6CF, textLow = 0xFF6272A4,
            onAccent = 0xFF282A36, danger = 0xFFFF5555, success = 0xFF50FA7B, warn = 0xFFF1FA8C, speech = 0xFF8BE9FD, thought = 0xFFFF79C6),
        preset("Gruvbox", "Warm retro browns and orange",
            accent = 0xFFFE8019, ground = 0xFF282828, chrome = 0xFF1D2021, cards = 0xFF3C3836, raised = 0xFF504945,
            lines = 0xFF504945, faint = 0xFF3C3836, text = 0xFFEBDBB2, textMed = 0xFFD5C4A1, textLow = 0xFF928374,
            onAccent = 0xFF282828, danger = 0xFFFB4934, success = 0xFFB8BB26, warn = 0xFFFABD2F, speech = 0xFF83A598, thought = 0xFFD3869B, corners = 0.5f),
        preset("Tokyo Night", "City-lights blue on midnight",
            accent = 0xFF7AA2F7, ground = 0xFF1A1B26, chrome = 0xFF16161E, cards = 0xFF24283B, raised = 0xFF2F334D,
            lines = 0xFF3B4261, faint = 0xFF24283B, text = 0xFFC0CAF5, textMed = 0xFFA9B1D6, textLow = 0xFF565F89,
            onAccent = 0xFF1A1B26, danger = 0xFFF7768E, success = 0xFF9ECE6A, warn = 0xFFE0AF68, speech = 0xFF7DCFFF, thought = 0xFFBB9AF7),
        preset("Rosé Pine", "Muted rose and gold, all natural",
            accent = 0xFFEBBCBA, ground = 0xFF191724, chrome = 0xFF13111E, cards = 0xFF1F1D2E, raised = 0xFF26233A,
            lines = 0xFF403D52, faint = 0xFF26233A, text = 0xFFE0DEF4, textMed = 0xFFC4C0DF, textLow = 0xFF6E6A86,
            onAccent = 0xFF191724, danger = 0xFFEB6F92, success = 0xFF9CCFD8, warn = 0xFFF6C177, speech = 0xFF9CCFD8, thought = 0xFFC4A7E7, corners = 1.6f),
        preset("Everforest", "Forest greens, easy on the eyes",
            accent = 0xFFA7C080, ground = 0xFF2D353B, chrome = 0xFF232A2E, cards = 0xFF343F44, raised = 0xFF3D484D,
            lines = 0xFF475258, faint = 0xFF343F44, text = 0xFFD3C6AA, textMed = 0xFFB9AE95, textLow = 0xFF859289,
            onAccent = 0xFF2D353B, danger = 0xFFE67E80, success = 0xFFA7C080, warn = 0xFFDBBC7F, speech = 0xFF7FBBB3, thought = 0xFFD699B6),
    )
}
