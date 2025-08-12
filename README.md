# Ticketsystem

REST-API für Support-Tickets mit Rollen, festem Statusablauf, Fristen je Priorität, internen Notizen, Änderungsverlauf und Benachrichtigungen. Spring Boot 3.5, Spring Security, PostgreSQL, Flyway, Testcontainers.

## Rollen

| Rolle | darf |
|---|---|
| REPORTER | Tickets melden, eigene Tickets sehen und kommentieren, Lösung bestätigen oder Ticket wieder öffnen |
| AGENT | alle Tickets sehen, übernehmen, zuweisen, Priorität ändern, lösen, interne Notizen schreiben |
| ADMIN | wie AGENT, zusätzlich Tickets direkt schließen (Duplikat, Spam) und Benutzer anlegen |

Fremde Tickets sind für Meldende nicht nur gesperrt, sondern liefern `404`. Sonst ließe sich über `403` herausfinden, welche Ticketnummern existieren.

## Statusablauf

| von | nach | wer |
|---|---|---|
| OPEN | IN_PROGRESS | AGENT, ADMIN (übernimmt das Ticket automatisch, wenn noch niemand zugewiesen ist) |
| OPEN | CLOSED | ADMIN |
| IN_PROGRESS | WAITING | AGENT, ADMIN (Rückfrage an die meldende Person) |
| IN_PROGRESS, WAITING | RESOLVED | AGENT, ADMIN, nur mit Kommentar zur Lösung |
| WAITING | IN_PROGRESS | alle (Rückmeldung ist da) |
| RESOLVED | CLOSED | REPORTER, ADMIN |
| RESOLVED | IN_PROGRESS | alle (wieder geöffnet) |

Die Regeln stehen als Tabelle in `TicketWorkflow` statt verteilt in if-Abfragen. Ein Übergang, den es nicht gibt, liefert `409`, eine falsche Rolle `403`. Die Detailansicht eines Tickets enthält `nextStatuses`: die Übergänge, die der angemeldeten Person gerade offenstehen. Eine Oberfläche kann damit direkt die passenden Knöpfe anzeigen.

## Fristen (SLA)

| Priorität | zu lösen innerhalb |
|---|---|
| CRITICAL | 4 Stunden |
| HIGH | 1 Tag |
| MEDIUM | 3 Tage |
| LOW | 7 Tage |

Die Ticketliste ist nach Frist sortiert, überfällige Tickets sind markiert (`overdue`). Ändert sich die Priorität, wird die Frist ab Erstellung neu berechnet.

## Gleichzeitige Änderungen

Jedes Ticket hat eine Versionsnummer (`@Version`). Ändern zwei Personen dasselbe Ticket gleichzeitig, gewinnt die erste, und die zweite bekommt `409`, statt die erste Änderung still zu überschreiben. Zusätzlich können Clients die zuletzt gelesene Version mitschicken (`"version": 3`). Ist das Ticket inzwischen weiter, kommt ebenfalls `409`.

## Benachrichtigungen

Änderungen lösen ein Spring-Event aus. Der `NotificationListener` entscheidet, wer informiert wird: Meldende bei Status- und Prioritätsänderungen und neuen Kommentaren, Bearbeitende bei Zuweisung und Kommentaren. Bei internen Notizen erfährt die meldende Person nichts, und wer etwas selbst geändert hat, bekommt dazu keine Nachricht. Der Listener läuft in derselben Transaktion: Scheitert die Änderung, werden auch keine Benachrichtigungen gespeichert.

## Starten

Voraussetzungen: JDK 21 und Docker.

```bash
docker compose up -d        # PostgreSQL auf Port 5434
./gradlew bootRun           # Windows: .\gradlew.bat bootRun
```

API: http://localhost:8082, Swagger-UI: http://localhost:8082/swagger-ui.html (oben rechts "Authorize").

Beim ersten Start werden Demo-Benutzer angelegt, Passwort jeweils `demo123`: `anna`, `dora` (REPORTER), `ben`, `clara` (AGENT), `admin` (ADMIN). Abschalten mit `tickets.demo-users=false`.

```bash
curl -u anna:demo123 -X POST localhost:8082/api/tickets -H "Content-Type: application/json" \
     -d '{"title": "VPN trennt sich ständig", "description": "Seit gestern alle 10 Minuten.", "priority": "HIGH"}'

curl -u ben:demo123 -X PATCH localhost:8082/api/tickets/1/status -H "Content-Type: application/json" \
     -d '{"status": "IN_PROGRESS"}'

curl -u anna:demo123 localhost:8082/api/notifications
```

## API

| Methode | Pfad | Beschreibung |
|---|---|---|
| POST | `/api/tickets` | Ticket melden |
| GET | `/api/tickets?status=&priority=&assignedToMe=&page=&size=` | Liste, nach Frist sortiert |
| GET | `/api/tickets/{id}` | Details mit Kommentaren und `nextStatuses` |
| PATCH | `/api/tickets/{id}/status` | Status ändern |
| PATCH | `/api/tickets/{id}/assignee` | zuweisen |
| PATCH | `/api/tickets/{id}/priority` | Priorität ändern |
| POST | `/api/tickets/{id}/comments` | Kommentar oder interne Notiz |
| GET | `/api/tickets/{id}/history` | Verlauf |
| GET | `/api/notifications?unreadOnly=` | eigene Benachrichtigungen |
| POST | `/api/notifications/{id}/read` | als gelesen markieren |
| GET | `/api/users/me` | angemeldete Person |
| GET | `/api/users/staff` | Support-Team (für die Zuweisung) |
| POST | `/api/users` | Benutzer anlegen (ADMIN) |

## Tests

```bash
./gradlew test
```

- `TicketWorkflowTest`: Statusregeln als reiner Unit-Test ohne Spring
- `TicketApiTest`: Integrationstests mit den Demo-Benutzern gegen echtes PostgreSQL (Testcontainers, Docker muss laufen). Geprüft werden Anmeldung, kompletter Ablauf mit Verlauf und Benachrichtigungen, verbotene Übergänge, Sichtbarkeit fremder Tickets, interne Notizen, veraltete Versionen, Zuweisung, Filter und Admin-Rechte.

GitHub Actions führt alles bei jedem Push aus.

## Projektstruktur

```
src/main/java/dev/moghrabi/tickets/
  ticket/        Ticket, Workflow, Kommentare, Verlauf, Filter, Service, Controller
  notification/  Benachrichtigungen (Event-Listener und API)
  user/          Benutzer, Rollen, Anmeldung
  config/        Security, OpenAPI, Demo-Benutzer
  error/         Problem Details (RFC 9457)
src/main/resources/db/migration/   Flyway-Migrationen
```

## Bewusst weggelassen

- **Kein JWT oder OAuth.** HTTP Basic über HTTPS reicht für eine interne API und hält den Fokus auf der Fachlogik. Für eine echte Anwendung wäre ein Identity Provider (z. B. Keycloak) mit OAuth2 Resource Server der nächste Schritt.
- **Keine E-Mails.** Benachrichtigungen landen in der Datenbank und sind über die API abrufbar. Ein Mailversand könnte als zweiter Listener dazukommen.
