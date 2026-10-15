package com.bunnycode.battle.application;

/** Runs judging off the WebSocket inbound thread so a 10-second run never blocks other frames. */
public interface JudgeDispatcher {

    void dispatch(Runnable judging);
}
