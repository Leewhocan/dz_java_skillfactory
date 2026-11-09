# Hotel Booking - микросервисное приложение (Java 21 / Spring Boot 3)

Четыре Spring Boot приложения, объединённые Eureka и
Spring Cloud Gateway, с JWT-аутентификацией (booking-service выдаёт токены, hotel-service их
проверяет), сагой бронирования с компенсацией и защитой от гонок при пересечении дат.

```
                       ┌────────────────────┐
   client  ────────▶   │   api-gateway :8080 │   (Spring Cloud Gateway, lb:// через Eureka)
                       └─────────┬──────────┘
                                 │
              ┌──────────────────┴───────────────────┐
              ▼                                      ▼
   ┌──────────────────────┐              ┌───────────────────────┐
   │ booking-service :8082│  Feign +     │  hotel-service :8081  │
   │ users, JWT, bookings │──X-Internal─▶│  hotels, rooms,       │
   │ H2: bookingdb        │   -Token     │  room_blocks          │
   └──────────────────────┘              └───────────────────────┘
              │                                      │
              └──────────────┬───────────────────────┘
                             ▼
                  ┌────────────────────┐
                  │ eureka-server :8761 │
                  └────────────────────┘
```

## Модули

| Модуль            | Порт | Ответственность                                                        |
|-------------------|------|------------------------------------------------------------------------|
| `eureka-server`   | 8761 | Service registry                                                       |
| `hotel-service`   | 8081 | Каталог отелей/номеров, счётчик `times_booked`, блокировки периодов    |
| `booking-service` | 8082 | Пользователи, выдача/проверка JWT, бронирования, сага с компенсацией   |
| `api-gateway`     | 8080 | Единая точка входа, маршрутизация `lb://`, запрет внутренних эндпоинтов |

## Технологии

* Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x
* Spring Security (stateless, JJWT 0.12.x), BCrypt
* Spring Data JPA + H2 (in-memory, `ddl-auto: update`)
* Spring Cloud OpenFeign + LoadBalancer + Eureka Client
* springdoc-openapi 2.8.x (`springdoc-openapi-starter-webmvc-ui`) - Swagger UI в hotel- и booking-service
* MapStruct 1.6.x (booking-service: entity → DTO), Lombok

## Требования

* JDK 21+
* Maven 3.9+
* Свободные порты 8761, 8081, 8082, 8080

## Сборка

```bash
mvn clean package            # все модули, тесты выключены по умолчанию (src/test пуст)
mvn -DskipTests package      # только артефакты
```

## Запуск: строгий порядок

Сервисы регистрируются в Eureka, поэтому стартуем **Eureka → Hotel → Booking → Gateway**.
Первый запрос через гейтвей может вернуть 503, пока Eureka не отдаст реестр (5-15 секунд).

### Вариант 1 - скрипты (запуск в фоне, логи в `./logs`)

```bash
bash scripts/start-all.sh    # стартует все 4 сервиса и ждёт их готовности
bash scripts/e2e-test.sh     # сквозной сценарий через http://localhost:8080
bash scripts/race-test.sh 5  # параллельные брони одного периода: 1 победитель, остальные 409
bash scripts/stop-all.sh     # остановить всё
```

### Вариант 2 - вручную, каждый сервис в своём терминале

```bash
# 1. Eureka
mvn -pl eureka-server spring-boot:run

# 2. hotel-service (ждать "Started HotelServiceApplication")
mvn -pl hotel-service spring-boot:run

# 3. booking-service (ждать "Started BookingServiceApplication")
mvn -pl booking-service spring-boot:run

# 4. api-gateway
mvn -pl api-gateway spring-boot:run
```

### Вариант 3 - собранные jar'ы

```bash
mvn -DskipTests package
java -jar eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar
java -jar hotel-service/target/hotel-service-1.0.0-SNAPSHOT.jar
java -jar booking-service/target/booking-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

### Проверка, что всё поднялось

```bash
curl http://localhost:8761/eureka/apps          # в списке: HOTEL-SERVICE, BOOKING-SERVICE, API-GATEWAY
curl http://localhost:8080/actuator/health      # {"status":"UP"}
curl -i http://localhost:8080/api/hotels        # 401 (нужен токен) - значит security работает
```

## Swagger / OpenAPI

| Сервис         | Swagger UI                              | OpenAPI JSON                     |
|----------------|-----------------------------------------|----------------------------------|
| hotel-service  | http://localhost:8081/swagger-ui.html   | http://localhost:8081/v3/api-docs |
| booking-service| http://localhost:8082/swagger-ui.html   | http://localhost:8082/v3/api-docs |

`/swagger-ui/**`, `/swagger-ui.html` и `/v3/api-docs/**` открыты в обоих `SecurityConfig`.
В UI есть кнопка **Authorize**: вставьте `accessToken`, полученный из `POST /api/user/auth`.

## Переменные окружения

| Переменная                 | По умолчанию                                     | Где используется            |
|----------------------------|--------------------------------------------------|-----------------------------|
| `JWT_SECRET`               | `change-me-development-only-secret-key-0123456789` | оба сервиса (**обязан совпадать**) |
| `INTERNAL_API_TOKEN`       | `change-me-internal-service-token`               | hotel- и booking-service (**обязан совпадать**) |
| `INTERNAL_API_HEADER`      | `X-Internal-Token`                               | имя заголовка               |
| `BOOTSTRAP_ADMIN_USERNAME` | `admin`                                          | booking-service             |
| `BOOTSTRAP_ADMIN_PASSWORD` | `admin123`                                       | booking-service             |

> В проде секреты задаются только через окружение/secret manager, дефолты в `application.yml`
> предназначены исключительно для локального запуска.

## Безопасность

### Кто выдаёт токены

`booking-service` — единственный эмитент JWT (HS256, `sub`=username, `uid`=id, `role`=ADMIN/USER,
TTL 1 час). `hotel-service` работает как **resource server**: он не содержит пользователей, а только
проверяет подпись и клеймы того же самого секрета (`security.jwt.secret` должен совпадать
байт-в-байт). Никакого похода в booking-service за проверкой токена нет.

### Матрица доступа (hotel-service)

| Endpoint                                     | Аноним | USER | ADMIN | SERVICE (`X-Internal-Token`) |
|----------------------------------------------|--------|------|-------|------------------------------|
| `GET /api/hotels`, `GET /api/rooms*`         | 401    | 200  | 200   | 200                          |
| `POST /api/hotels`, `POST /api/rooms`        | 401    | 403  | 201   | —                            |
| `POST /api/rooms/{id}/confirm-availability`  | 401    | 403  | 403   | 200                          |
| `POST /api/rooms/{id}/release`               | 401    | 403  | 403   | 204                          |
| `/swagger-ui/**`, `/v3/api-docs/**`          | 200    | 200  | 200   | 200                          |

### Внутренний контур (saga)

Эндпоинты `confirm-availability` и `release` не входят в публичный API:

1. **Gateway** объявляет маршрут `internal-saga-blocked` (order `-1`) на
   `/api/rooms/*/confirm-availability` и `/api/rooms/*/release` и отвечает `403` — снаружи они
   недоступны даже с ADMIN-токеном.
2. **hotel-service** требует заголовок `X-Internal-Token` со значением `security.internal.token`
   (сравнение через `MessageDigest.isEqual`, только `POST`). Без секрета — `403`, даже с валидным JWT.
3. **booking-service** добавляет секрет в каждый вызов через Feign-интерсептор и дополнительно
   прокидывает JWT вызывающего пользователя (для аудита).

`SERVICE` — служебная роль: людям не выдаётся, её ставит только `InternalTokenAuthenticationFilter`
после успешной проверки секрета. Фильтр применяется до JWT-фильтра и перекрывает его, поэтому
порядок регистрации фильтров не влияет на результат.

## Сага бронирования и защита от гонок

```
POST /api/booking
  ├─ (autoSelect) GET  hotel-service /api/rooms/recommend     -> лучший свободный номер
  ├─ INSERT booking (status=PENDING)                          -> локальная транзакция
  ├─ POST hotel-service /api/rooms/{id}/confirm-availability  (X-Internal-Token)
  │     ├─ 200        -> status=CONFIRMED, ответ 201
  │     ├─ 409        -> status=CANCELLED (без release!), ответ 409
  │     └─ timeout/5xx -> status=CANCELLED + компенсирующий release, ответ 502
  └─ DELETE /api/booking/{id} -> статус CANCELLED + release (204)
```

Ключевые решения:

* **Полуоткрытый интервал** `[startDate, endDate)`: пересечение =
  `start < otherEnd && end > otherStart`, поэтому брони 10→12 и 12→14 не конфликтуют.
* **Пессимистическая блокировка**: `RoomRepository.findByIdForUpdate` берёт строку номера с
  `PESSIMISTIC_WRITE` **до** проверки пересечений, поэтому два параллельных запроса не могут
  одновременно увидеть «свободно». Блокировка строки номера работает и на пустом наборе блокировок —
  в отличие от `SELECT ... FOR UPDATE` по `room_blocks`, который на пустом результате ничего не
  блокирует.
* **Страховка на уровне БД**: unique constraint `(room_id, start_date, end_date)` в `room_blocks`;
  `DataIntegrityViolationException` маппится в `409`.
* **Компенсация идемпотентна и не трогает чужое**: `release` вызывается только если блок реально
  мог быть создан (timeout/5xx или отмена подтверждённой брони). При `409` блок не создавался,
  поэтому `release` не вызывается — иначе компенсация удалила бы блок брони, которая эти даты
  действительно держит.
* **Идемпотентность запроса**: `requestId` (в теле или заголовок `Idempotency-Key`) — при повторе
  возвращается уже обработанная бронь. После неуспешного подтверждения ключ сбрасывается, чтобы
  клиент мог повторить тот же запрос.


## Проверка работоспособности

### Сквозной сценарий (43 проверки)

```bash
bash scripts/e2e-test.sh
```

Что проверяется: регистрация USER, bootstrap-ADMIN, создание ADMIN, создание отеля/номера,
бронирование с `autoSelect`, рост `timesBooked` 0→1, отказ `409` на тот же и на пересекающийся
период, успех на непересекающийся период, отмена (`204`) и повторный захват периода, матрица
доступа (401/403), внутренние эндпоинты (`403` без секрета / `200` и `204` с секретом), доступность
Swagger. Скрипт завершается с кодом `1`, если хотя бы одна проверка упала.

### Проверка защиты от гонок

```bash
bash scripts/race-test.sh 5     # 5 одновременных клиентов на один номер и период
```

Ожидаемый результат: ровно **один** `201 CONFIRMED`, остальные `409`, `timesBooked == 1`
(двойного бронирования нет).

## Полезные curl-примеры

```bash
GW=http://localhost:8080

# токен
TOKEN=$(curl -s -X POST $GW/api/user/auth -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# каталог
curl -s $GW/api/hotels -H "Authorization: Bearer $TOKEN"
curl -s $GW/api/rooms/recommend -H "Authorization: Bearer $TOKEN"

# создание отеля и номера (ADMIN)
curl -s -X POST $GW/api/hotels -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Grand","address":"Tverskaya 1"}'
curl -s -X POST $GW/api/rooms -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"hotelId":1,"number":"101"}'

# бронирование и отмена
curl -s -X POST $GW/api/booking -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"roomId":1,"startDate":"2027-01-10","endDate":"2027-01-12"}'
curl -i -X DELETE $GW/api/booking/1 -H "Authorization: Bearer $TOKEN"
```

## Структура репозитория

```
project_dz_java/
├── eureka-server/      # discovery
├── hotel-service/      # отели, номера, room_blocks (JWT resource server)
├── booking-service/    # пользователи, JWT, бронирования, сага (issuer)
├── api-gateway/        # маршрутизация + блокировка внутренних эндпоинтов
├── scripts/
│   ├── start-all.sh    # запуск в правильном порядке
│   ├── stop-all.sh     # остановка по PID-файлам
│   ├── e2e-test.sh     # сквозной сценарий через gateway
│   └── race-test.sh    # проверка защиты от гонок
└── logs/               # *.out.log / *.err.log / *.pid (создаётся при запуске)
```

## Диагностика

| Симптом                                                       | Причина / решение                                                                              |
|---------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `503` от gateway сразу после старта                           | Eureka ещё не отдала реестр — подождать 5-15 с и повторить                                      |
| `401 "A valid JWT access token is required"` от hotel-service | `JWT_SECRET` в hotel-service и booking-service различаются                                      |
| `403` на `confirm-availability` из booking-service            | не совпал `INTERNAL_API_TOKEN` (или переименован заголовок)                                     |
| `502 "Could not confirm the booking"`                         | hotel-service недоступен/таймаут Feign; бронь отменена, блок снят                                |
| `409` на всех бронированиях одного номера                     | период действительно занят — смотреть `GET /api/rooms/recommend`                                 |
| Порт занят                                                     | `bash scripts/stop-all.sh` либо освободить 8761/8081/8082/8080 вручную                          |
| Нужны H2-консоли                                               | `/h2-console` на 8081 и 8082: `jdbc:h2:mem:hoteldb` / `jdbc:h2:mem:bookingdb`, пользователь `sa` |


