package dev.inkhome;

/** Builds the current page using the activity-owned state and shell. */
interface Screen {
    void build(MainActivity host);
}
