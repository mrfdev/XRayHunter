package com.onemb.cmiapi.xrayhunter.coreprotect;

/** CoreProtect database action identifiers used by the active lookup implementation. */
enum CoreProtectAction {
    BREAK(0),
    PLACE(1);

    private final int id;

    CoreProtectAction(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }
}
