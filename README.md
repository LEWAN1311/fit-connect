# FitConnect — Réservation et paiement de cours de sport (microservices)

FitConnect permet aux utilisateurs de réserver des cours dans différentes salles de sport partenaires.
Le projet est un **multi-module Maven** Spring Boot 3.3 / Spring Cloud 2023.0 composé de 3 briques
d'infrastructure et de 4 microservices métier.

## Sommaire

1. [Architecture](#architecture)
2. [Stack technique](#stack-technique)
3. [Structure du projet](#structure-du-projet)
4. [Démarrage](#démarrage)
5. [API REST](#api-rest)
6. [Workflow de réservation (pattern Saga)](#workflow-de-réservation-pattern-saga)
7. [Verrouillage optimiste](#verrouillage-optimiste)
8. [Clients Feign et Circuit Breaker](#clients-feign-et-circuit-breaker)
9. [Scheduler](#scheduler)
10. [Configuration centralisée](#configuration-centralisée)
11. [Tests](#tests)
12. [Collection Postman](#collection-postman)
13. [Choix de conception et limites](#choix-de-conception-et-limites)
14. [Livrables](#livrables)

---

## Architecture

```
                         ┌───────────────────┐
                         │   eureka-server   │  :8761  (annuaire de services)
                         └─────────▲─────────┘
            ┌──────────────────────┼───────────────────────┬──────────────────────┐
┌───────────┴─────────┐ ┌──────────┴──────────┐ ┌──────────┴──────────┐ ┌─────────┴───────────┐
│     api-gateway     │ │    class-service    │ │   booking-service   │ │  payment-service    │
│        :8080        │─▶        :8091        │ │        :8092        │ │       :8093         │
│  4 routes lb://...  │ │ CRUD cours, @Version│ │ Orchestrateur Saga  │ │ Paiements simulés   │
└──────────┬──────────┘ │ H2 "classdb"        │ │ Feign + CB + cron   │ │ H2 "paymentdb"      │
           │            └──────────▲──────────┘ │ H2 "bookingdb"      │ └─────────▲───────────┘
           │                       │ Feign      └──┬───────┬──────────┘           │ Feign
           │                       └───────────────┘       └──────────────────────┤
           │                                               ┌──────────────────────▼───┐
           │                                               │  notification-service    │
           ▼                                               │  :8094  H2 "notificationdb"│
   config-server :8888  (config-repo/*.yml)                └──────────────────────────┘
```

| Service                | Port | Rôle                                                | Base H2          |
|------------------------|------|-----------------------------------------------------|------------------|
| `eureka-server`        | 8761 | Découverte de services                              | —                |
| `config-server`        | 8888 | Configuration centralisée (profil `native`)         | —                |
| `api-gateway`          | 8080 | Point d'entrée unique, routage `lb://`              | —                |
| `class-service`        | 8091 | Gestion des cours, verrouillage optimiste           | `classdb`        |
| `booking-service`      | 8092 | Réservations, orchestration Saga, scheduler         | `bookingdb`      |
| `payment-service`      | 8093 | Paiements (simulation) et remboursements            | `paymentdb`      |
| `notification-service` | 8094 | Envoi d'emails/SMS (simulé) et historique           | `notificationdb` |

Chaque service expose la console H2 sur `/h2-console` (JDBC URL `jdbc:h2:mem:<base>`, user `sa`, mot de passe vide)
et Swagger UI sur `/swagger-ui.html`.

## Stack technique

- Java 17, Maven (multi-module), Spring Boot 3.3.2, Spring Cloud 2023.0.3
- Spring Web, Spring Data JPA, Bean Validation, H2 en mémoire
- Spring Cloud Netflix Eureka, Spring Cloud Config (native), Spring Cloud Gateway
- Spring Cloud OpenFeign + client Apache HttpClient 5 (`feign-hc5`, nécessaire pour les requêtes `PATCH`)
- Spring Cloud Circuit Breaker Resilience4j
- JUnit 5, Mockito, MockMvc, AssertJ
- springdoc-openapi (Swagger UI), Docker Compose

## Structure du projet

```
fit-connect/
├── pom.xml                         # POM parent (modules + BOM Spring Cloud)
├── docker-compose.yml              # Bonus : les 7 conteneurs
├── postman/FitConnect.postman_collection.json
├── eureka-server/
├── config-server/
│   └── config-repo/                # application.yml, api-gateway.yml + 1 fichier par service
├── api-gateway/
├── class-service/      org.formation.classservice   {entity, dto, repository, service, controller, exception, validation, config}
├── booking-service/    org.formation.booking        {entity, dto, repository, service, controller, exception, client, scheduler, config}
├── payment-service/    org.formation.payment        {entity, dto, repository, service, controller, exception}
└── notification-service/ org.formation.notification {entity, dto, repository, service, controller, exception}
```

## Démarrage

### Prérequis

- JDK 17 ou plus, Maven 3.9+
- (optionnel) Docker + Docker Compose

### Compilation et tests

```bash
mvn clean install          # compile les 7 modules et exécute tous les tests
```

### Lancement local (ordre important)

Depuis la racine du projet, dans des terminaux séparés :

```bash
java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar          # 1. annuaire
java -jar config-server/target/config-server-1.0.0-SNAPSHOT.jar          # 2. configuration
java -jar class-service/target/class-service-1.0.0-SNAPSHOT.jar          # 3. services métier
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar              # 4. passerelle
```

(ou `mvn spring-boot:run` dans chaque module). Le config-server trouve `config-repo` aussi bien depuis la racine
que depuis son propre dossier.

> **Délai Eureka** : un service enregistré n'est visible par les autres qu'après le rafraîchissement de leur
> cache (~30 s). Juste après le démarrage, un appel peut donc renvoyer `503 Service Unavailable` : il suffit
> de réessayer quelques secondes plus tard.

Vérifications : dashboard Eureka sur http://localhost:8761 (5 applications enregistrées),
routes du gateway sur http://localhost:8080/actuator/gateway/routes.

`class-service` insère 4 cours de démonstration au démarrage (dates relatives à la date du jour).

### Lancement avec Docker Compose (bonus)

```bash
mvn clean package -DskipTests
docker compose up --build
```

Les URLs Eureka et Config sont injectées via `EUREKA_URI` et `CONFIG_SERVER_URI`.
Les clients utilisent `SPRING_CLOUD_CONFIG_FAIL_FAST=true` + `restart: on-failure` : tant que le
config-server n'est pas prêt, le conteneur échoue puis redémarre, ce qui garantit par exemple que le gateway
démarre toujours avec ses routes.

## API REST

Toutes les routes sont accessibles via le gateway `http://localhost:8080`.
Les erreurs renvoient un JSON homogène :
`{"timestamp", "status", "error", "message", "path", "details"}` (`details` = erreurs de validation par champ).

### class-service — `/api/classes`

| Méthode  | URL                                    | Description                                              |
|----------|----------------------------------------|----------------------------------------------------------|
| `GET`    | `/api/classes`                         | Liste filtrée et paginée                                 |
| `GET`    | `/api/classes/{id}`                    | Détail d'un cours                                        |
| `POST`   | `/api/classes`                         | Créer un cours (`201`)                                   |
| `PUT`    | `/api/classes/{id}`                    | Mettre à jour un cours                                   |
| `DELETE` | `/api/classes/{id}`                    | Supprime si aucun inscrit, sinon annule (`CANCELLED`) — `204` |
| `PATCH`  | `/api/classes/{id}/increment?spots=N`  | Appelé par booking-service — `409` si complet            |
| `PATCH`  | `/api/classes/{id}/decrement?spots=N`  | Appelé par booking-service                               |
| `GET`    | `/api/classes/search`                  | Recherche par `date`, `category`, `level`, `location`    |

Filtres de `GET /api/classes` : `category`, `level`, `dateFrom`, `dateTo` (format `yyyy-MM-dd`, inclusifs),
`location` et `instructor` (contient, insensible à la casse), `status`.
Pagination : `?page=0&size=10&sort=dateTime,asc`. Réponse : `{"content": [...], "page": {size, number, totalElements, totalPages}}`.

Validation (`FitnessClassRequest`) : `name` ≥ 3 caractères, champs texte obligatoires, `durationMinutes` ∈ {30, 45, 60, 90}
(contrainte personnalisée `@ValidDuration`), `maxParticipants` entre 5 et 30, `price` ≥ 5.00, `dateTime` dans le futur.
`currentParticipants` n'est modifiable que par increment/decrement ; un `PUT` ne peut pas baisser `maxParticipants`
sous le nombre d'inscrits (`409`).

Exemple :

```json
POST /api/classes
{
  "name": "Yoga Vinyasa", "description": "Enchaînements fluides", "instructor": "Marie",
  "gymLocation": "Paris 11e", "category": "YOGA", "level": "BEGINNER", "durationMinutes": 60,
  "maxParticipants": 20, "price": 15.00, "dateTime": "2026-10-10T18:00:00"
}
```

### booking-service — `/api/bookings`

| Méthode | URL                             | Description                                         |
|---------|---------------------------------|-----------------------------------------------------|
| `GET`   | `/api/bookings[?status=...]`    | Lister les réservations                             |
| `GET`   | `/api/bookings/{id}`            | Récupérer une réservation                           |
| `GET`   | `/api/bookings/user/{userId}`   | Réservations d'un utilisateur                       |
| `POST`  | `/api/bookings`                 | Créer une réservation (`201`, `PENDING_PAYMENT`)    |
| `PATCH` | `/api/bookings/{id}/confirm`    | Payer et confirmer                                  |
| `PATCH` | `/api/bookings/{id}/cancel`     | Annuler (remboursement si payée)                    |
| `PATCH` | `/api/bookings/{id}/complete`   | Marquer comme terminée (`CONFIRMED` → `COMPLETED`)  |
| `GET`   | `/api/bookings/expired`         | Réservations en attente dont la deadline est passée |

```json
POST /api/bookings
{ "userId": 1, "userEmail": "john@example.com", "userName": "John Doe", "classId": 1, "numberOfSpots": 2 }

PATCH /api/bookings/{id}/confirm
{ "paymentMethod": "CREDIT_CARD", "cardLastFour": "1234", "transactionId": "txn_123456" }
```

`numberOfSpots` entre 1 et 4. Référence générée au format `BK-XXXXX`.
Les champs `className`, `classDate`, `instructor`, `price`, `userEmail`, `userName` sont des **snapshots** pris au
moment de la réservation. `totalAmount = price × numberOfSpots`, `paymentDeadline = bookingDate + 1 h`,
`cancellationDeadline = classDate − 24 h`.

### payment-service — `/api/payments`

| Méthode | URL                                  | Description                                     |
|---------|--------------------------------------|-------------------------------------------------|
| `POST`  | `/api/payments`                      | Traiter un paiement (appelé par booking-service) |
| `GET`   | `/api/payments/{id}`                 | Récupérer un paiement                           |
| `GET`   | `/api/payments/booking/{bookingId}`  | Dernier paiement d'une réservation              |
| `POST`  | `/api/payments/{id}/refund`          | Rembourser (uniquement un paiement `SUCCESS`)   |
| `GET`   | `/api/payments/user/{userId}`        | Historique des paiements d'un utilisateur       |

Simulation : montant **< 100 €** → `SUCCESS`, montant **≥ 100 €** → `FAILED` (seuil configurable
`payment.simulation.max-accepted-amount`). Chaque tentative est historisée (référence `PAY-XXXXX`).
`cardLastFour` (4 chiffres) est obligatoire pour `CREDIT_CARD` / `DEBIT_CARD`. Un second paiement d'une réservation
déjà payée est refusé (`409`).

### notification-service — `/api/notifications`

| Méthode | URL                                 | Description                                   |
|---------|-------------------------------------|-----------------------------------------------|
| `POST`  | `/api/notifications`                | Envoyer une notification (appelé par les services) |
| `GET`   | `/api/notifications/user/{userId}`  | Historique des notifications                  |
| `GET`   | `/api/notifications/pending`        | Notifications en attente                      |
| `PATCH` | `/api/notifications/{id}/retry`     | Réessayer l'envoi (`409` si déjà `SENT`)      |

L'envoi est simulé (journalisé, adresse email masquée dans les logs) : `SENT` si un destinataire est fourni,
`FAILED` sinon. La notification est d'abord persistée en `PENDING`, puis passe à `SENT` / `FAILED`.

## Workflow de réservation (pattern Saga)

`booking-service` est l'**orchestrateur** du Saga. Principe retenu : **les appels distants sont faits avant la
mise à jour locale**. En cas d'erreur, la réservation reste dans son état précédent et l'opération peut être rejouée.

### Cas 1 — Réservation réussie (`POST /api/bookings`)

1. **Vérification du cours** : `GET /api/classes/{id}` — le cours existe (`404` sinon), est `SCHEDULED`, dans le futur,
   et a assez de places (`409` sinon). Capture du snapshot et calcul de `totalAmount`.
2. **Réservation des places** : `PATCH /api/classes/{id}/increment?spots=N` — `409` si
   `currentParticipants + spots > maxParticipants` ou si un conflit de version survient.
3. **Création** de la réservation `PENDING_PAYMENT` (+ `paymentDeadline`, `cancellationDeadline`, `bookingReference`).
   *Compensation* : si la persistance échoue, les places sont rendues (`decrement`).
4. **Notification** `BOOKING_CONFIRMATION` : « Votre réservation est en attente de paiement. Payez avant … ».
5. **Réponse** `201 Created`, statut `PENDING_PAYMENT`.

### Cas 2 — Plus de places disponibles

Si class-service répond `409` à l'étape 2 (quelqu'un a réservé entre-temps), aucune réservation n'a été créée :
**aucune compensation n'est nécessaire**. Réponse `409 Conflict : "Plus de places disponibles pour ce cours"`.

### Cas 3 — Paiement (`PATCH /api/bookings/{id}/confirm`)

1. Vérifie `status == PENDING_PAYMENT` et `paymentDeadline` non dépassée → sinon `409` (paiement expiré).
2. `POST /api/payments` avec `bookingId`, `amount`, `paymentMethod`…
3. Paiement `SUCCESS` → réservation `CONFIRMED`.
   Paiement `FAILED` → **la réservation reste `PENDING_PAYMENT`** pour permettre une nouvelle tentative avant la
   deadline (option proposée par l'énoncé) ; réponse `402 Payment Required`. Passé la deadline, le scheduler
   l'annule et libère les places.
4. Notification `PAYMENT_CONFIRMATION`.
5. Réponse `200 OK`.

### Cas 4 — Annulation (`PATCH /api/bookings/{id}/cancel`)

1. Refus (`409`) si la réservation est déjà `CANCELLED` / `COMPLETED` / `NO_SHOW` ou si `cancellationDeadline`
   est dépassée (moins de 24 h avant le cours).
2. Si `CONFIRMED` : `GET /api/payments/booking/{id}` puis `POST /api/payments/{id}/refund`
   (ignoré si déjà `REFUNDED`, ce qui rend l'annulation rejouable après une panne).
   Puis libération des places : `PATCH /api/classes/{id}/decrement`.
3. Statut `CANCELLED`.
4. Notification `BOOKING_CANCELLED`.
5. Réponse `200 OK`.

### Codes de retour

| Code  | Cas                                                                          |
|-------|------------------------------------------------------------------------------|
| `400` | Validation (`details` par champ), JSON illisible                            |
| `402` | Paiement refusé par payment-service                                          |
| `404` | Cours / réservation / paiement introuvable                                   |
| `409` | Surréservation, conflit de version, paiement expiré, annulation hors délais, transition d'état invalide |
| `503` | Service distant indisponible ou circuit ouvert                               |

## Verrouillage optimiste

`FitnessClass` porte un champ `@Version private Long version;`. L'incrément passe par la méthode d'entité
`incrementParticipants(int spots)` qui lève `NoSpotsAvailableException` si la capacité est dépassée, appelée depuis
une méthode `@Transactional` du service avec `saveAndFlush`.

Si deux réservations concurrentes lisent la même version, JPA rejette la seconde écriture
(`ObjectOptimisticLockingFailureException`), traduite en `409 Conflict`. Côté booking-service ce `409` devient
`NoSpotsAvailableException` : il est impossible de dépasser `maxParticipants`. Le test
`FitnessClassIntegrationTest.shouldRejectConcurrentUpdate_thanksToOptimisticLocking` démontre ce comportement.

`Booking` possède aussi un `@Version` pour éviter qu'une annulation par le scheduler et un paiement simultané
s'écrasent.

## Clients Feign et Circuit Breaker

booking-service appelle les 3 autres services via des clients Feign résolus par Eureka (`lb://`) :
`ClassServiceClient`, `PaymentServiceClient`, `NotificationServiceClient`.

- `spring.cloud.openfeign.circuitbreaker.enabled=true` enveloppe chaque méthode dans un circuit breaker
  Resilience4j configuré dans `CircuitBreakerConfiguration` : fenêtre de 10 appels, ouverture à 50 % d'échecs,
  15 s en état ouvert, timeout 5 s. Les erreurs 4xx (`FeignClientException`) sont **ignorées** par le circuit :
  un `409` métier ne signifie pas que le service est en panne.
- Chaque client a une `FallbackFactory` :
  - **class / payment** (critiques) : `RemoteErrorTranslator` propage l'erreur métier du service distant avec son
    message (`404`, `409`, `400`) ou lève `ServiceUnavailableException` (`503`) en cas de panne, timeout ou circuit ouvert ;
  - **notification** (non critique) : le fallback journalise et le Saga continue (dégradation gracieuse).
- État des circuits : `GET http://localhost:8092/actuator/circuitbreakers`.

Vérifié manuellement : avec notification-service arrêté, une réservation est créée normalement ; avec
payment-service arrêté, `confirm` renvoie `503`, la réservation reste `PENDING_PAYMENT` et le circuit
`processPayment` passe à `OPEN` après quelques échecs.

## Scheduler

`BookingScheduler` (booking-service), activé par `SchedulingConfiguration` (`booking.scheduler.enabled`) :

1. **Expiration des paiements** — toutes les 5 min : réservations `PENDING_PAYMENT` avec `paymentDeadline < now()`
   → libération des places, statut `CANCELLED`, notification `BOOKING_CANCELLED`. Si class-service est indisponible,
   la réservation est laissée telle quelle et retraitée au passage suivant.
2. **Rappel des cours** — toutes les 15 min : réservations `CONFIRMED` dont le cours a lieu dans les prochaines 24 h
   → notification `BOOKING_REMINDER` (un seul rappel grâce au champ `reminderSent`).

## Configuration centralisée

`config-server/config-repo/` :

| Fichier                    | Contenu                                                                 |
|----------------------------|-------------------------------------------------------------------------|
| `application.yml`          | Partagé : URL Eureka, actuator                                          |
| `api-gateway.yml`          | Les 4 routes `lb://` (`/api/classes/**`, `/api/bookings/**`, `/api/payments/**`, `/api/notifications/**`) |
| `class-service.yml`        | Port 8091, H2 `classdb`                                                 |
| `booking-service.yml`      | Port 8092, H2 `bookingdb`, Feign/circuit breaker, délais métier, fréquences du scheduler |
| `payment-service.yml`      | Port 8093, H2 `paymentdb`, seuil de simulation                          |
| `notification-service.yml` | Port 8094, H2 `notificationdb`                                          |

Paramètres métier modifiables (booking-service) :

```yaml
booking:
  payment-deadline-minutes: 60     # délai de paiement
  cancellation-deadline-hours: 24  # annulation gratuite jusqu'à 24 h avant le cours
  scheduler:
    expiration-rate-ms: 300000     # 5 min
    reminder-rate-ms: 900000       # 15 min
```

## Tests

```bash
mvn test                        # tous les modules
mvn -pl booking-service test    # un seul module
```

Les tests utilisent le profil `test` (config-server, Eureka et scheduler désactivés, bases H2 dédiées).

| Module               | Classe                                  | Type        | Contenu |
|----------------------|-----------------------------------------|-------------|---------|
| booking-service      | `BookingServiceTest`                    | Unitaire (Mockito) | `shouldCreateBooking_whenSpotsAvailable`, `shouldThrowException_whenNoSpotsAvailable`, `shouldCancelBookingAndRefund_whenWithinDeadline`, + réservation concurrente, annulation hors délais, paiement accepté / refusé / expiré |
| booking-service      | `BookingFlowIntegrationTest`            | Intégration (SpringBootTest + MockMvc + H2, services distants simulés) | `shouldCompleteFullBookingFlow`, `shouldCancelExpiredBookings`, + annulation avec remboursement, 409, 402, 503, 400 |
| booking-service      | `ClassServiceClientFallbackFactoryTest` | Unitaire    | Traduction des erreurs Feign (409 → surréservation, 404, panne → 503) |
| class-service        | `FitnessClassTest`                      | Unitaire    | Incrément / décrément et capacité |
| class-service        | `FitnessClassIntegrationTest`           | Intégration | CRUD, filtres + pagination, validation, 409, **verrouillage optimiste** |
| payment-service      | `PaymentIntegrationTest`                | Intégration | Règle < 100 € / ≥ 100 €, double paiement, remboursement |
| notification-service | `NotificationIntegrationTest`           | Intégration | Envoi, échec sans destinataire, retry |

Résultat actuel : **34 tests, 0 échec**.

## Collection Postman

`postman/FitConnect.postman_collection.json` (variable `baseUrl` = `http://localhost:8080`).
Les requêtes s'enchaînent via des variables de collection alimentées par les scripts de test (`classId`,
`bookingId`, …) et les dates sont calculées automatiquement : lancer **Run collection** dans l'ordre.

1. **Gestion des cours** — créer, lister (pagination), filtrer (`category=YOGA&level=INTERMEDIATE`, localisation), rechercher, détail, mise à jour
2. **Réservation** — réserver 2 places, vérifier `PENDING_PAYMENT`, vérifier les places prises
3. **Paiement** — payer (carte valide), vérifier `CONFIRMED`, consulter le paiement
4. **Annulation (dans les délais)** — annuler, vérifier le remboursement (`REFUNDED`) et les places libérées
5. **Scénarios d'erreur** — surréservation (`409`), paiement refusé ≥ 100 € (`402`), paiement sur réservation non payable (`409`), annulation hors délais sur un cours dans 5 h (`409`), requête invalide (`400`), réservation inexistante (`404`)
6. **Notifications** — historique, en attente, échec et retry

Exécution en ligne de commande : `npx newman run postman/FitConnect.postman_collection.json`
(exécutée sur la stack complète : 53 assertions, 0 échec).

> Tester une **vraie expiration de paiement** : mettre `booking.payment-deadline-minutes: 1` dans
> `config-repo/booking-service.yml`, redémarrer booking-service, réserver, attendre 1 minute puis appeler `confirm`
> → `409 "Paiement expire"`. Le scheduler annule ensuite la réservation et libère les places.

## Choix de conception et limites

- **Paiement refusé** : la réservation reste `PENDING_PAYMENT` (nouvelle tentative possible) plutôt que d'être
  annulée immédiatement ; l'expiration est gérée par le scheduler.
- **Suppression d'un cours** : suppression physique s'il n'y a aucun inscrit, sinon passage à `CANCELLED` afin de
  conserver la cohérence avec les réservations existantes. La notification `CLASS_CANCELLED` n'est pas envoyée
  automatiquement aux inscrits (class-service ne connaît pas les réservations) ; ce serait une évolution naturelle
  via un événement ou un endpoint dédié dans booking-service.
- **Cohérence** : le Saga est orchestré de façon synchrone. Si class-service tombe juste après un remboursement,
  l'annulation échoue en `503` mais peut être rejouée (remboursement idempotent). Une version production utiliserait
  une messagerie (Kafka/RabbitMQ) et un outbox pour garantir la livraison.
- **Statut `NO_SHOW`** : présent dans l'énumération, sans endpoint dédié (non demandé par l'énoncé).
- **Données** : bases H2 en mémoire, réinitialisées à chaque redémarrage ; emails et SMS simulés.
- **Sécurité** : pas d'authentification ; `userId`, `userEmail` et `userName` sont fournis par le client.

## Livrables

- [x] Code source (7 modules Maven)
- [x] Fichiers de configuration dans `config-repo`
- [x] Routes dans `api-gateway.yml`
- [x] Clients Feign avec Circuit Breaker
- [x] Pattern Saga complet (réservation → paiement → confirmation / annulation)
- [x] Verrouillage optimiste dans `class-service`
- [x] Scheduler pour l'expiration des paiements (+ rappels J-1)
- [x] Collection Postman complète
- [x] Tests unitaires et d'intégration
- [x] README détaillé
- [x] Docker Compose (bonus)
