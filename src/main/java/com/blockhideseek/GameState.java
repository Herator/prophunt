package com.blockhideseek;

public enum GameState {
    WAITING,     // No game running, waiting for start
    HIDING,      // Countdown phase - hiders are hiding, seekers are held
    PLAYING,     // Seekers released, game in progress
    ENDED        // Game over, showing results
}
