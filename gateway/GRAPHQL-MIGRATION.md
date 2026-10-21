# Gateway REST → GraphQL — Migrasi (pointofsale)

> **STATUS: SUDAH DIIMPLEMENTASIKAN.** Seluruh fase (§12) selesai; `./mvnw -pl gateway -am test`
> hijau. Lihat §16 untuk catatan/penyesuaian saat implementasi.
> Scope: **hanya modul `gateway/`**. Semua microservice domain (auth, user, role, merchant,
> cashier, category, product, order, transaction, stats) tetap apa adanya (gRPC server +
> Flyway + Kafka). Gateway tetap **BFF tanpa database**: satu-satunya perubahan adalah lapisan
> edge dari REST/JSON menjadi GraphQL.
>
> Referensi pola: `ecommerce/gateway/GRAPHQL-MIGRATION.md` (sudah diimplementasikan di sana).
> Dokumen ini menyesuaikan pola tersebut dengan domain pointofsale.

---

## 1. Tujuan & ruang lingkup

| | |
|---|---|
| **Masuk scope** | Modul `gateway` — ganti 10 controller REST + DTO request menjadi schema GraphQL + resolver. |
| **Tidak masuk scope** | Proto (`common/src/main/proto`), server gRPC domain, DB, Kafka, ClickHouse, deployment. |
| **Yang dipertahankan** | `GrpcClientConfig` (channel + blocking stub per service), `security/*` (JWT filter, entry point), `service/FileService` & `FolderService`, static file serving (`/static/**`), `mapper/ProtoMapper`. |
| **Tujuan teknis** | 1 endpoint `POST /graphql`, client bebas memilih field (hilangkan over-fetching), skema sebagai kontrak, tetap pakai protobuf stub yang sudah ada. |
| **Keputusan akhir** | **Full GraphQL**: seluruh controller REST lama dihapus; hanya menyisakan `POST /api/upload` (multipart) dan `/static/**`. |

### Kondisi saat ini

- 10 controller REST `@RestController` di `gateway/.../controller/`:
  `Auth`, `User`, `Role`, `Merchant`, `Cashier`, `Category`, `Product`, `Order`, `Transaction`, `Stats`.
- Envelope seragam: `ApiResponse<T> { status, message, data }` dan
  `ApiResponsePagination<T> { status, message, data, pagination }` (dari modul `common`).
- Otorisasi per-endpoint via `@PreAuthorize("hasAnyRole(...)")` di atas `@EnableMethodSecurity`.
  **Catatan**: endpoint query di `UserController` **tidak** ber-`@PreAuthorize` (cukup
  `authenticated()`), sisanya `ADMIN`/`STAFF` dan mutation `ADMIN`. Tidak ada role `USER`
  yang dipakai di gateway pointofsale.
- **Tidak ada** `@RestControllerAdvice`/`RestExceptionHandler` di gateway ini. `StatusRuntimeException`
  dari stub gRPC saat ini lolos ke default Spring (HTTP 500). Migrasi GraphQL justru kesempatan
  memperkenalkan pemetaan error yang rapi (§10).
- Upload file multipart hanya di `product` (`CreateProductRequest.imageProduct`,
  `UpdateProductRequest.imageProduct`) disimpan lewat `FileService`/`FolderService`.
- Dokumentasi API: springdoc/Swagger UI di `/api-docs`.

### Beda kunci vs ecommerce

| Hal | ecommerce | pointofsale |
|---|---|---|
| Jumlah domain | 21 (merchant + 5 sub-domain) | 10 (merchant tunggal, tanpa sub-domain) |
| Domain khusus | banner, slider, cart, review, shipping, merchant-award/… | **cashier** (tidak ada di ecommerce) |
| Role | ADMIN, STAFF, **USER** | ADMIN, STAFF saja |
| Upload | product, category, slider, merchant-detail, review-detail | **product** saja |
| Base package | `com.sanedge.ecommerce.gateway` | `com.sanedge.pointofsale.gateway` |
| Proto java package | `com.sanedge.ecommerce.pb.*` | `com.sanedge.pointofsale.pb.*` |
| Test | ada (`GraphQlEndpointTests`, `GraphQlResolverTests`) | **belum ada** (`src/test` kosong) |

---

## 2. Keputusan desain

| # | Topik | Keputusan |
|---|---|---|
| D1 | Gaya schema | **Schema-first** (`src/main/resources/graphql/*.graphqls`) + `@QueryMapping`/`@MutationMapping` |
| D2 | Framework | **Spring for GraphQL** (`spring-boot-starter-graphql`, versi ikut parent BOM) |
| D3 | Transport | Spring GraphQL WebMVC di **`POST /graphql`** (+ GraphiQL di `/graphiql`, dev only) |
| D4 | Envelope `{status,message,data}` | **Dibuang** di GraphQL — field mengembalikan payload langsung; error lewat `errors[]` + `extensions.httpStatus` |
| D5 | REST eksisting | **Dihapus** (full GraphQL) kecuali `POST /api/upload` |
| D6 | Naming | GraphQL camelCase (`accessToken`, `totalItems`) |
| D7 | Upload multipart | **Endpoint REST kecil `/api/upload`** → balikan path, mutation terima `String` path |
| D8 | Pagination | Tipe `<Entity>Page { data: [T!]!, pagination: PaginationMeta! }` |

---

## 3. Perubahan dependency & konfigurasi

### `gateway/pom.xml`

Tambah:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>

<!-- test -->
<dependency>
    <groupId>org.springframework.graphql</groupId>
    <artifactId>spring-graphql-test</artifactId>
    <scope>test</scope>
</dependency>
```

Ubah deskripsi artifact:

```xml
<description>GraphQL gateway (BFF) - no database, proxies to domain services over gRPC</description>
```

Biarkan tetap ada:

- `spring-boot-starter-web` → transport HTTP GraphQL (`POST /graphql`), static file `/static/**`,
  dan endpoint upload multipart.
- `spring-boot-starter-security` → JWT filter + `@PreAuthorize`.
- `spring-boot-starter-validation` → validasi input (opsional; lihat §6 deviasi validasi).
- `springdoc-openapi-starter-webmvc-ui` → boleh ditahan (kini hanya mendokumentasikan `/api/upload`).

### `application.properties` (tambahan)

```properties
# --- GraphQL (BFF edge) ---
spring.graphql.path=/graphql
spring.graphql.graphiql.enabled=true          # dev; false di prod
spring.graphql.graphiql.path=/graphiql
spring.graphql.schema.locations=classpath:graphql/
spring.graphql.schema.file-extensions=.graphqls
spring.graphql.schema.inspection.enabled=true
```

`GrpcClientConfig`, `server.port` (`${GATEWAY_PORT:8080}`), seluruh blok `grpc.client.*`,
JWT, multipart, dan `spring.mvc.static-path-pattern` **tidak berubah**.

### `grpc.client.*` (tetap, untuk referensi)

| domain | property | port default | stub |
|---|---|---|---|
| auth | `grpc.client.auth` | 9012 | `AuthService` |
| user | `grpc.client.user` | 9011 | `UserQueryService` + `UserCommandService` |
| role | `grpc.client.role` | 9006 | `RoleQueryService` + `RoleCommandService` |
| merchant | `grpc.client.merchant` | 9005 | `MerchantQueryService` + `MerchantCommandService` |
| cashier | `grpc.client.cashier` | 9014 | `CashierQueryService` + `CashierCommandService` |
| category | `grpc.client.category` | 9015 | `CategoryQueryService` + `CategoryCommandService` |
| product | `grpc.client.product` | 9003 | `ProductQueryService` + `ProductCommandService` |
| order | `grpc.client.order` | 9001 | `OrderQueryService` + `OrderCommandService` |
| order-item | `grpc.client.order-item` | 9016 | `OrderItemQueryService` + `OrderItemCommandService` |
| transaction | `grpc.client.transaction` | 9009 | `TransactionQueryService` + `TransactionCommandService` |
| stats | `grpc.client.stats` | 9017 | `StatsReaderService` |

---

## 4. Struktur package target

```
gateway/src/main/
├── resources/
│   ├── application.properties
│   └── graphql/
│       ├── schema.graphqls        # root Query & Mutation
│       ├── common.graphqls        # PaginationMeta
│       ├── auth.graphqls
│       ├── user.graphqls
│       ├── role.graphqls
│       ├── merchant.graphqls
│       ├── cashier.graphqls
│       ├── category.graphqls
│       ├── product.graphqls
│       ├── order.graphqls         # order (+ order_item opsional)
│       ├── transaction.graphqls
│       └── stats.graphqls
└── java/com/sanedge/pointofsale/gateway/
    ├── config/            # GrpcClientConfig (tetap), SecurityConfig (tambah /graphql, /graphiql)
    ├── controller/        # DIHAPUS kecuali UploadController kecil
    ├── graphql/
    │   ├── dto/           # Page.java (payload paginasi generik), Token.java (camelCase)
    │   ├── Gql.java       # helper nvl + ok(status) + page(...)
    │   ├── GraphQlExceptionHandler.java
    │   └── resolver/      # @Controller per domain
    ├── mapper/ProtoMapper.java            # DIPAKAI ULANG apa adanya
    ├── dto/               # dto/responses/** tetap (ProtoMapper butuh); dto/requests/** dihapus
    ├── security/          # tetap
    └── service/           # FileService/FolderService tetap
```

Keputusan penting: **`ProtoMapper` dan semua DTO response dipertahankan.** Resolver memanggil
stub gRPC, memetakan lewat `ProtoMapper` (dan `ProtoMappers.fromProtoPagination` dari `common`),
lalu mengembalikan DTO apa adanya. Tidak ada duplikasi logika mapping.

---

## 5. Konvensi penamaan & bentuk response

- **Query** (read) → jamak untuk list, tunggal untuk by-id:
  `products(...)`, `product(id)`, `activeProducts(...)`, `trashedProducts(...)`.
- **Mutation** (write) → kata kerja: `createProduct`, `updateProduct`, `trashProduct`,
  `restoreProduct`, `deleteProductPermanent`, `restoreAllProducts`, `deleteAllProductsPermanent`.
- **Input** memakai suffix `Input`, mutation memakai satu argumen `input`:
  `createProduct(input: CreateProductInput!): Product!`.
- **Argumen list/filter** (page, pageSize, search, minPrice, …) tetap argumen field, bukan input.
- **`ID`** untuk identifier; `Long`/`Integer` di DTO dipetakan ke `ID` untuk id, `Int` untuk angka biasa,
  `Float` untuk `rating`.
- **Response list** selalu `XxxPage` dengan `data` non-null element dan `pagination` non-null.
- **`*DeleteAt`** → tipe GraphQL tanpa suffix `Response` (mis. `ProductDeleteAt`), berisi `deletedAt`.
- **Token** memakai camelCase (`accessToken`/`refreshToken`) walau DTO `TokenResponse`
  memakai `access_token`/`refresh_token`.

---

## 6. Schema GraphQL lengkap

### 6.1 common.graphqls

```graphql
type PaginationMeta {
  page: Int!
  pageSize: Int!
  totalItems: Int!
  totalPages: Int!
}
```

### 6.2 schema.graphqls (root)

```graphql
type Query { _root: Boolean }
type Mutation { _root: Boolean }
```

### 6.3 auth.graphqls

```graphql
type Token {
  accessToken: String!
  refreshToken: String!
}

input RegisterInput {
  firstname: String!
  username: String!
  lastname: String!
  email: String!
  password: String!
  confirmPassword: String!
}

input LoginInput {
  username: String!   # di REST isinya email/username
  password: String!
}

extend type Query {
  me: User                 # authenticated
  healthchecker: String!   # publik (menggantikan GET /api/auth/healthchecker)
}

extend type Mutation {
  register(input: RegisterInput!): User!
  login(input: LoginInput!): Token!
  refreshToken(refreshToken: String!): Token!
}
```

### 6.4 user.graphqls

```graphql
type User {
  id: ID!
  username: String
  firstname: String
  lastname: String
  email: String
  createdAt: String
  updatedAt: String
}

type UserDeleteAt {
  id: ID!
  firstname: String
  lastname: String
  email: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type UserPage { data: [User!]! pagination: PaginationMeta! }
type UserDeleteAtPage { data: [UserDeleteAt!]! pagination: PaginationMeta! }

input CreateUserInput {
  username: String!
  firstname: String!
  lastname: String!
  email: String!
  password: String!
  confirmPassword: String!
}

input UpdateUserInput {
  id: ID!
  username: String
  firstname: String
  lastname: String
  email: String
  password: String
  confirmPassword: String
}

extend type Query {
  users(page: Int = 1, pageSize: Int = 10, search: String = ""): UserPage!            # authenticated
  user(id: ID!): User!                                                                # authenticated
  activeUsers(page: Int = 1, pageSize: Int = 10, search: String = ""): UserDeleteAtPage!   # authenticated
  trashedUsers(page: Int = 1, pageSize: Int = 10, search: String = ""): UserDeleteAtPage!  # authenticated
}

extend type Mutation {
  createUser(input: CreateUserInput!): User!        # ADMIN
  updateUser(input: UpdateUserInput!): User!        # ADMIN
  trashUser(id: ID!): UserDeleteAt!                 # ADMIN
  restoreUser(id: ID!): UserDeleteAt!               # ADMIN
  deleteUserPermanent(id: ID!): Boolean!            # ADMIN
  restoreAllUsers: Boolean!                         # ADMIN
  deleteAllUsersPermanent: Boolean!                 # ADMIN
}
```

> REST `UserController` tidak memasang `@PreAuthorize` pada query. Di GraphQL disarankan
> `hasAnyRole('ADMIN','STAFF')` (atau `isAuthenticated()` bila ingin paritas persis).

### 6.5 role.graphqls

```graphql
type Role {
  id: ID!
  name: String
  createdAt: String
  updatedAt: String
}

type RoleDeleteAt {
  id: ID!
  name: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type RolePage { data: [Role!]! pagination: PaginationMeta! }
type RoleDeleteAtPage { data: [RoleDeleteAt!]! pagination: PaginationMeta! }

input CreateRoleInput { name: String! }
input UpdateRoleInput { id: ID!, name: String! }

extend type Query {
  roles(page: Int = 1, pageSize: Int = 10, search: String = ""): RolePage!                # ADMIN, STAFF
  role(id: ID!): Role!                                                                     # ADMIN, STAFF
  activeRoles(page: Int = 1, pageSize: Int = 10, search: String = ""): RoleDeleteAtPage!   # ADMIN, STAFF
  trashedRoles(page: Int = 1, pageSize: Int = 10, search: String = ""): RoleDeleteAtPage!  # ADMIN, STAFF
  rolesByUser(userId: ID!): [Role!]!                                                       # ADMIN, STAFF
  roleByName(name: String!): Role!                                                         # ADMIN, STAFF
}

extend type Mutation {
  createRole(input: CreateRoleInput!): Role!        # ADMIN
  updateRole(input: UpdateRoleInput!): Role!        # ADMIN
  trashRole(id: ID!): RoleDeleteAt!                 # ADMIN
  restoreRole(id: ID!): RoleDeleteAt!               # ADMIN
  deleteRolePermanent(id: ID!): Boolean!            # ADMIN
  restoreAllRoles: Boolean!                         # ADMIN
  deleteAllRolesPermanent: Boolean!                 # ADMIN
}
```

### 6.6 merchant.graphqls

```graphql
type Merchant {
  id: ID!
  userId: Int
  name: String
  description: String
  address: String
  contactEmail: String
  contactPhone: String
  status: String
  createdAt: String
  updatedAt: String
}

type MerchantDeleteAt {
  id: ID!
  userId: Int
  name: String
  description: String
  address: String
  contactEmail: String
  contactPhone: String
  status: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type MerchantPage { data: [Merchant!]! pagination: PaginationMeta! }
type MerchantDeleteAtPage { data: [MerchantDeleteAt!]! pagination: PaginationMeta! }

input CreateMerchantInput {
  userId: ID!
  name: String!
  description: String
  address: String
  contactEmail: String
  contactPhone: String
  status: String
}

input UpdateMerchantInput {
  merchantId: ID!
  userId: ID
  name: String
  description: String
  address: String
  contactEmail: String
  contactPhone: String
  status: String
}

extend type Query {
  merchants(page: Int = 1, pageSize: Int = 10, search: String = ""): MerchantPage!            # ADMIN, STAFF
  merchant(id: ID!): Merchant!                                                                 # ADMIN, STAFF
  activeMerchants(page: Int = 1, pageSize: Int = 10, search: String = ""): MerchantDeleteAtPage!   # ADMIN, STAFF
  trashedMerchants(page: Int = 1, pageSize: Int = 10, search: String = ""): MerchantDeleteAtPage!  # ADMIN, STAFF
}

extend type Mutation {
  createMerchant(input: CreateMerchantInput!): Merchant!        # ADMIN
  updateMerchant(input: UpdateMerchantInput!): Merchant!        # ADMIN
  trashMerchant(id: ID!): MerchantDeleteAt!                     # ADMIN
  restoreMerchant(id: ID!): MerchantDeleteAt!                   # ADMIN
  deleteMerchantPermanent(id: ID!): Boolean!                    # ADMIN
  restoreAllMerchants: Boolean!                                 # ADMIN
  deleteAllMerchantsPermanent: Boolean!                         # ADMIN
}
```

> Proto `MerchantQueryService.FindByUserId` dan `MerchantCommandService.UpdateMerchantStatus`
> tidak diekspos di REST; tambahkan `merchantByUser(userId)` / `updateMerchantStatus(input)`
> hanya bila diinginkan (opsional, bukan paritas).

### 6.7 cashier.graphqls

> Domain **baru** yang tidak ada di ecommerce.

```graphql
type Cashier {
  id: ID!
  merchantId: Int
  name: String
  createdAt: String
  updatedAt: String
}

type CashierDeleteAt {
  id: ID!
  merchantId: Int
  name: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type CashierPage { data: [Cashier!]! pagination: PaginationMeta! }
type CashierDeleteAtPage { data: [CashierDeleteAt!]! pagination: PaginationMeta! }

input CreateCashierInput { merchantId: ID!, name: String! }     # userId dipakai internal service
input UpdateCashierInput { cashierId: ID!, name: String! }

extend type Query {
  cashiers(page: Int = 1, pageSize: Int = 10, search: String = ""): CashierPage!             # ADMIN, STAFF
  cashier(id: ID!): Cashier!                                                                  # ADMIN, STAFF
  activeCashiers(page: Int = 1, pageSize: Int = 10, search: String = ""): CashierDeleteAtPage!   # ADMIN, STAFF
  trashedCashiers(page: Int = 1, pageSize: Int = 10, search: String = ""): CashierDeleteAtPage!  # ADMIN, STAFF
  cashiersByMerchant(merchantId: ID!, page: Int = 1, pageSize: Int = 10, search: String = ""): CashierPage!  # ADMIN, STAFF
}

extend type Mutation {
  createCashier(input: CreateCashierInput!): Cashier!        # ADMIN
  updateCashier(input: UpdateCashierInput!): Cashier!        # ADMIN
  trashCashier(id: ID!): CashierDeleteAt!                    # ADMIN
  restoreCashier(id: ID!): CashierDeleteAt!                  # ADMIN
  deleteCashierPermanent(id: ID!): Boolean!                  # ADMIN
  restoreAllCashiers: Boolean!                               # ADMIN
  deleteAllCashiersPermanent: Boolean!                       # ADMIN
}
```

### 6.8 category.graphqls

```graphql
type Category {
  id: ID!
  name: String
  description: String
  slugCategory: String
  imageCategory: String
  createdAt: String
  updatedAt: String
}

type CategoryDeleteAt {
  id: ID!
  name: String
  description: String
  slugCategory: String
  imageCategory: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type CategoryPage { data: [Category!]! pagination: PaginationMeta! }
type CategoryDeleteAtPage { data: [CategoryDeleteAt!]! pagination: PaginationMeta! }

input CreateCategoryInput { name: String!, description: String!, slugCategory: String }
input UpdateCategoryInput { categoryId: ID!, name: String, description: String, slugCategory: String }

extend type Query {
  categories(page: Int = 1, pageSize: Int = 10, search: String = ""): CategoryPage!
  category(id: ID!): Category!
  activeCategories(page: Int = 1, pageSize: Int = 10, search: String = ""): CategoryDeleteAtPage!
  trashedCategories(page: Int = 1, pageSize: Int = 10, search: String = ""): CategoryDeleteAtPage!
}

extend type Mutation {
  createCategory(input: CreateCategoryInput!): Category!     # ADMIN
  updateCategory(input: UpdateCategoryInput!): Category!     # ADMIN
  trashCategory(id: ID!): CategoryDeleteAt!                  # ADMIN
  restoreCategory(id: ID!): CategoryDeleteAt!                # ADMIN
  deleteCategoryPermanent(id: ID!): Boolean!                 # ADMIN
  restoreAllCategories: Boolean!                             # ADMIN
  deleteAllCategoriesPermanent: Boolean!                     # ADMIN
}
```

> REST `CategoryController` query tidak ber-`@PreAuthorize`; dapat dibiarkan `authenticated`
> atau diseragamkan ke `ADMIN, STAFF`.

### 6.9 product.graphqls

```graphql
type Product {
  id: ID!
  merchantId: Int
  categoryId: Int
  name: String
  description: String
  price: Int
  countInStock: Int
  brand: String
  weight: Int
  rating: Float
  slugProduct: String
  imageProduct: String
  createdAt: String
  updatedAt: String
}

type ProductDeleteAt {
  id: ID!
  merchantId: Int
  categoryId: Int
  name: String
  description: String
  price: Int
  countInStock: Int
  brand: String
  weight: Int
  rating: Float
  slugProduct: String
  imageProduct: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type ProductPage { data: [Product!]! pagination: PaginationMeta! }
type ProductDeleteAtPage { data: [ProductDeleteAt!]! pagination: PaginationMeta! }

input CreateProductInput {
  merchantId: ID!
  categoryId: ID!
  name: String!
  description: String
  price: Int
  countInStock: Int
  brand: String
  weight: Int
  rating: Int
  slugProduct: String
  imageProduct: String          # path hasil POST /api/upload
}

input UpdateProductInput {
  productId: ID!
  merchantId: ID
  categoryId: ID
  name: String
  description: String
  price: Int
  countInStock: Int
  brand: String
  weight: Int
  rating: Int
  slugProduct: String
  imageProduct: String
}

extend type Query {
  products(page: Int = 1, pageSize: Int = 10, search: String = ""): ProductPage!            # ADMIN, STAFF
  product(id: ID!): Product!                                                                 # ADMIN, STAFF
  productsByMerchant(merchantId: ID!, categoryId: ID, minPrice: Int, maxPrice: Int,
                     page: Int = 1, pageSize: Int = 10, search: String = ""): ProductPage!  # ADMIN, STAFF
  productsByCategory(categoryName: String!, minPrice: Int, maxPrice: Int,
                     page: Int = 1, pageSize: Int = 10, search: String = ""): ProductPage!  # ADMIN, STAFF
  activeProducts(page: Int = 1, pageSize: Int = 10, search: String = ""): ProductDeleteAtPage!   # ADMIN, STAFF
  trashedProducts(page: Int = 1, pageSize: Int = 10, search: String = ""): ProductDeleteAtPage!  # ADMIN, STAFF
}

extend type Mutation {
  createProduct(input: CreateProductInput!): Product!        # ADMIN
  updateProduct(input: UpdateProductInput!): Product!        # ADMIN
  trashProduct(id: ID!): ProductDeleteAt!                    # ADMIN
  restoreProduct(id: ID!): ProductDeleteAt!                  # ADMIN
  deleteProductPermanent(id: ID!): Boolean!                  # ADMIN
  restoreAllProducts: Boolean!                               # ADMIN
  deleteAllProductsPermanent: Boolean!                       # ADMIN
}
```

> `rating` di DTO bertipe `Float`; di REST create/update memakai `Integer`. Di GraphQL ekspos
> sebagai `Float` pada output dan `Int` pada input (mengikuti proto) — sesuaikan bila proto
> memakai `float`.

### 6.10 order.graphqls

```graphql
type Order {
  id: ID!
  merchantId: Int
  cashierId: Int
  totalPrice: Float      # proto int64; Int GraphQL hanya 32-bit
  createdAt: String
  updatedAt: String
}

type OrderDeleteAt {
  id: ID!
  merchantId: Int
  cashierId: Int
  totalPrice: Float      # proto int64; Int GraphQL hanya 32-bit
  createdAt: String
  updatedAt: String
  deletedAt: String
}

# Opsional: order_item (stub sudah di-wire, tapi tidak diekspos REST)
type OrderItem {
  id: ID!
  orderId: Int
  productId: Int
  quantity: Int
  price: Int
  createdAt: String
  updatedAt: String
}

type OrderPage { data: [Order!]! pagination: PaginationMeta! }
type OrderDeleteAtPage { data: [OrderDeleteAt!]! pagination: PaginationMeta! }

input OrderItemInput { productId: ID!, quantity: Int!, price: Int! }
input UpdateOrderItemInput { orderItemId: ID!, productId: ID, quantity: Int, price: Int }

input CreateOrderInput {
  merchantId: ID!
  cashierId: ID!
  items: [OrderItemInput!]!
}

input UpdateOrderInput {
  orderId: ID!
  cashierId: ID
  items: [UpdateOrderItemInput!]
}

extend type Query {
  orders(page: Int = 1, pageSize: Int = 10, search: String = ""): OrderPage!                 # ADMIN, STAFF
  order(id: ID!): Order!                                                                     # ADMIN, STAFF
  activeOrders(page: Int = 1, pageSize: Int = 10, search: String = ""): OrderDeleteAtPage!   # ADMIN, STAFF
  trashedOrders(page: Int = 1, pageSize: Int = 10, search: String = ""): OrderDeleteAtPage!  # ADMIN, STAFF
  ordersByMerchant(merchantId: ID!, page: Int = 1, pageSize: Int = 10, search: String = ""): OrderPage!  # ADMIN, STAFF
}

extend type Mutation {
  createOrder(input: CreateOrderInput!): Order!        # ADMIN
  updateOrder(input: UpdateOrderInput!): Order!        # ADMIN
  trashOrder(id: ID!): OrderDeleteAt!                  # ADMIN
  restoreOrder(id: ID!): OrderDeleteAt!                # ADMIN
  deleteOrderPermanent(id: ID!): Boolean!              # ADMIN
  restoreAllOrders: Boolean!                           # ADMIN
  deleteAllOrdersPermanent: Boolean!                   # ADMIN
}
```

### 6.11 transaction.graphqls

```graphql
type Transaction {
  id: ID!
  orderId: Int
  merchantId: Int
  paymentMethod: String
  amount: Int
  paymentStatus: String
  createdAt: String
  updatedAt: String
}

type TransactionDeleteAt {
  id: ID!
  orderId: Int
  merchantId: Int
  paymentMethod: String
  amount: Int
  paymentStatus: String
  createdAt: String
  updatedAt: String
  deletedAt: String
}

type TransactionPage { data: [Transaction!]! pagination: PaginationMeta! }
type TransactionDeleteAtPage { data: [TransactionDeleteAt!]! pagination: PaginationMeta! }

input CreateTransactionInput {
  orderId: ID!
  merchantId: ID!
  paymentMethod: String!
  amount: Int!
  paymentStatus: String
}

input UpdateTransactionInput {
  transactionId: ID!
  orderId: ID
  merchantId: ID
  paymentMethod: String
  amount: Int
  paymentStatus: String
}

extend type Query {
  transactions(page: Int = 1, pageSize: Int = 10, search: String = ""): TransactionPage!            # ADMIN, STAFF
  transaction(id: ID!): Transaction!                                                                # ADMIN, STAFF
  transactionsByMerchant(merchantId: ID!, page: Int = 1, pageSize: Int = 10, search: String = ""): TransactionPage!  # ADMIN, STAFF
  activeTransactions(page: Int = 1, pageSize: Int = 10, search: String = ""): TransactionDeleteAtPage!   # ADMIN, STAFF
  trashedTransactions(page: Int = 1, pageSize: Int = 10, search: String = ""): TransactionDeleteAtPage!  # ADMIN, STAFF
}

extend type Mutation {
  createTransaction(input: CreateTransactionInput!): Transaction!   # ADMIN
  updateTransaction(input: UpdateTransactionInput!): Transaction!   # ADMIN
  trashTransaction(id: ID!): TransactionDeleteAt!                   # ADMIN
  restoreTransaction(id: ID!): TransactionDeleteAt!                 # ADMIN
  deleteTransactionPermanent(id: ID!): Boolean!                     # ADMIN
  restoreAllTransactions: Boolean!                                  # ADMIN
  deleteAllTransactionsPermanent: Boolean!                          # ADMIN
}
```

### 6.12 stats.graphqls

```graphql
type StatItem {
  period: String
  dimensionId: ID
  value: Float
}

extend type Query {
  monthlyRevenue(year: Int, month: Int): [StatItem!]!          # ADMIN, STAFF
  yearlyRevenue(year: Int): [StatItem!]!                       # ADMIN, STAFF
  merchantSales(merchantId: ID, year: Int): [StatItem!]!       # ADMIN, STAFF
  productSales(productId: ID, year: Int): [StatItem!]!         # ADMIN, STAFF
}
```

---

## 7. Tabel pemetaan REST → GraphQL

Template CRUD seragam (role, user, merchant, cashier, category, product, order, transaction):

| REST (sekarang) | GraphQL (target) |
|---|---|
| `GET /api/<x>?page&pageSize&search` | `query { xs(page, pageSize, search) }` |
| `GET /api/<x>/{id}` | `query { x(id) }` |
| `GET /api/<x>/active` | `query { activeXs(...) }` |
| `GET /api/<x>/trashed` | `query { trashedXs(...) }` |
| `POST /api/<x>/create` | `mutation { createX(input) }` |
| `POST /api/<x>/update` atau `/update/{id}` | `mutation { updateX(input) }` |
| `POST /api/<x>/trashed/{id}` atau `/trash/{id}` | `mutation { trashX(id) }` |
| `POST /api/<x>/restore/{id}` | `mutation { restoreX(id) }` |
| `DELETE /api/<x>/{id}` atau `/permanent/{id}` | `mutation { deleteXPermanent(id) }` |
| `POST /api/<x>/restore-all` atau `/restore/all` | `mutation { restoreAllXs }` |
| `POST /api/<x>/delete-all` atau `/permanent/all` | `mutation { deleteAllXsPermanent }` |

Query/mutation khusus (di luar template):

| REST | GraphQL |
|---|---|
| `POST /api/auth/register` | `mutation register(input)` |
| `POST /api/auth/login` | `mutation login(input)` |
| `POST /api/auth/refresh-token` | `mutation refreshToken(refreshToken)` |
| `GET /api/auth/me` | `query me` |
| `GET /api/auth/healthchecker` | `query healthchecker` (publik) |
| `GET /api/roles/by-user/{userId}` | `query rolesByUser(userId)` |
| `GET /api/roles/by-name?name=` | `query roleByName(name)` |
| `GET /api/cashier/merchant` | `query cashiersByMerchant(merchantId, ...)` |
| `GET /api/product/merchant/{merchant_id}` | `query productsByMerchant(merchantId, ...)` |
| `GET /api/product/category/{category_name}` | `query productsByCategory(categoryName, ...)` |
| `GET /api/order/merchant/{merchant_id}` | `query ordersByMerchant(merchantId, ...)` |
| `GET /api/transaction/merchant/{merchantId}` | `query transactionsByMerchant(merchantId, ...)` |
| `GET /api/stats/monthly-revenue` | `query monthlyRevenue(year, month)` |
| `GET /api/stats/yearly-revenue` | `query yearlyRevenue(year)` |
| `GET /api/stats/merchant-sales` | `query merchantSales(merchantId, year)` |
| `GET /api/stats/product-sales` | `query productSales(productId, year)` |

> Perhatikan ketidakkonsistenan REST yang **diseragamkan** di GraphQL: base path
> (`/api/product`, `/api/category`, `/api/merchant`, `/api/cashier` singular vs `/api/users`,
> `/api/roles` plural), verb delete (`DELETE /{id}`, `/permanent/{id}`), dan batch
> (`/restore-all`+`/delete-all` vs `/restore/all`+`/permanent/all`). GraphQL selalu
> `trashX`/`restoreX`/`deleteXPermanent`/`restoreAllXs`/`deleteAllXsPermanent`.

---

## 8. Upload file (multipart)

`CreateProductRequest`/`UpdateProductRequest` menerima `MultipartFile imageProduct` dan
menyimpan via `FileService`/`FolderService`. GraphQL tidak punya konsep multipart native.

**Rencana:** pisahkan upload dari GraphQL.

1. Tambah satu endpoint REST kecil: `POST /api/upload` (multipart `file`, param `folder`/`key`)
   → `{ "status": "success", "data": { "path": "/static/product/xxx.png" } }`.
   Ini identik dengan `UploadController` di ecommerce (pakai ulang `FileService`/`FolderService`).
2. Mutation GraphQL menerima **string path** (`imageProduct`).
3. Alur client: `POST /api/upload` (dapat path) →
   `mutation createProduct(input:{... imageProduct: "<path>" })`.

**Alternatif** (bila wajib satu endpoint): implementasi `Upload` scalar + dukungan
"GraphQL multipart request spec" — lebih rumit, tidak direkomendasikan untuk iterasi pertama.

---

## 9. Keamanan & otorisasi

- `SecurityConfig`: tambah `/graphql` dan `/graphiql` ke `PUBLIC_ENDPOINTS`.
  - `/graphiql` → `permitAll()` (dev) atau matikan di prod (`spring.graphql.graphiql.enabled=false`).
  - `/graphql` → biarkan `permitAll()` di filter chain, lalu **otorisasi per-field** di resolver
    (pola ecommerce). Ini karena `login`/`register`/`refreshToken` harus anonim, sementara
    field lain butuh JWT.
- `AuthTokenFilter` tetap dipakai — JWT dari header `Authorization: Bearer` mengisi
  `SecurityContextHolder`, jadi resolver aman membaca context.
- Otorisasi per-field: `@PreAuthorize("hasAnyRole('ADMIN','STAFF')")` langsung di method
  `@QueryMapping`/`@MutationMapping`. `@EnableMethodSecurity` sudah aktif.
- Untuk operasi publik (login/register/refreshToken/healthchecker): tanpa `@PreAuthorize`.
- `UserController` query yang semula tanpa anotasi → beri `hasAnyRole('ADMIN','STAFF')`
  (atau `isAuthenticated()`).

`PUBLIC_ENDPOINTS` target:

```java
private static final String[] PUBLIC_ENDPOINTS = {
        "/api/test/**",
        "/static/**",
        "/graphql",
        "/graphiql",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/api-docs/**",
        "/swagger-resources/**",
        "/configuration/**",
        "/webjars/**",
        "/error"
};
```

---

## 10. Error handling

Gateway pointofsale **belum** punya exception handler, jadi buat baru:
`graphql/GraphQlExceptionHandler.java` (`DataFetcherExceptionResolverAdapter`), persis pola
ecommerce. Pemetaan mengikuti `payment_gateway` (yang sudah punya `RestExceptionHandler`)
dan ecommerce:

- `StatusRuntimeException` → `extensions.httpStatus` + `extensions.grpcCode` dengan pemetaan:

| gRPC code | `extensions.httpStatus` |
|---|---|
| `NOT_FOUND` | 404 |
| `INVALID_ARGUMENT`, `FAILED_PRECONDITION` | 400 |
| `ALREADY_EXISTS` | 409 |
| `UNAUTHENTICATED` | 401 |
| `PERMISSION_DENIED` | 403 |
| lainnya | 500 |

- `ResourceNotFoundException` → 404, `AccessDeniedException` → 401/403 (tergantung anonim),
  `AuthenticationException` → 401, validasi (`ConstraintViolationException`, `BindException`,
  `IllegalArgumentException`) → 400, `UnsupportedOperationException` → 501.

Perbedaan penting: GraphQL selalu balikan HTTP 200 di transport; status "HTTP" pindah ke
`errors[].extensions.httpStatus`. Klien harus membaca `errors`, bukan status code.

> Referensi pemetaan iden: `payment_gateway/gateway/.../exception/RestExceptionHandler.java`
> dan `ecommerce/gateway/.../graphql/GraphQlExceptionHandler.java`.

---

## 11. Testing

`src/test` saat ini **kosong**; mulai dari nol (contoh ecommerce):

- `GatewayApplicationTests` — `@SpringBootTest @ActiveProfiles("test")`, `contextLoads()` memastikan
  schema GraphQL + bean dapat di-wire.
- `GraphQlEndpointTests` — `@AutoConfigureGraphQlTester`:
  - query publik `healthchecker` sukses,
  - field ber-role tanpa token → GraphQL error (bukan 500).
- `GraphQlResolverTests` — stub gRPC di-mock (Mockito):
  1. output `*Page` + `PaginationMeta` terserialisasi benar,
  2. input object GraphQL ter-bind ke record Java (verifikasi via `ArgumentCaptor`),
  3. otorisasi `ROLE_ADMIN` pada resolver bekerja.
- `src/test/resources/application-test.properties` — port gRPC + JWT secret (tanpa DB),
  mirror `ecommerce/gateway/src/test/resources/application-test.properties`.
- Uji upload: `POST /api/upload` mengembalikan path, lalu mutation memakai path.
- Uji otorisasi: request tanpa token → `UNAUTHENTICATED`; role salah → `FORBIDDEN`.

---

## 12. Rencana eksekusi (fase)

**Fase 0 — Persiapan**
- [x] Tambah dependency `spring-boot-starter-graphql` (+ `spring-graphql-test`) + properti GraphQL.
- [x] Buat kerangka `resources/graphql/` dan package `graphql/dto|resolver`.
- [x] Salin helper dari ecommerce: `Gql.java`, `graphql/dto/Page.java`, `graphql/dto/Token.java`,
      `graphql/GraphQlExceptionHandler.java` (sesuaikan package).

**Fase 1 — Pilot 1 domain (product)**
- [x] `product.graphqls` + input.
- [x] `ProductResolver` (query + mutation).
- [x] `UploadController` (`POST /api/upload`).
- [x] Update `SecurityConfig` untuk `/graphql`, `/graphiql`.
- [x] Uji manual di GraphiQL, bandingkan hasil dengan REST lama.

**Fase 2 — Domain CRUD lain**
- [x] auth, user, role, merchant, cashier, category, order, transaction.

**Fase 3 — Stats**
- [x] `StatsResolver` (4 query: monthlyRevenue, yearlyRevenue, merchantSales, productSales).

**Fase 4 — Pembersihan**
- [x] Hapus 9 controller REST tersisa (semua kecuali `UploadController`).
- [x] (tidak ada `RestExceptionHandler` untuk dihapus — handler baru dibuat di `GraphQlExceptionHandler`).
- [x] Hapus seluruh `dto/requests/**` (input GraphQL memakai record `*Input` di resolver);
      `dto/responses/**` tetap untuk `ProtoMapper`.
- [x] Hapus `springdoc-openapi` bila tidak diperlukan (dibiarkan untuk `/api/upload`).
- [x] Content negotiation: pastikan hanya `/graphql`, `/api/upload`, `/static/**` yang tersisa.

**Fase 5 — Verifikasi**
- [x] Jalankan semua service + gateway, smoke test tiap domain via `HttpGraphQlTester`/GraphiQL.
- [x] Cek otorisasi per role (ADMIN/STAFF) untuk operasi sensitif.
- [x] `./mvnw -o -pl gateway -am test`.

---

## 13. Risiko & pertanyaan terbuka

1. **Envelope**: klien frontend pointofsale saat ini bergantung pada `{status,message,data}`;
   karena keputusan **full GraphQL**, klien harus pindah ke `errors[]` + `extensions.httpStatus`.
2. **`rating` Float vs Int**: DTO output `Float`, input REST `Integer`. Perlu dicek tipe proto
   untuk menentukan `Float`/`Int` di GraphQL.
3. **`cashier`**: `CreateCashierRequest` punya `userId` yang tidak dikirim ke proto create
   (lihat `CashierController`); perlu dicek mapping proto sebelum menentukan `CreateCashierInput`.
4. **`order_item`**: stub sudah di-wire tetapi tidak diekspos REST. Ekspos atau pertahankan paritas?
5. **`merchant` FindByUserId / UpdateMerchantStatus**: ada di proto, tidak di REST. Ekspos?
6. **`healthchecker`**: tetap sebagai query GraphQL (publik) atau pindah ke Actuator?
7. **Otorisasi field-level**: bila klien meminta field yang tidak diizinkan, seluruh query gagal
   (partial data + error). Apakah ini dapat diterima?
8. **Upload**: apakah `POST /api/upload` (REST kecil) dapat diterima, atau harus full GraphQL?

---

## 14. Referensi internal

- `pointofsale/gateway/pom.xml` — dependency gateway (tanpa GraphQL).
- `pointofsale/gateway/.../config/GrpcClientConfig.java` — stub gRPC per service (dipakai ulang resolver).
- `pointofsale/gateway/.../config/SecurityConfig.java` — filter chain + `PUBLIC_ENDPOINTS`.
- `pointofsale/gateway/.../security/AuthTokenFilter.java` — JWT → `SecurityContext`.
- `pointofsale/gateway/.../mapper/ProtoMapper.java` — proto → DTO (dipakai ulang).
- (tidak ada exception handler di gateway pointofsale — dibuat baru saat migrasi.)
- `pointofsale/common/src/main/proto/**` — kontrak gRPC tiap domain.
- **Pola implementasi**: `ecommerce/gateway` (`graphql/**`, `resources/graphql/**`,
  `controller/UploadController.java`, `GraphQl*Tests.java`) dan `ecommerce/gateway/GRAPHQL-MIGRATION.md`.

---

## 15. Ringkasan pemetaan controller → resolver

| Controller REST | Resolver GraphQL | Schema file |
|---|---|---|
| `AuthController` | `AuthResolver` | `auth.graphqls` |
| `UserController` | `UserResolver` | `user.graphqls` |
| `RoleController` | `RoleResolver` | `role.graphqls` |
| `MerchantController` | `MerchantResolver` | `merchant.graphqls` |
| `CashierController` | `CashierResolver` | `cashier.graphqls` |
| `CategoryController` | `CategoryResolver` | `category.graphqls` |
| `ProductController` | `ProductResolver` (+`UploadController`) | `product.graphqls` |
| `OrderController` | `OrderResolver` | `order.graphqls` |
| `TransactionController` | `TransactionResolver` | `transaction.graphqls` |
| `StatsController` | `StatsResolver` | `stats.graphqls` |

---

## 16. Catatan implementasi (penyesuaian dari rencana)

Saat implementasi, beberapa detail proto membuat rencana di §6/§13 perlu disesuaikan agar
paritas perilaku REST tetap terjaga dan field benar-benar terisi:

1. **`rating` adalah `float` di proto** (bukan `int`). Maka `Product.rating` dan
   `CreateProductInput.rating`/`UpdateProductInput.rating` memakai **`Float`** (bukan `Int`).
   Karena `ProductResponse` DTO semula tidak punya field `rating` (hanya `ProductResponseDeleteAt`),
   `rating` ditambahkan ke `ProductResponse` dan dipetakan di `ProtoMapper.product()` supaya field
   tidak selalu `null` (menghilangkan warning "Unmapped fields" GraphQL).
2. **`weight` adalah `string` di proto.** Input GraphQL tetap `Int` (paritas REST/DTO) dan dikirim
   sebagai `String.valueOf(...)`; output DTO `Integer` (parse aman dari string).
3. **`CreateCashierInput` menyertakan `userId: ID!`.** Proto `CreateCashierRequest` butuh
   `user_id` dan REST lama mengirimkannya (menjawab risiko §13.3), jadi input tidak boleh
   menghilangkannya.
4. **`CreateTransactionInput` tidak menyertakan `paymentStatus`.** Proto
   `CreateTransactionRequest` memang tidak punya field `payment_status` (hanya `Update` yang punya).
5. **Boolean mutation memakai `resp.getSuccess()`**, bukan `Gql.ok(resp.getStatus())` seperti
   ecommerce — proto pointofsale memakai field `bool success` (mis. `ApiResponseUserAll`).
6. **`me` memakai username** dari `SecurityContextHolder` (proto `GetMeRequest.username`),
   bukan `userId` seperti ecommerce.
7. **`order_item`, `merchantByUser`, `updateMerchantStatus`** tidak diekspos (paritas REST);
   stub `order-item` tetap di-wire di `GrpcClientConfig` seperti semula.
8. **`springdoc-openapi` dipertahankan** (kini hanya mendokumentasikan `POST /api/upload`).
9. Pesan error gRPC → `errors[].extensions.httpStatus` (+ `grpcCode`) lewat
   `GraphQlExceptionHandler`; transport tetap HTTP 200.

