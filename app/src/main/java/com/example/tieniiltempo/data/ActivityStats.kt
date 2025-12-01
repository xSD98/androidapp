package com.example.tieniiltempo.data

data class ActivityStats(
    val planned: Int = 0,    // da iniziare (status = PLANNED)
    val running: Int = 0,    // in corso    (status = RUNNING)
    val done: Int = 0,       // concluse    (status = DONE)
    val avgCompletionMinutes: Int = 0 // media (solo attività con startedAt & completedAt)
)