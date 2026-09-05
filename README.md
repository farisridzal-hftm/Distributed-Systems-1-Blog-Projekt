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

Die Eingaben werden mit Bean Validation geprüft, bevor sie den Service erreichen:

| Feld               | Regel                            | Meldung                                   |
| ------------------ | -------------------------------- | ----------------------------------------- |
| `Blog.title`       | `@NotBlank`, `@Size(max = 120)`  | "Title must not be blank"                 |
| `Blog.content`     | `@NotBlank`                      | "Content must not be blank"               |
| `BlogComment.text` | `@NotBlank`, `@Size(max = 1000)` | "Text must not be blank"                  |
| `X-Max-Results`    | `@Positive`                      | "X-Max-Results must be a positive number" |

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
| Mapper     | `ch.hftm.rest.mapper` | Exceptions → einheitliche Fehler-Responses    |
| Service    | `ch.hftm.service`     | Fachlogik, Transaktionen, Sortier-/Suchregeln |
| Repository | `ch.hftm.repository`  | Panache-Datenzugriff                          |
| Model      | `ch.hftm.model`       | JPA-Entitäten inkl. Validierungsregeln        |
| Exceptions | `ch.hftm.exception`   | Fachliche Fehler (`BlogNotFoundException`, …) |

### Vorerst ausgeklammert

Bewusst noch nicht umgesetzt, um den Umfang klein zu halten:

- **DTOs / Mapping**: Die JPA-Entitäten werden direkt serialisiert. Damit daraus keine
  Endlosschleife und kein Lazy-Loading-Fehler entsteht, sind `Blog.getComments()` und
  `BlogComment.getBlog()` mit `@JsonbTransient` ausgeblendet. Kommentare sind deshalb nur über
  `/blogs/{id}/comments` erreichbar. DTO-Mapping folgt in der nächsten Einheit.
- **`PATCH`**: Es gibt nur vollständige Updates via `PUT`.
- **Kommentare einzeln bearbeiten/löschen**: `PUT`/`DELETE` auf `/blogs/{id}/comments/{commentId}`
  fehlt noch; Kommentare verschwinden aktuell nur zusammen mit ihrem Blog.
- **Pagination**: Statt `page`/`size` gibt es vorerst nur den Header `X-Max-Results`.
- **Autor / Zeitstempel**: Blogs und Kommentare haben noch keinen Autor und kein Erstelldatum.
- **Authentifizierung und Autorisierung**: Alle Endpunkte sind offen.
- **OpenAPI-Dokumentation**: Noch keine `quarkus-smallrye-openapi`-Integration.

## Tests

```shell script
./mvnw test
```

Die Tests laufen mit `@QuarkusTest` und Rest-Assured. Abgedeckt sind unter anderem der
`PUT`-Request, die Query- und Header-Parameter, die 404-Fehler-Response und zwei ungültige
Requests, die von Bean Validation abgelehnt werden.

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
