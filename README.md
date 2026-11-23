# ds1 – Blog REST API

Quarkus-Applikation mit einem Blog-System (Blogs und Kommentare, 1:n) und einer REST-Schnittstelle,
abgesichert mit Keycloak und als Container-Image auf GitHub Packages veröffentlicht.

## Schnellstart: veröffentlichtes Image ausprobieren

Voraussetzungen: Docker und [HTTPie](https://httpie.io/cli). Die Ports `8080` (API) und `8180`
(Keycloak) müssen frei sein.

```shell script
git clone https://github.com/farisridzal-hftm/Distributed-Systems-1-Blog-Projekt.git
cd Distributed-Systems-1-Blog-Projekt
cp .env.example .env
docker compose -f docker-compose.app.yml up -d
```

Das startet das Backend-Image `ghcr.io/farisridzal-hftm/blog-backend`, Keycloak mit Realm und
Test-Benutzern sowie MySQL. Nach etwa einer Minute meldet `http :8080/q/health/ready` den Status
`UP`. Beispielaufrufe mit Token stehen unter
[Container-Image und Betrieb](#container-image-und-betrieb), das Swagger UI läuft unter
<http://localhost:8080/q/swagger-ui>.

## REST-Design

Basis-URL: `http://localhost:8080`
Alle Endpunkte liefern und erwarten `application/json` (Ausnahme: `GET /hello`, `text/plain`).

### Ressourcen

Das Blog ist die Hauptressource. Kommentare sind eine Unterressource eines Blogs und haben
deshalb keinen eigenen Top-Level-Pfad – ein Kommentar existiert nie ohne Blog.

| Methode  | Pfad                   | Erwartetes Verhalten                                          | Wer darf                              | Status (Erfolg)  | Status (Fehler)            |
| -------- | ---------------------- | ------------------------------------------------------------- | ------------------------------------- | ---------------- | -------------------------- |
| `GET`    | `/blogs`               | Liste aller Blogs, optional gefiltert, sortiert, begrenzt      | alle (öffentlich)                     | `200 OK`         | `400`                      |
| `GET`    | `/blogs/{id}`          | Einzelnes Blog                                                 | alle (öffentlich)                     | `200 OK`         | `404`                      |
| `POST`   | `/blogs`               | Neues Blog anlegen, `Location`-Header zeigt auf die Ressource  | `author`                              | `201 Created`    | `400`, `401`, `403`        |
| `PUT`    | `/blogs/{id}`          | Bestehendes Blog vollständig ersetzen (Titel + Inhalt)         | `author`, nur eigene Blogs            | `200 OK`         | `400`, `401`, `403`, `404` |
| `DELETE` | `/blogs/{id}`          | Blog samt seinen Kommentaren löschen                           | `author` (eigene) oder `admin` (alle) | `204 No Content` | `401`, `403`, `404`        |
| `GET`    | `/blogs/{id}/comments` | Alle Kommentare eines Blogs                                    | alle (öffentlich)                     | `200 OK`         | `404`                      |
| `POST`   | `/blogs/{id}/comments` | Kommentar zu einem Blog hinzufügen                             | `reader` (jeder angemeldete Benutzer) | `201 Created`    | `400`, `401`, `404`        |
| `GET`    | `/hello`               | Begrüssungstext (aus dem Projekt-Setup)                        | alle (öffentlich)                     | `200 OK`         | –                          |

Details und Begründungen stehen im [Berechtigungskonzept](#berechtigungskonzept), wie man es
ausprobiert unter [Auth-Setup testen](#auth-setup-testen). Die laufende API beschreibt sich
ausserdem selbst: OpenAPI unter <http://localhost:8080/q/openapi>, Swagger UI unter
<http://localhost:8080/q/swagger-ui> (Dev-Modus).

`PUT` wurde bewusst gegenüber `PATCH` gewählt: Ein Blog besteht nur aus Titel und Inhalt, ein
vollständiges Ersetzen ist damit einfacher und idempotent.

### Query- und Header-Parameter (`GET /blogs`)

| Parameter       | Typ    | Default | Bedeutung                                                                |
| --------------- | ------ | ------- | ------------------------------------------------------------------------ |
| `search`        | Query  | –       | Suche über Titel **und** Inhalt, Gross-/Kleinschreibung egal              |
| `sort`          | Query  | `id`    | Sortierfeld, erlaubt sind `id` und `title`; alles andere ergibt `400`     |
| `order`         | Query  | `asc`   | Sortierrichtung, erlaubt sind `asc` und `desc`; alles andere ergibt `400` |
| `X-Max-Results` | Header | –       | Maximale Anzahl zurückgegebener Blogs, muss positiv sein; sonst `400`     |

Beispiel: `GET /blogs?search=quarkus&sort=title&order=desc` mit Header `X-Max-Results: 5`

### Validierung (Bean Validation)

Die Eingaben werden mit Bean Validation geprüft, bevor sie den Service erreichen. Die Regeln
stehen auf den Request-DTOs (siehe [DTOs und Mapping](#dtos-und-mapping)), weil diese festlegen,
was ein Client schicken darf. Die Entitäten tragen dieselben Regeln als zweites Sicherheitsnetz
beim Speichern.

| Feld                     | Regel                            | Meldung                                   |
| ------------------------ | -------------------------------- | ----------------------------------------- |
| `BlogRequest.title`      | `@NotBlank`, `@Size(max = 120)`  | "Title must not be blank"                 |
| `BlogRequest.content`    | `@NotBlank`, `@Size(max = 4000)` | "Content must not be blank"               |
| `CommentRequest.text`    | `@NotBlank`, `@Size(max = 1000)` | "Text must not be blank"                  |
| `X-Max-Results` (Header) | `@Positive`                      | "X-Max-Results must be a positive number" |

Die Obergrenze für `content` entspricht der Spaltenlänge in der Datenbank. Vorher prüfte erst die
Datenbank die Länge; jetzt antwortet die API schon vorher mit einem sauberen `400`.

### DTOs und Mapping

Die REST-Schicht arbeitet nicht mit den JPA-Entitäten, sondern mit eigenen DTOs (Java-`record`s
im Paket `ch.hftm.rest.dto`). Die Entitäten verlassen `BlogResource` nie – was die API nach
aussen zeigt, ist damit unabhängig vom Datenbankmodell.

| DTO               | Verwendet bei                                                 | Gibt frei / nimmt an               | Bewusst **nicht** enthalten                                      |
| ----------------- | ------------------------------------------------------------- | ---------------------------------- | ---------------------------------------------------------------- |
| `BlogRequest`     | `POST /blogs`, `PUT /blogs/{id}`                              | `title`, `content`                 | `id` (vergibt der Server), Autor (kommt aus dem Token), Kommentare |
| `BlogResponse`    | `GET /blogs`, `GET /blogs/{id}`, Antwort auf `POST` und `PUT` | `id`, `title`, `content`, `author`, `createdAt` | Kommentarliste (eigene Unterressource), Keycloak-ID des Autors, JPA-Interna |
| `CommentRequest`  | `POST /blogs/{id}/comments`                                   | `text`                             | `id`, Blog (kommt aus dem Pfad), Autor (kommt aus dem Token)     |
| `CommentResponse` | `GET /blogs/{id}/comments`, Antwort auf `POST`                | `id`, `text`, `author`, `createdAt` | Rückverweis auf das Blog, Keycloak-ID des Autors                 |

Beispiel `POST /blogs`:

```jsonc
// Request (BlogRequest) von bob – mitgeschickte Felder "id" oder "author" werden ignoriert
{ "title": "Mein Blog", "content": "Hallo Welt" }

// Response 201 (BlogResponse) – "author" ist der Login-Name aus dem Token,
// "createdAt" setzt der Server beim Speichern (UTC)
{ "id": 3, "title": "Mein Blog", "content": "Hallo Welt", "author": "bob", "createdAt": "2026-11-23T20:30:12.345678Z" }
```

#### Wie das Mapping funktioniert

```text
Request:   JSON ──JSON-B──▶ BlogRequest ──@Valid──▶ BlogMapper.toEntity()   ──▶ Blog ──▶ BlogService ──▶ DB
Response:  JSON ◀──JSON-B── BlogResponse ◀────────── BlogMapper.toResponse() ◀── Blog ◀── BlogService
```

1. JSON-B deserialisiert den Body in ein Request-DTO. Felder, die das DTO nicht kennt (z. B. `id`),
   werden verworfen.
2. `@Valid` prüft die Regeln auf dem DTO. Bei einem Fehler antwortet der
   `ValidationExceptionMapper` mit `400`, der Service wird gar nicht aufgerufen.
3. `BlogMapper.toEntity(...)` baut daraus eine neue `Blog`-Entität. Die Service-Schicht arbeitet
   weiterhin nur mit Entitäten. Bei `PUT` dient diese Entität nur als Datenträger:
   `BlogService.updateBlog` übernimmt Titel und Inhalt in das bereits gespeicherte Blog.
4. Das Ergebnis des Service wandelt `BlogMapper.toResponse(...)` in ein Response-DTO um, das
   JSON-B serialisiert. Für Kommentare gibt es dafür `toResponse(BlogComment)`.

Das Mapping ist bewusst von Hand geschrieben (`ch.hftm.rest.dto.BlogMapper`, eine CDI-Bean) statt
mit einer Bibliothek wie MapStruct: Jedes Feld wird ausdrücklich kopiert. Ein neues Feld in einer
Entität erscheint erst dann in der API, wenn es jemand in DTO und Mapper ergänzt. Bei vier kleinen
DTOs ist das ausserdem übersichtlicher als eine zusätzliche Abhängigkeit.

Ein Beispiel ist der Autor: Die Entitäten speichern `authorId` (Keycloak-ID, Claim `sub`) und
`authorName` (Login-Name). Der Mapper gibt nur den Namen als `author` heraus – die ID braucht nur
der Server für die Besitzprüfung.

Weil keine Entität mehr serialisiert wird, sind die früheren `@JsonbTransient`-Annotationen auf
`Blog.getComments()` und `BlogComment.getBlog()` entfernt. Die Entitäten wissen nichts mehr von
JSON, und der Serializer kann keine Lazy-Relation mehr versehentlich nachladen.

### Fehler-Responses

Alle Fehler haben denselben Aufbau. Erzeugt wird er von den `ExceptionMapper`n in
`ch.hftm.rest.mapper`, damit der Client nicht raten muss, was schiefgelaufen ist.

Unbekanntes Blog (`404 Not Found`):

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Blog with id 42 not found"
}
```

Ungültige Eingabe (`400 Bad Request`) – zusätzlich mit Liste der betroffenen Felder:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "violations": [
    { "field": "title", "message": "Title must not be blank" }
  ]
}
```

Ungültiger Sortierparameter (`400 Bad Request`):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Unsupported sort field 'password', allowed values are [id, title]"
}
```

Kein oder ungültiges Token bei einem geschützten Endpunkt (`401 Unauthorized`): Diese Antwort
erzeugt Quarkus selbst, mit dem Header `WWW-Authenticate: Bearer`.

Angemeldet, aber nicht berechtigt (`403 Forbidden`) – Rolle fehlt bzw. fremdes Blog. Beide Fälle
laufen über den `ForbiddenExceptionMapper`:

```json
{ "status": 403, "error": "Forbidden", "message": "Missing required role" }
```

```json
{ "status": 403, "error": "Forbidden", "message": "Only the author may change blog 1" }
```

Body ist kein gültiges JSON (`400 Bad Request`, `InvalidJsonExceptionMapper`):

```json
{ "status": 400, "error": "Bad Request", "message": "Request body is not valid JSON" }
```

Unerwarteter Fehler (`500 Internal Server Error`, `UnexpectedExceptionMapper`). Der Stacktrace steht
nur im Log, der Client erfährt keine Interna:

```json
{ "status": 500, "error": "Internal Server Error", "message": "An unexpected error occurred" }
```

### Beispiele

Schreibende Aufrufe brauchen ein Token. Wie man es holt, steht unter
[Auth-Setup testen](#auth-setup-testen); hier liegt es in der Variable `$token` (PowerShell).

```powershell
# Blog anlegen (als author)
http POST :8080/blogs "Authorization:Bearer $token" title="Mein Blog" content="Hallo Welt"

# Suchen, sortieren, begrenzen (öffentlich)
http ":8080/blogs?search=hallo&sort=title&order=desc" X-Max-Results:5

# Eigenes Blog aktualisieren
http PUT :8080/blogs/1 "Authorization:Bearer $token" title="Neuer Titel" content="Neuer Inhalt"

# Ungültiger Request -> 400 mit Feldliste
http POST :8080/blogs "Authorization:Bearer $token" title="   " content="Inhalt ist ok"

# Kommentar hinzufügen (als reader)
http POST :8080/blogs/1/comments "Authorization:Bearer $token" text="Schöner Beitrag!"
```

### Aufbau im Code

| Schicht    | Paket                 | Aufgabe                                       |
| ---------- | --------------------- | --------------------------------------------- |
| REST       | `ch.hftm.rest`        | Pfade, Parameter, Statuscodes                 |
| DTOs       | `ch.hftm.rest.dto`    | Request-/Response-DTOs und `BlogMapper`       |
| Mapper     | `ch.hftm.rest.mapper` | Exceptions → einheitliche Fehler-Responses    |
| Security   | `ch.hftm.security`    | Rollen-Konstanten, `CurrentUser` aus dem Token |
| Service    | `ch.hftm.service`     | Fachlogik, Transaktionen, Sortier-/Suchregeln |
| Repository | `ch.hftm.repository`  | Panache-Datenzugriff                          |
| Model      | `ch.hftm.model`       | JPA-Entitäten inkl. Validierungsregeln        |
| Exceptions | `ch.hftm.exception`   | Fachliche Fehler (`BlogNotFoundException`, …) |
| Config     | `ch.hftm.config`      | Eigene Properties (`BlogConfig`, Präfix `blog`) |
| Migrationen | `src/main/resources/db/migration` | Flyway-Skripte `V1__…`, `V2__…` |
| Start      | `ch.hftm`             | `ApplicationLifecycle` (Start/Stopp-Log), `DataInitialization` (Startdaten) |

### Vorerst ausgeklammert

Bewusst noch nicht umgesetzt, um den Umfang klein zu halten:

- **`PATCH`**: Es gibt nur vollständige Updates via `PUT`.
- **Kommentare einzeln bearbeiten/löschen**: `PUT`/`DELETE` auf `/blogs/{id}/comments/{commentId}`
  fehlt noch; Kommentare verschwinden aktuell nur zusammen mit ihrem Blog.
- **Pagination**: Statt `page`/`size` gibt es vorerst nur den Header `X-Max-Results`.
- **Rate Limiting**: Angemeldete Benutzer können beliebig viele Kommentare schreiben (API6).

## CORS

Ein Browser-Frontend, das auf einem anderen Port oder einer anderen Domain läuft, darf die API
nur aufrufen, wenn der Server das per CORS erlaubt. Die Konfiguration steht in
`src/main/resources/application.properties`:

| Einstellung                              | Wert                                             | Grund                                                                                     |
| ---------------------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| `quarkus.http.cors.enabled`              | `true`                                           | CORS-Filter von Quarkus einschalten                                                       |
| `quarkus.http.cors.origins`              | `http://localhost:4200`, `http://localhost:5173` | Standard-Ports der Dev-Server von Angular und Vite                                         |
| `quarkus.http.cors.methods`              | `GET, POST, PUT, DELETE, OPTIONS`                | Genau die Methoden, die die API anbietet (`OPTIONS` für den Preflight)                     |
| `quarkus.http.cors.headers`              | `Content-Type, Accept, Authorization, X-Max-Results` | `Authorization` für das Token, `X-Max-Results` ist ein eigener Header. Ohne Freigabe blockiert der Browser den Preflight |
| `quarkus.http.cors.exposed-headers`      | `Location`                                       | Damit das Frontend nach einem `POST` die URL der neuen Ressource lesen kann               |
| `quarkus.http.cors.access-control-max-age` | `1H`                                           | Der Browser darf das Preflight-Ergebnis eine Stunde lang zwischenspeichern                |

Bewusst **kein** Wildcard `*`: Anfragen mit einer fremden `Origin` beantwortet Quarkus mit `403`
(geprüft in `CorsTest`).

Noch offen ist die produktive Origin. Es gibt noch kein Frontend und kein Deployment, deshalb ist
nur die lokale Entwicklung freigegeben. Sobald das Frontend eine feste URL hat, kommt diese in
`quarkus.http.cors.origins` dazu.

CORS schützt nur Benutzer im Browser. `curl`, Postman oder andere Server ignorieren CORS
vollständig – es ersetzt also keine Authentifizierung.

## Sicherheit: OWASP API Security Top 10 (2023)

Einschätzung, welche Risiken für diese Blog-API relevant sind und wie weit sie abgedeckt sind:

| Risiko                                                  | Relevanz         | Status             | Begründung                                                                                                                                                                                                                                                  |
| ------------------------------------------------------- | ---------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **API1** Broken Object Level Authorization              | hoch             | abgedeckt          | Blogs sind über fortlaufende IDs adressierbar (`/blogs/1`, `/blogs/2`, …). Jedes Blog hat einen Besitzer (`sub` aus dem Token); `BlogService` prüft bei `PUT`/`DELETE`, ob es dem Aufrufer gehört, sonst `403`. Getestet in `BlogSecurityTest` und `KeycloakTokenTest`. |
| **API2** Broken Authentication                          | hoch             | abgedeckt          | Kein eigener Login-Mechanismus: Keycloak stellt Tokens aus, `quarkus-oidc` prüft Signatur, Aussteller und Ablaufzeit. Ungültige Tokens ergeben `401`. Offen für den Betrieb: HTTPS, damit Tokens nicht mitgelesen werden können. |
| **API3** Broken Object Property Level Authorization     | hoch             | teilweise abgedeckt | Durch die DTOs: Responses geben nur freigegebene Felder heraus (keine Excessive Data Exposure), Requests nehmen nur `title`/`content`/`text` an, die `id` ist nicht setzbar (keine Mass Assignment, Test `clientCannotChooseTheIdOfANewBlog`). Felder mit rollenabhängiger Sichtbarkeit gibt es noch nicht. |
| **API4** Unrestricted Resource Consumption              | mittel           | teilweise abgedeckt | Längenlimits per Bean Validation (Titel 120, Inhalt 4000, Kommentar 1000 Zeichen), Sortierfelder per Whitelist. Offen: `GET /blogs` liefert ohne Header alle Blogs (keine Pagination), `X-Max-Results` hat keine Obergrenze, es gibt kein Rate Limiting.     |
| **API5** Broken Function Level Authorization            | hoch             | abgedeckt          | Jeder Endpunkt hat `@PermitAll` oder `@RolesAllowed`; mit `deny-unannotated-endpoints=true` ist ein Endpunkt ohne Annotation gesperrt. Fremde Blogs löschen darf nur `admin`. |
| **API6** Unrestricted Access to Sensitive Business Flows | mittel          | teilweise abgedeckt | Kommentieren ist genau das Beispiel aus der OWASP-Beschreibung. Es braucht jetzt ein Login, und jeder Kommentar ist einem Konto zugeordnet. Offen: Rate Limiting; ein angemeldeter Bot könnte weiterhin beliebig viele Kommentare posten. |
| **API7** Server Side Request Forgery                    | nicht relevant   | –                  | Die API ruft keine URLs auf, die von einem Client stammen.                                                                                                                                                                                                  |
| **API8** Security Misconfiguration                      | mittel           | teilweise abgedeckt | CORS mit expliziten Origins statt `*`; auch unerwartete Fehler liefern nur ein `500` ohne Stacktrace (`UnexpectedExceptionMapper`); Secrets kommen aus `.env` und das Image startet nicht ohne DB-Passwort; die Dev UI läuft nur im Dev-Modus. Bewusst offen: Das Swagger UI ist im Image enthalten, damit sich die API testen lässt. Offen: HTTPS, Security-Header und die produktive CORS-Origin. |
| **API9** Improper Inventory Management                  | niedrig          | teilweise abgedeckt | Alle Endpunkte sind in dieser README dokumentiert, die OpenAPI-Beschreibung (`/q/openapi`) wird aus dem Code erzeugt und zeigt pro Operation die nötigen Rollen. Offen: keine Versionierung (z. B. `/v1/blogs`), und der Demo-Endpunkt `/hello` ist noch aktiv. |
| **API10** Unsafe Consumption of APIs                    | nicht relevant   | –                  | Es werden keine Drittanbieter-APIs angebunden.                                                                                                                                                                                                              |

Fazit: Mit Keycloak und dem Berechtigungskonzept sind die drei Zugriffsrisiken API1, API2 und
API5 abgedeckt und automatisiert getestet. Dank der DTOs gibt die API keine internen Felder preis
und lässt weder `id` noch Autor von aussen setzen (API3). Offen bleiben vor allem
Ressourcen-Begrenzungen: Pagination und Rate Limiting (API4, API6).

## Berechtigungskonzept

Stand: umgesetzt mit `quarkus-oidc` und `@RolesAllowed`, siehe [Umsetzung im Code](#umsetzung-im-code).
Ausprobieren lässt es sich wie unter [Auth-Setup testen](#auth-setup-testen) beschrieben.

### Grundsätze

1. **Lesen ist öffentlich, Schreiben braucht ein Login.** Ein Blog lebt davon, dass man es ohne
   Konto lesen kann. Jede Änderung ist dagegen einer angemeldeten Person zugeordnet.
2. **Identität und Rollen kommen aus Keycloak.** Der Client schickt ein Access-Token
   (`Authorization: Bearer …`). Quarkus prüft Signatur und Ablaufzeit und liest die Rollen aus dem
   Claim `realm_access.roles`. Die API verwaltet selbst keine Passwörter.
3. **Die Rolle allein reicht nicht, es zählt auch der Besitz (API1).** Ein `author` darf nur die
   eigenen Blogs ändern und löschen. Dafür bekommt jedes Blog einen Besitzer: die Keycloak-ID des
   Benutzers (Claim `sub`) aus dem Token. Der Besitzer wird nie aus dem Request-Body übernommen,
   `BlogRequest` bekommt also kein Autor-Feld (sonst Mass Assignment, API3).
4. **Rollen sind nicht hierarchisch.** `admin` erbt nicht automatisch `author`. Wer moderiert und
   selbst schreibt, bekommt beide Rollen. So hat jede Rolle genau eine Aufgabe (Least Privilege).
5. **Standardmässig gesperrt.** Mit `quarkus.security.jaxrs.deny-unannotated-endpoints=true` ist
   jeder Endpunkt ohne Sicherheits-Annotation gesperrt. Ein neuer Endpunkt ist damit nie aus
   Versehen öffentlich (API5).

### Öffentliche Endpunkte (kein Login nötig)

| Methode   | Pfad                   | Begründung                                                      |
| --------- | ---------------------- | --------------------------------------------------------------- |
| `GET`     | `/blogs`               | Blogs sollen ohne Konto lesbar und durchsuchbar sein            |
| `GET`     | `/blogs/{id}`          | Einzelne Blogs werden verlinkt und geteilt                      |
| `GET`     | `/blogs/{id}/comments` | Die Diskussion gehört zum öffentlichen Inhalt des Blogs         |
| `GET`     | `/hello`               | Begrüssungstext ohne Daten                                      |
| `OPTIONS` | alle                   | CORS-Preflight – der Browser schickt dabei nie ein Token mit    |

Alle Blogs gelten derzeit als veröffentlicht. Kommt später ein Entwurfsstatus dazu, sieht einen
Entwurf nur die Person, die ihn verfasst hat.

### Rollen

| Rolle    | Für wen                                                                                         | Darf zusätzlich zum öffentlichen Lesen                               |
| -------- | ----------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `reader` | Jede registrierte Person. Standardrolle in Keycloak (`default-roles-blog`), wird automatisch vergeben | Kommentare schreiben                                                 |
| `author` | Personen, die Blogs veröffentlichen                                                             | Blogs erstellen, eigene Blogs ändern und löschen                     |
| `admin`  | Moderation                                                                                      | Jedes Blog löschen (Spam, Regelverstösse), später auch jeden Kommentar |

- **Kommentieren nur mit Login:** Anonyme Kommentare wären eine offene Tür für Spam-Bots (API6).
  Mit Login ist jeder Kommentar einer Person zugeordnet und kann gesperrt werden.
- **`admin` darf löschen, aber nicht umschreiben:** Ein Blog erscheint unter dem Namen der Person,
  die es geschrieben hat. Würde der Admin den Text ändern, stünde fremder Inhalt unter diesem Namen.
  Moderation heisst deshalb löschen, nicht ändern.

### Zugriffsregeln

Jede Spalte zeigt einen Benutzer mit genau dieser Rolle (plus der Standardrolle `reader`). Wer
mehrere Rollen hat, darf alles, was eine davon erlaubt.

| Methode  | Pfad                                         | anonym | `reader` | `author`             | `admin` | Umsetzung                                                 |
| -------- | -------------------------------------------- | ------ | -------- | -------------------- | ------- | --------------------------------------------------------- |
| `GET`    | `/blogs`, `/blogs/{id}`, `/blogs/{id}/comments` | ja  | ja       | ja                   | ja      | `@PermitAll`                                              |
| `GET`    | `/hello`                                     | ja     | ja       | ja                   | ja      | `@PermitAll`                                              |
| `POST`   | `/blogs/{id}/comments`                       | 401    | ja       | ja                   | ja      | `@RolesAllowed("reader")`                                 |
| `POST`   | `/blogs`                                     | 401    | 403      | ja                   | 403     | `@RolesAllowed("author")`                                 |
| `PUT`    | `/blogs/{id}`                                | 401    | 403      | nur eigene, sonst 403 | 403    | `@RolesAllowed("author")` + Besitzprüfung im Service       |
| `DELETE` | `/blogs/{id}`                                | 401    | 403      | nur eigene, sonst 403 | ja     | `@RolesAllowed({"author", "admin"})` + Besitzprüfung ausser für `admin` |
| `DELETE` | `/blogs/{id}/comments/{commentId}` (geplant) | 401    | nur eigene | nur eigene         | ja      | `@RolesAllowed({"reader", "admin"})` + Besitzprüfung      |

- **`401 Unauthorized`:** kein, abgelaufenes oder ungültiges Token – die API weiss nicht, wer
  anfragt.
- **`403 Forbidden`:** Das Token ist gültig, aber die Rolle fehlt oder das Blog gehört jemand
  anderem. Ein `404` zum Verstecken des Blogs wäre hier sinnlos, weil Blogs ohnehin öffentlich
  lesbar sind.

### Umsetzung im Code

- **Annotationen in `BlogResource`:** Jede Methode trägt `@PermitAll` oder `@RolesAllowed` wie in
  der Tabelle. Die Rollennamen stehen als Konstanten in `ch.hftm.security.Roles`.
  `quarkus.security.jaxrs.deny-unannotated-endpoints=true` sperrt jeden Endpunkt ohne Annotation.
- **Benutzer identifizieren (`ch.hftm.security.CurrentUser`):** eine `@RequestScoped`-Bean, die aus
  dem `JsonWebToken` die ID (`sub`) und den Login-Namen (`preferred_username`) liest und über die
  `SecurityIdentity` prüft, ob jemand `admin` ist.
- **Besitzprüfung in `BlogService`:** Beim Anlegen wird der angemeldete Benutzer Autor von Blog bzw.
  Kommentar. Beim Ändern und Löschen prüft der Service den Besitz:

  ```java
  var blog = getBlog(blogId);
  if (!currentUser.isAuthorOf(blog)) {
      throw new BlogAccessDeniedException("Only the author may change blog " + blogId);
  }
  ```

  `BlogAccessDeniedException` erweitert Quarkus' `ForbiddenException`. Der
  `ForbiddenExceptionMapper` macht daraus wie bei einer fehlenden Rolle ein `403` im einheitlichen
  Fehlerformat.
- **Startdaten:** Die zwei Blogs aus `DataInitialization` gehören `bob`. Weil die Benutzer-IDs im
  Realm fest sind, kann `bob` sie nach jedem Neustart bearbeiten.
- **CORS:** `Authorization` ist als Header freigegeben, sonst blockiert der Browser Requests mit
  Token.

## Auth-Setup testen

Keycloak stellt Benutzer, Rollen und Tokens aus, Quarkus prüft nur die Tokens (`quarkus-oidc`,
Bearer-Token). Keycloak lässt sich auf zwei Arten einbinden. Beide verwenden dieselbe Realm-Datei
`keycloak/blog-realm.json`, deshalb sind Benutzer, Passwörter, Rollen und sogar die Benutzer-IDs
identisch. Eine manuelle Konfiguration in der Keycloak-Admin-Konsole ist in keiner Variante nötig.

Voraussetzungen: Java 25, Docker (Desktop) und [HTTPie](https://httpie.io/cli).

|                      | Variante 1: Dev Services                   | Variante 2: eigener Container                                                            |
| -------------------- | ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| Start                | `./mvnw quarkus:dev`                       | `docker compose up -d`, danach `./mvnw quarkus:dev -Dquarkus.profile=external-keycloak` |
| Wer startet Keycloak | Quarkus, automatisch beim Start            | Docker Compose, unabhängig von Quarkus                                                   |
| Keycloak-Port        | `8280` (fest eingestellt für HTTPie)       | `8180`                                                                                   |
| Lebensdauer          | wird mit Quarkus beendet                   | läuft weiter bis `docker compose stop` bzw. `down`                                       |
| Gut für              | schnelles Entwickeln, automatisierte Tests | Setup wie im Betrieb, Keycloak überlebt Neustarts der App                                |

In PowerShell muss ein `-D`-Argument in Anführungszeichen stehen:
`./mvnw quarkus:dev "-Dquarkus.profile=external-keycloak"`. Die App läuft in beiden Fällen auf
<http://localhost:8080>; der Start dauert wegen der Container ein bis zwei Minuten.

### Wie die Varianten konfiguriert sind

Alles steht in `src/main/resources/application.properties`:

```properties
quarkus.oidc.client-id=blog-client

# Variante 1: Dev Services importieren dieselbe Realm-Datei
quarkus.keycloak.devservices.realm-path=keycloak/blog-realm.json
quarkus.keycloak.devservices.realm-name=blog
quarkus.keycloak.devservices.image-name=quay.io/keycloak/keycloak:26.8.1
quarkus.keycloak.devservices.start-command=start-dev
%dev.quarkus.keycloak.devservices.port=8280

# Variante 2: Ist auth-server-url gesetzt, startet Quarkus keinen eigenen Keycloak
%external-keycloak.quarkus.oidc.auth-server-url=http://localhost:8180/realms/blog
```

- **`start-command=start-dev`:** Ohne diese Zeile startet Quarkus Keycloak im Produktionsmodus.
  Der baut sein Server-Image bei jedem Start neu und war damit langsamer als das Start-Timeout, so
  dass Dev-Modus und Tests abbrachen.
- **Profil `external-keycloak`:** Ein eigenes Quarkus-Profil, das nur die URL des eigenen
  Keycloak setzt. MySQL kommt in beiden Varianten aus den Dev Services.

### Test-Benutzer

| Benutzer | Passwort | Rollen         | Darf                                                          |
| -------- | -------- | -------------- | ------------------------------------------------------------- |
| `alice`  | `alice`  | reader         | lesen, kommentieren                                           |
| `bob`    | `bob`    | reader, author | zusätzlich Blogs schreiben; die zwei Startblogs gehören `bob` |
| `carol`  | `carol`  | reader, author | wie `bob` – um zu prüfen, dass fremde Blogs gesperrt sind     |
| `admin`  | `admin`  | reader, admin  | lesen, kommentieren, jedes Blog löschen                       |

Die Passwörter sind nur für die lokale Entwicklung gedacht. Zusätzlich enthält jedes Token die
Keycloak-Standardrollen `default-roles-blog`, `offline_access` und `uma_authorization`.

### Token mit HTTPie holen

PowerShell – die Funktion holt ein Token per Password Grant, Passwort = Benutzername. Port `8280`
für Variante 1, `8180` für Variante 2:

```powershell
function Get-Token($user, $port = 8280) {
  (http --form POST ":$port/realms/blog/protocol/openid-connect/token" grant_type=password client_id=blog-client username=$user password=$user | ConvertFrom-Json).access_token
}
$bob = Get-Token bob; $carol = Get-Token carol; $alice = Get-Token alice; $admin = Get-Token admin
```

Bash (braucht [`jq`](https://jqlang.org/)):

```shell script
TOKEN=$(http --form POST :8280/realms/blog/protocol/openid-connect/token grant_type=password client_id=blog-client username=bob password=bob | jq -r .access_token)
```

Ein Token gilt 30 Minuten. Bei falschem Passwort antwortet Keycloak mit `400` und
`"error": "invalid_grant"` – so schreibt es OAuth 2.0 vor, nicht `401`.

### Beispielaufrufe mit HTTPie

Mit den Tokens von oben. Die Kommentare zeigen das erwartete Ergebnis, durchgespielt mit beiden
Varianten:

```powershell
http :8080/blogs                                                               # 200 öffentlich
http POST :8080/blogs title="Test" content="Ohne Token"                        # 401 kein Token
http POST :8080/blogs "Authorization:Bearer $alice" title="Test" content="X"   # 403 Rolle author fehlt
http POST :8080/blogs "Authorization:Bearer $bob" title="Von bob" content="X"  # 201 "author": "bob"
http PUT :8080/blogs/1 "Authorization:Bearer $bob" title="Neu" content="X"     # 200 Startblog gehört bob
http PUT :8080/blogs/1 "Authorization:Bearer $carol" title="Neu" content="X"   # 403 "Only the author may change blog 1"
http PUT :8080/blogs/1 "Authorization:Bearer $admin" title="Neu" content="X"   # 403 admin darf nicht umschreiben
http POST :8080/blogs/1/comments "Authorization:Bearer $alice" text="Hallo"    # 201 "author": "alice"
http DELETE :8080/blogs/2 "Authorization:Bearer $carol"                        # 403 fremdes Blog
http DELETE :8080/blogs/2 "Authorization:Bearer $admin"                        # 204 Moderation
http POST :8080/blogs "Authorization:Bearer kaputt" title="Test" content="X"   # 401 ungültiges Token
```

### Mit dem Swagger UI testen

1. Eine Variante starten und <http://localhost:8080/q/swagger-ui> öffnen. Geschützte Operationen
   tragen ein Schloss. Welche Rollen nötig sind und dass `401`/`403` möglich sind, erzeugt Quarkus
   aus den `@RolesAllowed`-Annotationen.
2. Oben rechts **Authorize** klicken, ein Token von oben einfügen (ohne `Bearer `), dann
   **Authorize** und **Close**.
3. Eine Operation aufklappen, **Try it out**, z. B. `PUT /blogs/1` als `bob` → `200`. Nach
   **Logout** im selben Dialog liefert derselbe Request `401`.

Das Token bleibt dank `quarkus.swagger-ui.persist-authorization=true` auch nach einem Neuladen
der Seite gesetzt.

### Rollen im Token prüfen

Das Token auf [jwt.io](https://jwt.io) einfügen. Im Payload stehen die Rollen unter
`realm_access.roles`. Ausschnitt für `bob` (Variante 2, bei Variante 1 steht Port `8280` im `iss`):

```json
{
  "iss": "http://localhost:8180/realms/blog",
  "sub": "b4b8b584-8662-4d9e-80cb-419a80f1e492",
  "azp": "blog-client",
  "preferred_username": "bob",
  "realm_access": {
    "roles": ["author", "default-roles-blog", "offline_access", "reader", "uma_authorization"]
  }
}
```

jwt.io dekodiert das Token im Browser. Für diese lokalen Test-Tokens ist das unkritisch, Tokens
aus echten Systemen gehören aber nicht auf fremde Webseiten.

### Eigenen Keycloak-Container verwalten (Variante 2)

| Was           | Wo                                                                                                  |
| ------------- | --------------------------------------------------------------------------------------------------- |
| Keycloak      | <http://localhost:8180>                                                                             |
| Admin-Konsole | <http://localhost:8180/admin>, Login `kcadmin` / `kcadmin` (Realm `master`, kein Blog-Benutzer)     |
| Log           | `docker compose logs -f keycloak` – bereit, sobald `Realm 'blog' imported` und `started in` erscheinen |

Der Import läuft nur, wenn der Realm noch nicht existiert. Änderungen aus der Admin-Konsole bleiben
erhalten, solange der Container existiert (`docker compose stop` / `start`).
`docker compose down` löscht den Container samt Daten. Das nächste `docker compose up -d`
importiert den Realm wieder frisch aus der JSON-Datei. Dauerhafte Änderungen gehören deshalb in
`keycloak/blog-realm.json`, dann gelten sie auch für Variante 1.

### Was der Realm-Import einrichtet

- **Realm `blog`** mit einer Access-Token-Lebensdauer von 30 Minuten (Keycloak-Standard: 5) –
  bequemer beim manuellen Testen.
- **Realm-Rollen** `reader`, `author` und `admin`. `reader` steckt in der Standardrolle
  `default-roles-blog` und wird damit jedem Benutzer automatisch zugewiesen.
- **Client `blog-client`**: öffentlicher Client ohne Secret, nur *Direct Access Grants*
  (Password Grant) aktiv, damit HTTPie Tokens abholen kann. Der Password Grant ist nur für
  Entwicklung und Tests gedacht, OAuth 2.1 sieht ihn nicht mehr vor. Ein späteres Frontend nutzt
  stattdessen den Authorization Code Flow mit PKCE.
- **Test-Benutzer** mit festen IDs, damit `sub` bei jedem Import gleich bleibt.

## Container-Image und Betrieb

Das Backend ist als Container-Image auf GitHub Packages (GHCR) veröffentlicht:

| Was        | Wert                                                                                                   |
| ---------- | ------------------------------------------------------------------------------------------------------ |
| Image      | `ghcr.io/farisridzal-hftm/blog-backend`                                                                |
| Tags       | `0.2` (Version aus der `pom.xml`, mit Flyway) und `latest`; `0.1` ist die Version vor Flyway           |
| Basis      | `registry.access.redhat.com/ubi9/openjdk-25-runtime` (aus `src/main/docker/Dockerfile.jvm`)            |
| Paketseite | <https://github.com/users/farisridzal-hftm/packages/container/package/blog-backend>                    |
| Sichtbarkeit | Public – das Image lässt sich ohne Login ziehen                                                      |

### Gesamtsystem starten

Das Image allein reicht nicht: Es braucht eine MySQL-Datenbank und Keycloak.
`docker-compose.app.yml` startet alle drei Container. Keycloak kommt per `include` aus
`docker-compose.yml`, mit derselben Realm-Datei wie in der Entwicklung.

```text
HTTPie / Browser ──1. Token holen──────────▶ blog-keycloak  localhost:8180  (Realm "blog", Test-Benutzer)
       │
       └─────────2. Aufruf mit Bearer-Token──▶ blog-backend   localhost:8080  (Image von GHCR)
                                                   ├──▶ blog-mysql   mysql:3306     (Daten im Volume blog-mysql-data)
                                                   └──▶ blog-keycloak keycloak:8080 (Signaturschlüssel)
```

```shell script
cp .env.example .env                                # PowerShell: Copy-Item .env.example .env
docker compose -f docker-compose.app.yml up -d
docker compose -f docker-compose.app.yml ps         # alle drei "Up", MySQL und Keycloak "healthy"
http :8080/q/health/ready                           # "status": "UP" mit Datenbank-Check
```

- **Startreihenfolge:** Das Backend wartet per Healthcheck, bis MySQL und Keycloak bereit sind.
  Der erste Start dauert etwa eine Minute, Quarkus selbst startet inklusive Flyway-Migrationen in
  rund 10 Sekunden.
- **Datenbank:** MySQL 9.5 in einem eigenen Container, siehe
  [Datenbank: externer MySQL-Container](#datenbank-externer-mysql-container).
- **Keycloak:** importiert beim ersten Start `keycloak/blog-realm.json`, ohne manuelle
  Konfiguration. Test-Benutzer und Rollen siehe [Test-Benutzer](#test-benutzer).
- **Logs:** `docker compose -f docker-compose.app.yml logs -f blog-backend`
- **Stoppen:** `docker compose -f docker-compose.app.yml down` behält die Daten im Volume,
  `down -v` löscht auch die Datenbank.

### Datenbank: externer MySQL-Container

Das Image enthält keine Datenbank. Im Profil `prod` verbindet sich die App mit der Datenbank, auf
die `DB_URL` zeigt, und richtet beim Start mit Flyway selbst das Schema ein (siehe
[Datenbank-Migrationen](#datenbank-migrationen-flyway)). Die Dev Services von Quarkus spielen im
Container keine Rolle.

| Was            | Wert                                                                              |
| -------------- | --------------------------------------------------------------------------------- |
| Container      | `blog-mysql`, Image `mysql:9.5`                                                   |
| Datenbank      | `blog`, Benutzer `blog`, Passwort aus `DB_PASSWORD` in `.env`                    |
| Erreichbarkeit | nur im Docker-Netz als `mysql:3306`, kein Port auf dem Host                       |
| Daten          | im Volume `blog-mysql-data`, überleben Neustarts und neue Backend-Container       |

Ausprobiert: Nach `docker compose -f docker-compose.app.yml up -d --force-recreate --no-deps blog-backend`
läuft ein neuer Backend-Container, alle Blogs sind noch da, und Flyway meldet
`Schema blog is up to date`.

Eine andere MySQL-Datenbank, zum Beispiel ein bestehender Server, funktioniert genauso: `DB_URL`,
`DB_USER` und `DB_PASSWORD` setzen (siehe [Konfiguration des Images](#konfiguration-des-images)).
Der Benutzer braucht Rechte zum Anlegen und Ändern von Tabellen in dieser Datenbank, weil Flyway
beim ersten Start die Tabellen erstellt. Ist die Datenbank leer, legt die App danach zwei
Beispiel-Blogs von `bob` an.

Datenbank direkt ansehen, ohne das Passwort einzutippen (es kommt aus der Umgebung des
Containers):

```shell script
docker compose -f docker-compose.app.yml exec mysql sh -c 'mysql -ublog -p"$MYSQL_PASSWORD" blog -e "SELECT id, title, authorName, createdAt FROM Blog"'
```

### Secrets in `.env`

Passwörter stehen nicht im Repository. `docker compose` liest sie aus `.env`, die in der
`.gitignore` steht; `.env.example` ist die Vorlage:

| Variable                  | Pflicht | Bedeutung                                                    |
| ------------------------- | ------- | ------------------------------------------------------------ |
| `DB_PASSWORD`             | ja      | Passwort des DB-Benutzers `blog` (MySQL und Backend)         |
| `DB_ROOT_PASSWORD`        | ja      | Passwort des MySQL-Benutzers `root`                          |
| `KEYCLOAK_ADMIN_PASSWORD` | nein    | Admin-Konsole `kcadmin`, Standard `kcadmin`                  |
| `BACKEND_IMAGE`           | nein    | anderes Image oder anderer Tag, Standard `…/blog-backend:latest` |

Fehlt ein Pflichtwert, bricht `docker compose` mit einer Meldung ab, statt mit einem leeren
Passwort zu starten. Auch die App selbst hat für `DB_PASSWORD` keinen Standardwert.

### Konfiguration des Images

Alles, was sich zwischen Umgebungen unterscheidet, kommt aus Umgebungsvariablen (Profil `prod`
in `application.properties`):

| Variable                 | Standard                            | Bedeutung                                                         |
| ------------------------ | ----------------------------------- | ----------------------------------------------------------------- |
| `DB_URL`                 | `jdbc:mysql://localhost:3306/blog`  | JDBC-URL der Datenbank                                            |
| `DB_USER`                | `blog`                              | Datenbank-Benutzer                                                |
| `DB_PASSWORD`            | – (Pflicht)                         | Datenbank-Passwort; ohne startet die App nicht                    |
| `OIDC_SERVER_URL`        | `http://localhost:8180/realms/blog` | Wo das Backend Keycloak erreicht, um die Signaturschlüssel zu holen |
| `OIDC_ISSUER`            | wie `OIDC_SERVER_URL`               | Welcher Aussteller (`iss`) in den Tokens stehen muss              |
| `BLOG_SEED_DATA_ENABLED` | `true`                              | Eigene Property `blog.seed-data.enabled`: Beispiel-Blogs anlegen  |

Jede andere Quarkus-Property lässt sich ebenso setzen, zum Beispiel
`QUARKUS_LOG_CATEGORY__CH_HFTM__LEVEL=DEBUG` für ausführliche Logs.

**Warum `OIDC_SERVER_URL` und `OIDC_ISSUER` getrennt sind:** Im Docker-Netz erreicht das Backend
Keycloak unter `http://keycloak:8080`. HTTPie und Browser holen ihre Tokens aber über
`http://localhost:8180`, und genau diese Adresse steht als Aussteller im Token. Ohne
`OIDC_ISSUER=http://localhost:8180/realms/blog` lehnt das Backend gültige Tokens mit `401` ab
(ausprobiert); mit der Einstellung prüft es Signatur und Aussteller korrekt.

Ohne Compose, zum Beispiel neben bestehender Infrastruktur, sieht ein Start so aus:

```shell script
docker run -d -p 8080:8080 -e DB_URL=jdbc:mysql://<db-host>:3306/blog -e DB_PASSWORD=<passwort> -e OIDC_SERVER_URL=http://<keycloak-host>/realms/blog ghcr.io/farisridzal-hftm/blog-backend:latest
```

### Use-Cases durchspielen (Token holen und einsetzen)

Voraussetzung: Das Gesamtsystem läuft (siehe oben) und die Datenbank ist frisch. Dann gibt es die
zwei Beispiel-Blogs `1` und `2` von `bob`.

**Schritt 1 – Token holen.** Jeder Benutzer meldet sich bei Keycloak (Port `8180`) an und bekommt
ein Access-Token. Passwort = Benutzername, gültig 30 Minuten. PowerShell:

```powershell
function Get-Token($user, $port = 8180) {
  (http --form POST ":$port/realms/blog/protocol/openid-connect/token" grant_type=password client_id=blog-client username=$user password=$user | ConvertFrom-Json).access_token
}
$bob = Get-Token bob; $carol = Get-Token carol; $alice = Get-Token alice; $admin = Get-Token admin
```

Bash (braucht [`jq`](https://jqlang.org/)):

```shell script
token() { http --form POST :8180/realms/blog/protocol/openid-connect/token grant_type=password client_id=blog-client username=$1 password=$1 | jq -r .access_token; }
bob=$(token bob); carol=$(token carol); alice=$(token alice); admin=$(token admin)
```

**Schritt 2 – Token einsetzen.** Das Token gehört in den Header `Authorization: Bearer <token>`.
Mit HTTPie geht das als `"Authorization:Bearer $bob"` oder kürzer mit der eingebauten Option
`-A bearer -a $bob`.

**Schritt 3 – Use-Cases.** Die Aufrufe bauen aufeinander auf:

| #  | Use-Case                                | Wer                  | Ergebnis                                    |
| -- | --------------------------------------- | -------------------- | ------------------------------------------- |
| 1  | Blogs lesen                             | alle, ohne Login     | `200`, zwei Blogs von `bob`                 |
| 2  | Schreiben ohne Login                    | anonym               | `401`                                       |
| 3  | Blog veröffentlichen                    | `bob` (author)       | `201`, `"author": "bob"`, mit `createdAt`   |
| 4  | Eigenes Blog ändern                     | `bob`                | `200`                                       |
| 5  | Fremdes Blog ändern                     | `carol` (author)     | `403` – «Only the author may change blog 1» |
| 6  | Kommentieren                            | `alice` (reader)     | `201`, `"author": "alice"`                  |
| 7  | Blog schreiben ohne Autorenrolle        | `alice`              | `403` – «Missing required role»             |
| 8  | Fremdes Blog löschen (Moderation)       | `admin`              | `204`                                       |

```powershell
http :8080/blogs                                                                         # 1
http POST :8080/blogs title="Test" content="Ohne Token"                                  # 2
http POST :8080/blogs "Authorization:Bearer $bob" title="Mein Blog" content="Hallo Welt" # 3
http PUT :8080/blogs/1 "Authorization:Bearer $bob" title="Neuer Titel" content="Neu"     # 4
http PUT :8080/blogs/1 "Authorization:Bearer $carol" title="Gekapert" content="X"        # 5
http POST :8080/blogs/1/comments "Authorization:Bearer $alice" text="Toller Beitrag"     # 6
http :8080/blogs/1/comments                                                              #   Kommentar ist öffentlich lesbar
http POST :8080/blogs "Authorization:Bearer $alice" title="Test" content="X"             # 7
http DELETE :8080/blogs/2 "Authorization:Bearer $admin"                                  # 8
```

In Bash sind es dieselben Aufrufe mit `$bob` usw. aus Schritt 1, zum Beispiel
`http -A bearer -a "$bob" POST :8080/blogs title="Mein Blog" content="Hallo Welt"`.

Die Ergebnisse in der Tabelle stammen aus einem Durchlauf mit genau diesen Befehlen gegen das
Image `0.2` auf frischer Datenbank. Was das Backend dabei protokolliert, zeigt der Abschnitt
[Logging](#logging). Alternativ lassen sich alle Use-Cases im Swagger UI durchspielen:
<http://localhost:8080/q/swagger-ui>, Token über **Authorize** einfügen (siehe
[Mit dem Swagger UI testen](#mit-dem-swagger-ui-testen)).

### Image bauen und veröffentlichen

Gebaut wird mit der Extension `quarkus-container-image-docker` aus `src/main/docker/Dockerfile.jvm`.
Registry, Name, Tags und das Label, das das Paket mit diesem Repository verknüpft, stehen in
`application.properties`:

```shell script
./mvnw package -Dquarkus.container-image.build=true
```

Das ergibt `ghcr.io/farisridzal-hftm/blog-backend:0.2` und `:latest`. Zum Veröffentlichen
braucht Docker einmalig ein Login bei GHCR mit einem Token, das Pakete schreiben darf:

```shell script
gh auth refresh -h github.com -s write:packages
gh auth token | docker login ghcr.io -u farisridzal-hftm --password-stdin
docker push ghcr.io/farisridzal-hftm/blog-backend:0.2
docker push ghcr.io/farisridzal-hftm/blog-backend:latest
```

Bauen und Pushen geht auch in einem Schritt mit `-Dquarkus.container-image.push=true`.

Neue Pakete sind auf GHCR zuerst privat. Öffentlich wird das Image auf GitHub unter
*Packages → blog-backend → Package settings → Danger Zone → Change visibility → Public*.
Alternativ lässt sich dort unter *Manage access → Invite* einem einzelnen Benutzer (z. B.
`simeonlin`) die Rolle *Read* geben.

Ob das Image wirklich öffentlich ist, zeigt ein Pull ohne Login:

```shell script
docker logout ghcr.io
docker pull ghcr.io/farisridzal-hftm/blog-backend:latest
```

### Vertiefung: natives Image im Vergleich

Dasselbe Backend lässt sich mit GraalVM (Mandrel) zu einem nativen Programm kompilieren. Eine
lokale GraalVM ist nicht nötig, gebaut wird in einem Builder-Container:

```shell script
./mvnw package -Dnative -DskipTests -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.container-image.tag=0.2-native -Dquarkus.container-image.additional-tags=latest-native
```

Die eigenen Tags verhindern, dass das native Image `latest` (JVM) überschreibt. Als Basis dient
`src/main/docker/Dockerfile.native` (`ubi9/ubi-minimal`). Der Build dauerte rund 8 Minuten und
brauchte bis zu 5 GB Arbeitsspeicher, Docker sollte also mindestens 6 GB zur Verfügung haben.

Gemessen mit Version `0.1` auf demselben Laptop, je zweimal, beide Images mit identischer
Konfiguration gegen dieselbe MySQL und denselben Keycloak:

| Messgrösse                              | JVM (`0.1`)    | Nativ (`0.1-native`) |
| --------------------------------------- | -------------- | -------------------- |
| Image-Grösse                            | 730 MB         | 304 MB               |
| Startzeit laut Quarkus                  | 4,1 – 4,2 s    | 0,12 s               |
| `docker run` bis Health `UP`            | 5,4 – 5,5 s    | 1,0 – 1,1 s          |
| Arbeitsspeicher nach dem Start          | 233 – 244 MiB  | 23 MiB               |
| Arbeitsspeicher nach 200 Requests       | 241 – 252 MiB  | 41 – 43 MiB          |

Fachlich verhält sich das native Image gleich: Token-Prüfung, Besitzprüfung (`403`), Fehler
(`400` bei kaputtem JSON) und Logging funktionieren unverändert. Im Gesamtsystem lässt es sich
mit `BACKEND_IMAGE=ghcr.io/farisridzal-hftm/blog-backend:0.2-native` in der `.env` verwenden,
sofern es ebenfalls gepusht wurde.

Abwägung: Das native Image startet rund 35-mal schneller und braucht etwa ein Zehntel des
Speichers. Das lohnt sich, wenn viele Instanzen laufen oder Instanzen oft neu starten (Skalieren
auf null, Serverless). Dafür ist der Build langsam und speicherhungrig, und Bibliotheken mit viel
Reflection brauchen manchmal zusätzliche Konfiguration. Für die Entwicklung und die Tests bleibt
deshalb das JVM-Image der Standard. Nicht gemessen wurde der Durchsatz unter Dauerlast, bei dem
die JVM dank JIT aufholen kann.

## Datenbank-Migrationen (Flyway)

Das Schema entsteht nicht mehr automatisch aus den Entitäten. Stattdessen legen versionierte
SQL-Skripte es an und entwickeln es weiter. Flyway (`quarkus-flyway` und `flyway-mysql`) führt
sie beim Start aus und merkt sich in der Tabelle `flyway_schema_history`, welche schon gelaufen
sind.

| Migration                       | Inhalt                                                                                        |
| ------------------------------- | --------------------------------------------------------------------------------------------- |
| `V1__create_blog_tables.sql`    | Tabellen `Blog`, `BlogComment` und die ID-Tabellen `Blog_SEQ`, `BlogComment_SEQ` – das Schema von Version 0.1 |
| `V2__add_created_at.sql`        | Spalte `createdAt` für Blogs und Kommentare; bestehende Zeilen bekommen den Zeitpunkt der Migration, danach wird die Spalte `NOT NULL` |

Warum nicht weiter Hibernates `update`? `update` legt nur fehlende Tabellen und Spalten an, ohne
Historie und ohne Kontrolle. Bestehende Daten nachzuführen wie in V2 (erst befüllen, dann
`NOT NULL`) oder Spalten umzubenennen kann es nicht. Mit Flyway läuft in der Entwicklung, in den
Tests und im Container genau dasselbe, nachvollziehbar und im Git versioniert.

### Ablauf beim Start

1. **Flyway** prüft die Prüfsummen der bereits gelaufenen Skripte und führt neue Skripte der Reihe
   nach aus (`quarkus.flyway.migrate-at-start=true`).
2. **Hibernate** vergleicht Entitäten und Tabellen
   (`quarkus.hibernate-orm.schema-management.strategy=validate`). Fehlt eine Spalte oder passt ein
   Typ nicht, startet die App nicht, statt später mit SQL-Fehlern zu scheitern.
3. **`DataInitialization`** legt bei leerer Datenbank die zwei Beispiel-Blogs an.

Das gilt in jedem Profil. Im Dev-Modus und in den Tests migriert Flyway die frische Datenbank der
Dev Services; `FlywayMigrationTest` prüft, dass alle Migrationen erfolgreich gelaufen sind und
keine nachträglich verändert wurde.

### Upgrade einer Datenbank aus Version 0.1

Datenbanken aus Version 0.1 hat noch Hibernate angelegt, sie haben keine Flyway-Historie. Damit
Flyway nicht daran scheitert, ist `quarkus.flyway.baseline-on-migrate=true` mit
`baseline-version=1` gesetzt: Flyway trägt eine solche Datenbank als Version 1 ein und führt nur
V2 und neuere Skripte aus. Bei einer leeren Datenbank hat das keine Wirkung. Ausprobiert mit
einer echten 0.1-Datenbank samt Daten:

```text
installed_rank  version  description            type      success
1               1        << Flyway Baseline >>  BASELINE  1
2               2        add created at         SQL       1
```

Alle drei bestehenden Blogs blieben erhalten und bekamen einen `createdAt`-Wert. Die Historie
lässt sich so abfragen:

```shell script
docker compose -f docker-compose.app.yml exec mysql sh -c 'mysql -ublog -p"$MYSQL_PASSWORD" blog -e "SELECT installed_rank, version, description, type, success FROM flyway_schema_history"'
```

### Eine neue Migration hinzufügen

1. Entität ändern.
2. Neues Skript `src/main/resources/db/migration/V3__kurze_beschreibung.sql` anlegen. Den
   passenden SQL-Code liefert Hibernate, wenn man es das komplette Schema als Skript ausgeben
   lässt:

   ```shell script
   ./mvnw test -Dtest=BlogRepositoryTest -Dquarkus.hibernate-orm.scripts.generation=create -Dquarkus.hibernate-orm.scripts.generation.create-target=target/hibernate-create.sql
   ```

3. `./mvnw test`: Flyway migriert die Testdatenbank, Hibernate validiert das Ergebnis.

Ein Skript, das schon irgendwo gelaufen ist, wird **nie mehr verändert**. Flyway erkennt die
geänderte Prüfsumme und verweigert den Start. Korrekturen kommen als neues Skript. Weil MySQL
Änderungen an Tabellen nicht in einer Transaktion zurückrollen kann, bleiben die Skripte klein:
Bricht eines ab, ist nur wenig von Hand aufzuräumen.

Flyway meldet beim Start eine Warnung, dass MySQL 9.5 neuer sei als die zuletzt geprüfte Version
9.4. Die Migrationen verwenden nur einfaches SQL, die Warnung ist harmlos.

## Logging

Alle Klassen loggen über die statische Fassade `io.quarkus.logging.Log`. Ein Logger-Feld ist
nicht nötig, Quarkus setzt beim Build die Kategorie der aufrufenden Klasse ein. Parameter werden
mit `Log.infof("Blog %d created by %s", id, user)` übergeben statt per String-Verkettung.

| Stufe   | Wofür                                                                     | Wo                                                                            |
| ------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `ERROR` | Unerwartete Fehler mit Stacktrace; der Client bekommt nur ein `500`        | `UnexpectedExceptionMapper`                                                   |
| `WARN`  | Verweigerter Zugriff eines angemeldeten Benutzers – möglicher Missbrauch  | `ForbiddenExceptionMapper`                                                    |
| `INFO`  | Systemzustände (Start mit Profil/Datenbank/Keycloak, Startdaten, Stopp) und Geschäftsereignisse (Blog angelegt, geändert, gelöscht, Kommentar), immer mit Benutzer | `ApplicationLifecycle`, `DataInitialization`, `BlogService` |
| `DEBUG` | Details für die Fehlersuche: Lesezugriffe, abgelehnte Eingaben (`400`, `404`) | `BlogService`, übrige Exception-Mapper                                     |

Dazu schreibt Quarkus pro HTTP-Request eine Zeile ins Access-Log (Methode, Pfad, Status, Grösse,
Dauer). Damit ist auch ein `401` sichtbar, das Quarkus beantwortet, bevor der eigene Code läuft.

Log des Images bei den Use-Cases oben (Zeitstempel und Thread gekürzt, Health-Checks weggelassen):

```text
INFO  [ch.hftm.ApplicationLifecycle] Blog API started: profiles=[prod], database=jdbc:mysql://mysql:3306/blog, keycloak=http://keycloak:8080/realms/blog
INFO  [ch.hftm.DataInitialization] Created 2 seed blogs owned by bob
INFO  [io.quarkus.http.access-log] 172.21.0.1 "GET /blogs HTTP/1.1" 200 245 bytes 235 ms
INFO  [io.quarkus.http.access-log] 172.21.0.1 "POST /blogs HTTP/1.1" 401 - bytes 38 ms
INFO  [ch.hftm.service.BlogService] Blog 3 created by bob
INFO  [io.quarkus.http.access-log] 172.21.0.1 "POST /blogs HTTP/1.1" 201 108 bytes 145 ms
INFO  [ch.hftm.service.BlogService] Blog 1 updated by bob
INFO  [io.quarkus.http.access-log] 172.21.0.1 "PUT /blogs/1 HTTP/1.1" 200 103 bytes 68 ms
WARN  [ch.hftm.rest.mapper.ForbiddenExceptionMapper] Access denied for carol on /blogs/1: Only the author may change blog 1
INFO  [io.quarkus.http.access-log] 172.21.0.1 "PUT /blogs/1 HTTP/1.1" 403 80 bytes 43 ms
INFO  [ch.hftm.service.BlogService] Comment 1 added to blog 1 by alice
INFO  [io.quarkus.http.access-log] 172.21.0.1 "POST /blogs/1/comments HTTP/1.1" 201 91 bytes 67 ms
INFO  [io.quarkus.http.access-log] 172.21.0.1 "GET /blogs/1/comments HTTP/1.1" 200 93 bytes 113 ms
WARN  [ch.hftm.rest.mapper.ForbiddenExceptionMapper] Access denied for alice on /blogs: Missing required role
INFO  [io.quarkus.http.access-log] 172.21.0.1 "POST /blogs HTTP/1.1" 403 68 bytes 8 ms
INFO  [ch.hftm.service.BlogService] Blog 2 deleted by admin (as admin)
INFO  [io.quarkus.http.access-log] 172.21.0.1 "DELETE /blogs/2 HTTP/1.1" 204 - bytes 62 ms
```

- **Konfiguration:** `ch.hftm` loggt standardmässig ab `INFO`, im Dev-Modus ab `DEBUG`. Im
  Container lässt sich das mit `QUARKUS_LOG_CATEGORY__CH_HFTM__LEVEL=DEBUG` ändern, ohne das Image
  neu zu bauen.
- **Nachvollziehbarkeit:** Löscht ein `admin` ein fremdes Blog, steht `(as admin)` im Log. So ist
  Moderation von normalem Löschen unterscheidbar.
- **Was nie geloggt wird:** Tokens, Passwörter sowie Inhalte von Blogs und Kommentaren. Das
  Access-Log enthält keine Header, also auch kein `Authorization`. Geloggt werden nur IDs und
  Login-Namen.

## Tests

```shell script
./mvnw test
```

Docker muss laufen: Für die Tests starten die Dev Services MySQL und Keycloak (mit
`keycloak/blog-realm.json`). Der erste Start dauert rund 1,5 Minuten, danach laufen die Tests in
wenigen Sekunden.

REST Assured ist als Test-Abhängigkeit in der `pom.xml` eingebunden. Bei `@QuarkusTest` startet
Quarkus die Applikation auf Port `8081` und richtet REST Assured automatisch auf diesen Port aus;
`given()` braucht deshalb keine URL. Request-Bodies werden als DTO-Objekte übergeben
(`.body(new BlogRequest(...))`) und von REST Assured zu JSON serialisiert. Antworten lassen sich
typisiert auslesen (`.extract().as(BlogResponse.class)`).

| Testklasse           | Art                                                   | Prüft                                                                                                  |
| -------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `BlogSecurityTest`   | `@QuarkusTest` + REST Assured + `@TestSecurity`       | Die Zugriffsregeln Zeile für Zeile: anonym `401`, `reader`/`author`/`admin` mit erlaubten und verbotenen Aufrufen, Besitzprüfung, Autor im Body wird ignoriert |
| `KeycloakTokenTest`  | `@QuarkusTest` + REST Assured + echte Keycloak-Tokens | Ende-zu-Ende mit Tokens aus dem Dev-Services-Keycloak: Rollen aus `realm_access.roles`, `sub` als Besitzer, ungültiges Token → `401` |
| `BlogResourceTest`   | `@QuarkusTest` + REST Assured, als `bob`              | Alle Endpunkte, DTO-Vertrag (nur freigegebene Felder, keine Mass Assignment), Validierung, Fehler     |
| `BlogServiceTest`    | `@QuarkusTest` + `@TestSecurity`                      | Fachlogik: Suche, Sortierung, Update, Löschen, Kommentare, Autor und Besitzprüfung ohne HTTP          |
| `CorsTest`           | `@QuarkusTest` + REST Assured                         | Preflight inkl. `Authorization`-Header, `Location` freigegeben, fremde Origin → `403`                  |
| `BlogRepositoryTest` | `@QuarkusTest`                                        | Panache-Datenzugriff                                                                                   |
| `FlywayMigrationTest` | `@QuarkusTest`                                       | Alle Migrationen erfolgreich angewendet (Version 2), keine nachträglich verändert                     |
| `BlogMapperTest`     | reiner JUnit-Test                                     | Mapping Entität ↔ DTO (nur der Autor-Name wird herausgegeben), läuft ohne Quarkus und Datenbank       |
| `ErrorHandlingTest`  | `@QuarkusTest` + REST Assured                         | Der Catch-all-Mapper lässt `404`, `405` und `400` (kaputtes JSON) unverändert, Health-Check `UP`       |
| `BlogApiIT`, `GreetingResourceIT` | `@QuarkusIntegrationTest`                | Blackbox-Tests gegen das gebaute Container-Image, siehe unten                                         |

### Benutzer simulieren mit `@TestSecurity`

`@TestSecurity` meldet für einen Test einen Benutzer mit Rollen an, ohne dass ein Token geholt
werden muss. Weil die Besitzprüfung den Claim `sub` verwendet, ergänzt `@OidcSecurity` (aus
`quarkus-test-security-oidc`) das simulierte Token um diese ID. Die Werte stehen als Konstanten in
`ch.hftm.security.TestUsers` und entsprechen den IDs im Realm:

```java
@Test
@TestSecurity(user = CAROL, roles = {Roles.READER, Roles.AUTHOR})
@OidcSecurity(claims = @Claim(key = "sub", value = CAROL_ID))
void authorMayNotChangeSomeoneElsesBlog() {
    Long id = blogOfBob();

    given().contentType(ContentType.JSON).body(new BlogRequest("Hijacked", "Carol was here"))
            .when().put("/blogs/{id}", id)
            .then().statusCode(403)
            .body("message", is("Only the author may change blog " + id));
}
```

Die zwei Ansätze ergänzen sich: `@TestSecurity` prüft jede Regel schnell und gezielt.
`KeycloakTokenTest` holt mit `KeycloakTestClient` echte Tokens und stellt sicher, dass Realm-Datei,
Rollen-Claim und `sub` im Zusammenspiel funktionieren.

### Blackbox-Tests gegen das Container-Image

```shell script
./mvnw verify -DskipITs=false -Dquarkus.container-image.build=true
```

Maven baut zuerst das Image, dann startet `@QuarkusIntegrationTest` es per `docker run` neben den
Dev Services (MySQL, Keycloak). Die Tests sprechen nur HTTP mit dem Container und haben keinen
Zugriff auf Beans oder die Datenbank. `BlogApiIT` prüft: Start mit Health `UP`, öffentliches Lesen
inklusive Startdaten, `401` ohne Token, und dass die OpenAPI-Beschreibung die geschützten
Operationen markiert.

Damit die Dev Services greifen, startet Quarkus das Image dabei mit dem Profil `test`
(`quarkus.test.integration-test-profile=test`); das `prod`-Profil würde echte Server erwarten. Ohne
`-DskipITs=false` werden die Integrationstests übersprungen, `./mvnw test` bleibt also schnell.

---

## Quarkus - Projekt-Setup

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/ds1-0.2-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST JSON-B ([guide](https://quarkus.io/guides/rest#json-serialisation)): JSON-B serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
