# 060 page-envelope

## Decisions

- @api @pagination — Paginated endpoints serialize through Spring Data's stable
  `PagedModel` envelope (`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`):
  `content` plus a `page` object of exactly `size`, `number`, `totalElements`,
  `totalPages`. Serializing `PageImpl` directly is explicitly unstable across Spring
  versions, so the wire contract was hostage to dependency bumps; the frontend schemas
  now pin the envelope with strict parsing.
  REJECTED: keep serializing `PageImpl` — a minor-version upgrade can silently change
  the JSON shape under every paginated consumer.
  REJECTED: a hand-rolled envelope DTO per endpoint — re-implements what Spring Data
  ships, per endpoint, with no drift guard.

## Takeaway

- takeaway: a wire shape produced by serializing a framework's internal type is a
  contract nobody signed; either pin it to the framework's stable representation or
  own the DTO outright.
