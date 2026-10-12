# ds1 – Blog REST API

Quarkus-Applikation mit einem Blog-System (Blogs und Kommentare, 1:n) und einer REST-Schnittstelle.

## REST-Design

Basis-URL: `http://localhost:8080`
Alle Endpunkte liefern und erwarten `application/json` (Ausnahme: `GET /hello`, `text/plain`).

### Ressourcen

Das Blog ist die Hauptressource. Kommentare sind eine Unterressource eines Blogs und haben
deshalb keinen eigenen Top-Level-Pfad – ein Kommentar existiert nie ohne Blog.

| Methode  | Pfad                   | Erwartetes Verhalten                                          | Status (Erfolg)  | Status (Fehler) |
| -------- | ---------------------- | ------------------------------------------------------------- | ---------------- | --------------- |
| `GET`    | `/blogs`               | Liste aller Blogs, optional gefiltert, sortiert, begrenzt      | `200 OK`         | `400`           |
| `GET`    | `/blogs/{id}`          | Einzelnes Blog                                                 | `200 OK`         | `404`           |
| `POST`   | `/blogs`               | Neues Blog anlegen, `Location`-Header zeigt auf die Ressource  | `201 Created`    | `400`           |
| `PUT`    | `/blogs/{id}`          | Bestehendes Blog vollständig ersetzen (Titel + Inhalt)         | `200 OK`         | `400`, `404`    |
| `DELETE` | `/blogs/{id}`          | Blog samt seinen Kommentaren löschen                           | `204 No Content` | `404`           |
| `GET`    | `/blogs/{id}/comments` | Alle Kommentare eines Blogs                                    | `200 OK`         | `404`           |
| `POST`   | `/blogs/{id}/comments` | Kommentar zu einem Blog hinzufügen                             | `201 Created`    | `400`, `404`    |
| `GET`    | `/hello`               | Begrüssungstext (aus dem Projekt-Setup)                        | `200 OK`         | –               |

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

| DTO               | Verwendet bei                                                 | Gibt frei / nimmt an     | Bewusst **nicht** enthalten                         |
| ----------------- | ------------------------------------------------------------- | ------------------------ | --------------------------------------------------- |
| `BlogRequest`     | `POST /blogs`, `PUT /blogs/{id}`                              | `title`, `content`       | `id` (vergibt der Server), Kommentare               |
| `BlogResponse`    | `GET /blogs`, `GET /blogs/{id}`, Antwort auf `POST` und `PUT` | `id`, `title`, `content` | Kommentarliste (eigene Unterressource), JPA-Interna |
| `CommentRequest`  | `POST /blogs/{id}/comments`                                   | `text`                   | `id`, Blog (kommt aus dem Pfad, nicht aus dem Body) |
| `CommentResponse` | `GET /blogs/{id}/comments`, Antwort auf `POST`                | `id`, `text`             | Rückverweis auf das Blog                            |

Beispiel `POST /blogs`:

```jsonc
// Request (BlogRequest) – ein mitgeschicktes "id" wird ignoriert
{ "title": "Mein Blog", "content": "Hallo Welt" }

// Response 201 (BlogResponse)
{ "id": 3, "title": "Mein Blog", "content": "Hallo Welt" }
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
Entität, etwa später ein Autor oder ein internes Moderations-Flag, erscheint erst dann in der API,
wenn es jemand in DTO und Mapper ergänzt. Bei vier kleinen DTOs ist das ausserdem übersichtlicher
als eine zusätzliche Abhängigkeit.

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

### Beispiele

```shell script
# Blog anlegen
curl -i -X POST http://localhost:8080/blogs -H "Content-Type: application/json" -d "{\"title\": \"Mein Blog\", \"content\": \"Hallo Welt\"}"

# Suchen, sortieren, begrenzen
curl "http://localhost:8080/blogs?search=hallo&sort=title&order=desc" -H "X-Max-Results: 5"

# Blog aktualisieren
curl -X PUT http://localhost:8080/blogs/1 -H "Content-Type: application/json" -d "{\"title\": \"Neuer Titel\", \"content\": \"Neuer Inhalt\"}"

# Ungueltiger Request -> 400 mit Feldliste
curl -i -X POST http://localhost:8080/blogs -H "Content-Type: application/json" -d "{\"title\": \"   \", \"content\": \"Inhalt ist ok\"}"

# Kommentar hinzufuegen
curl -X POST http://localhost:8080/blogs/1/comments -H "Content-Type: application/json" -d "{\"text\": \"Schoener Beitrag!\"}"
```

### Aufbau im Code

| Schicht    | Paket                 | Aufgabe                                       |
| ---------- | --------------------- | --------------------------------------------- |
| REST       | `ch.hftm.rest`        | Pfade, Parameter, Statuscodes                 |
| DTOs       | `ch.hftm.rest.dto`    | Request-/Response-DTOs und `BlogMapper`       |
| Mapper     | `ch.hftm.rest.mapper` | Exceptions → einheitliche Fehler-Responses    |
| Service    | `ch.hftm.service`     | Fachlogik, Transaktionen, Sortier-/Suchregeln |
| Repository | `ch.hftm.repository`  | Panache-Datenzugriff                          |
| Model      | `ch.hftm.model`       | JPA-Entitäten inkl. Validierungsregeln        |
| Exceptions | `ch.hftm.exception`   | Fachliche Fehler (`BlogNotFoundException`, …) |

### Vorerst ausgeklammert

Bewusst noch nicht umgesetzt, um den Umfang klein zu halten:

- **`PATCH`**: Es gibt nur vollständige Updates via `PUT`.
- **Kommentare einzeln bearbeiten/löschen**: `PUT`/`DELETE` auf `/blogs/{id}/comments/{commentId}`
  fehlt noch; Kommentare verschwinden aktuell nur zusammen mit ihrem Blog.
- **Pagination**: Statt `page`/`size` gibt es vorerst nur den Header `X-Max-Results`.
- **Autor / Zeitstempel**: Blogs und Kommentare haben noch keinen Autor und kein Erstelldatum.
- **Authentifizierung und Autorisierung**: Alle Endpunkte sind offen (siehe
  [OWASP-Abschnitt](#sicherheit-owasp-api-security-top-10-2023)).
- **OpenAPI-Dokumentation**: Noch keine `quarkus-smallrye-openapi`-Integration.

## CORS

Ein Browser-Frontend, das auf einem anderen Port oder einer anderen Domain läuft, darf die API
nur aufrufen, wenn der Server das per CORS erlaubt. Die Konfiguration steht in
`src/main/resources/application.properties`:

| Einstellung                              | Wert                                             | Grund                                                                                     |
| ---------------------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| `quarkus.http.cors.enabled`              | `true`                                           | CORS-Filter von Quarkus einschalten                                                       |
| `quarkus.http.cors.origins`              | `http://localhost:4200`, `http://localhost:5173` | Standard-Ports der Dev-Server von Angular und Vite                                         |
| `quarkus.http.cors.methods`              | `GET, POST, PUT, DELETE, OPTIONS`                | Genau die Methoden, die die API anbietet (`OPTIONS` für den Preflight)                     |
| `quarkus.http.cors.headers`              | `Content-Type, Accept, X-Max-Results`            | `X-Max-Results` ist ein eigener Header; ohne Freigabe blockiert der Browser den Preflight |
| `quarkus.http.cors.exposed-headers`      | `Location`                                       | Damit das Frontend nach einem `POST` die URL der neuen Ressource lesen kann               |
| `quarkus.http.cors.access-control-max-age` | `1H`                                           | Der Browser darf das Preflight-Ergebnis eine Stunde lang zwischenspeichern                |

Bewusst **kein** Wildcard `*`: Anfragen mit einer fremden `Origin` beantwortet Quarkus mit `403`
(geprüft in `CorsTest`).

Noch offen ist die produktive Origin. Es gibt noch kein Frontend und kein Deployment, deshalb ist
nur die lokale Entwicklung freigegeben. Sobald das Frontend eine feste URL hat, kommt diese in
`quarkus.http.cors.origins` dazu. Kommt Authentifizierung hinzu, muss zusätzlich der Header
`Authorization` erlaubt werden.

CORS schützt nur Benutzer im Browser. `curl`, Postman oder andere Server ignorieren CORS
vollständig – es ersetzt also keine Authentifizierung.

## Sicherheit: OWASP API Security Top 10 (2023)

Einschätzung, welche Risiken für diese Blog-API relevant sind und wie weit sie abgedeckt sind:

| Risiko                                                  | Relevanz         | Status             | Begründung                                                                                                                                                                                                                                                  |
| ------------------------------------------------------- | ---------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **API1** Broken Object Level Authorization              | hoch             | offen              | Blogs sind über fortlaufende IDs adressierbar (`/blogs/1`, `/blogs/2`, …), und jeder darf jedes Blog lesen, ändern und löschen. Es gibt noch keine Besitzer. Nötig: Autor pro Blog und im Service die Prüfung, ob das Blog dem aufrufenden Benutzer gehört. |
| **API2** Broken Authentication                          | hoch             | offen              | Es gibt keine Authentifizierung. Geplant ist ein Standardverfahren (OIDC/JWT mit `quarkus-oidc` oder `smallrye-jwt`) statt eines eigenen Login-Mechanismus.                                                                                                 |
| **API3** Broken Object Property Level Authorization     | hoch             | teilweise abgedeckt | Durch die DTOs: Responses geben nur freigegebene Felder heraus (keine Excessive Data Exposure), Requests nehmen nur `title`/`content`/`text` an, die `id` ist nicht setzbar (keine Mass Assignment, Test `clientCannotChooseTheIdOfANewBlog`). Felder mit rollenabhängiger Sichtbarkeit gibt es noch nicht. |
| **API4** Unrestricted Resource Consumption              | mittel           | teilweise abgedeckt | Längenlimits per Bean Validation (Titel 120, Inhalt 4000, Kommentar 1000 Zeichen), Sortierfelder per Whitelist. Offen: `GET /blogs` liefert ohne Header alle Blogs (keine Pagination), `X-Max-Results` hat keine Obergrenze, es gibt kein Rate Limiting.     |
| **API5** Broken Function Level Authorization            | hoch             | offen              | `PUT` und `DELETE` sind verwaltende Funktionen, aber für jeden aufrufbar. Später mit Rollen absichern (`@RolesAllowed`).                                                                                                                                      |
| **API6** Unrestricted Access to Sensitive Business Flows | mittel          | offen              | Kommentieren ist genau das Beispiel aus der OWASP-Beschreibung: Ein Bot könnte beliebig viele Kommentare posten. Es gibt weder Rate Limiting noch Captcha oder Moderation.                                                                                  |
| **API7** Server Side Request Forgery                    | nicht relevant   | –                  | Die API ruft keine URLs auf, die von einem Client stammen.                                                                                                                                                                                                  |
| **API8** Security Misconfiguration                      | mittel           | teilweise abgedeckt | CORS mit expliziten Origins statt `*`; die eigenen Fehler-Responses enthalten keine Stacktraces oder Klassennamen; die Dev UI läuft nur im Dev-Modus. Offen: HTTPS, Security-Header und die produktive CORS-Origin.                                       |
| **API9** Improper Inventory Management                  | niedrig          | teilweise abgedeckt | Alle Endpunkte sind in dieser README dokumentiert. Offen: keine OpenAPI-Beschreibung, keine Versionierung (z. B. `/v1/blogs`), und der Demo-Endpunkt `/hello` aus dem Projekt-Setup ist noch aktiv.                                                     |
| **API10** Unsafe Consumption of APIs                    | nicht relevant   | –                  | Es werden keine Drittanbieter-APIs angebunden.                                                                                                                                                                                                              |

Fazit: Dank der DTOs gibt die API keine internen Felder preis und lässt keine fremden Felder
setzen (API3); zu lange Texte werden abgelehnt (API4). Die grösste Lücke ist die fehlende
Authentifizierung (API2), an der API1, API5 und API6 direkt hängen. Sie ist deshalb der nächste
Schritt.

## Tests

```shell script
./mvnw test
```

REST Assured ist als Test-Abhängigkeit in der `pom.xml` eingebunden. Bei `@QuarkusTest` startet
Quarkus die Applikation auf Port `8081` und richtet REST Assured automatisch auf diesen Port aus;
`given()` braucht deshalb keine URL. Request-Bodies werden als DTO-Objekte übergeben
(`.body(new BlogRequest(...))`) und von REST Assured zu JSON serialisiert. Antworten lassen sich
typisiert auslesen (`.extract().as(BlogResponse.class)`).

| Testklasse           | Art                           | Prüft                                                                                                  |
| -------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------ |
| `BlogResourceTest`   | `@QuarkusTest` + REST Assured | Alle Endpunkte, DTO-Vertrag (nur freigegebene Felder, keine Mass Assignment), Validierung, Fehler     |
| `CorsTest`           | `@QuarkusTest` + REST Assured | Preflight von erlaubter Origin, `Location` freigegeben, fremde Origin → `403`                          |
| `BlogServiceTest`    | `@QuarkusTest`                | Fachlogik: Suche, Sortierung, Update, Löschen, Kommentare                                              |
| `BlogRepositoryTest` | `@QuarkusTest`                | Panache-Datenzugriff                                                                                   |
| `BlogMapperTest`     | reiner JUnit-Test             | Mapping Entität ↔ DTO, läuft ohne Quarkus und Datenbank                                                |

> **_HINWEIS:_** Die Datenbank wird über Quarkus Dev Services bereitgestellt – dafür muss Docker
> laufen. Alternativ eine `quarkus.datasource.jdbc.url` in
> `src/main/resources/application.properties` setzen.

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

You can then execute your native executable with: `./target/ds1-0.1-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST JSON-B ([guide](https://quarkus.io/guides/rest#json-serialisation)): JSON-B serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
