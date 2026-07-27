package com.example.game

/**
 * Match Flow State Machine.
 */
enum class GameState {
    TITLE_SCREEN,
    MODE_SELECT,
    CINEMATIC_INTRO,
    ACTIVE_FIGHTING,
    ROUND_END,
    MATCH_END,
    PAUSED
}

enum class GameMode {
    VS_AI
}
