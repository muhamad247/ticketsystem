package dev.moghrabi.tickets.user;

public enum Role {
    /** meldet Probleme, sieht nur eigene Tickets */
    REPORTER,
    /** Support: sieht alle Tickets, bearbeitet sie */
    AGENT,
    /** wie AGENT, darf zusätzlich Tickets direkt schließen und Benutzer anlegen */
    ADMIN;

    public boolean isStaff() {
        return this == AGENT || this == ADMIN;
    }
}
