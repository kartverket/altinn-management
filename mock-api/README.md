# mock-api

A mock of Altinn, of Maskinporten, which issues the tokens Altinn's write routes require, and of the filtering service,
for running altinn-management locally. It keeps its data in memory, so every start begins from the seed in [`resources.json`](src/main/resources/resources.json): Kartverket's three MaskinportenSchema resources in
production, one of another organisation's, and Kartverket's GenericAccessResource `kv-geonorge` in TT02, with its policy
from [`policies`](src/main/resources/policies). Kartverket's 12 access lists in TT02, as
Altinn gave them on 26.11.2026, come from [`access-lists.json`](src/main/resources/access-lists.json). None of them
starts with `nrl-`, so with filtering on, the server lists none.

It copies Altinn's behaviour from Altinn's source code in
[Altinn/altinn-auth](https://github.com/Altinn/altinn-auth/tree/main/src/apps/Altinn.ResourceRegistry), not from the
documentation. When you add to it, check the source the same way.

## Running

```sh
./gradlew :mock-api:run   # from the repository root, on http://localhost:8090
```

## Routes

These are the routes of Altinn's Resource Registry it plays. Like in Altinn, anyone may read, and a write needs an Altinn
token from the exchange under [Tokens](#tokens), with the scope `altinn:resourceregistry/resource.write` or
`altinn:resourceregistry/resource.admin`.

| Route | Like in Altinn |
| :-- | :-- |
| `GET /resourceregistry/api/v1/resource/{id}` | The newest version of the resource, or the one in `?versionId=`. The id must match exactly |
| `GET /resourceregistry/api/v1/resource/search` | Filters on `id`, `resourceType`, `orgCode`, `organizationId`, `description`, `keyword` and `reference`, and ignores `title`. `resourceType` is a type's name or number, and any other value is 400. `reference` matches part of any reference, of every type. The filters run over every version, so when only an older version of a resource matches, that older version is returned |
| `POST /resourceregistry/api/v1/resource` | Creates a resource, and answers 201 with a `Location` and no body. First it checks the resource: the identifier, title, description and, when the resource can be delegated, right description in `en`, `nb` and `nn`, the owner's org code, and for a MaskinportenSchema a `MaskinportenScope` reference. Then it checks that the owner in the body is the token's organisation, and then the scope prefixes. An identifier that is taken is 409 |
| `PUT /resourceregistry/api/v1/resource/{id}` | Adds a new version that replaces the whole resource, and answers 200 with no body. It checks like a POST, but only the owner in the body, not the one the mock has |
| `POST` or `PUT /resourceregistry/api/v1/resource/{id}/policy` | Checks the XACML policy in the multipart field `policyFile`: every rule must be about the resource. It checks the owner the mock has, keeps the file as it was sent in place of the one before, and answers 201 with no body |
| `GET /resourceregistry/api/v1/resource/{id}/policy` | The policy as it was sent, as `text/xml`. Without the resource or a policy it answers 404 as plain text: "Unable to find resource" or "Unable to find requested policy" |

It also answers Altinn's two read routes for access lists the way Altinn does. Unlike resources, they need an Altinn
token with `altinn:resourceregistry/accesslist.read`, `accesslist.write`, `pdp:accesslist.read` or `resource.admin`,
and the mock's tokens are all Kartverket's, so the owner must be `kv` in any case. Another owner is 403, unless the
token has `pdp:accesslist.read` or `resource.admin`.

| Route | Like in Altinn |
| :-- | :-- |
| `GET /resourceregistry/api/v1/access-lists/{owner}` | 20 lists at a time, by identifier, as `{"links":{"next":…},"data":[…]}`. `next` is an absolute URL with `?token=`, the identifier the next page starts at as a JSON string in base64url, and is left out on the last page. A token that is not one is 400 |
| `GET /resourceregistry/api/v1/access-lists/{owner}/{identifier}` | The list. The identifier must match exactly, and a list the mock does not have is 404 |

Errors look like Altinn's: ProblemDetails sent as `application/json`, or a JSON string. A missing or unknown token gives
401 and a token without access gives 403, both without a body.

A resource is kept the way Altinn keeps it: field names may have any case, unknown fields and nulls are dropped, and an
omitted field gets Altinn's default, like `delegable` and `visible`, which are `true`. A value of the wrong type, or
`null` in a field Altinn cannot leave empty, like `delegable`, is 400. The mock knows the org codes of its seed resources'
owners: `kv` for 971040238 and `slf` for 981544315.

Which check of scope prefixes Altinn runs in production depends on a setting we cannot see. The mock runs Altinn's
default: every Maskinporten scope of the resource must start with the token's `consumer_prefix`, which is `kartverk`,
and a colon, unless the token has the admin scope. Otherwise it answers 401, with a body that lists the scopes under
`InvalidPrefix`.

## Tokens

| Route | Like in Maskinporten and Altinn |
| :-- | :-- |
| `POST /token` | Maskinporten's token endpoint for JWT grants. It checks a grant's claims like Maskinporten does: `aud` must be `http://localhost:8090/`, `iss`, `iat`, `exp` and `scope` must be there, and a grant can only be used once. It does not check the signature, and gives any client the scope it asks for, on behalf of Kartverket, with the scope prefix `kartverk` |
| `GET /authentication/api/v1/exchange/maskinporten` | Altinn's exchange of a Maskinporten token for an Altinn token. The Altinn token lasts 30 minutes and has the Maskinporten token's claims, `scope` and `consumer` among them |

The mock's tokens are JWTs without a signature. Instead, the mock only accepts a token it issued itself.

## Filtering

| Route | What it plays |
| :-- | :-- |
| `POST /filtering` | The filtering service, which tells the server which resources and scopes a caller has access to. It answers every caller the same way: the resources that start with `nrl-` and the scopes that start with `kartverk:nrl.`. That is two of the three Kartverket resources in the seed, so a list shows that filtering is on |

The filtering service is ours, not Altinn's, so this route does not copy Altinn's source. It answers in the format
the server's `ExternalFilteringClient` reads, and does not look at what the server sends.
