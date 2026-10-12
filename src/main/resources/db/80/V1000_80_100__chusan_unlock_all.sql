ALTER TABLE aqua_game_options
    ADD COLUMN chusan_unlock_music BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN chusan_unlock_collectables BIT(1) NOT NULL DEFAULT 0;
