# 111 gift-selection-decodes-over-the-brand
epic: none · pr: none

## Decisions
- @gift @encoding @brand — `decodeGiftSelection` takes an `EncodedGiftId` and returns a `GiftSelection` with no failure branch, reading the enhancement digit and the base id by position; a caller that holds a raw string runs `EncodedGiftIdSchema.safeParse` and drops or reports what fails before decoding. The `no-throw-in-lib` scan bans `throw` in page lib code, `getBaseGiftId` threw on a decode failure the brand already excluded, and the decoder's `string` parameter forced a nullable return that every branded caller guarded against for nothing.
  REJECTED: a nullable return with callers checking — the pre-brand signature; every caller pays for a case the brand rules out, `findEncodedGiftId` compares `null` against a gift id on a dead branch, and it reopens the totality the brand was introduced to provide.
  REJECTED: total over the brand but decoded through the nullable tier — keeps the second regex and needs a throw or non-null assertion inside the body, since `match` still types nullable; the throw is what the scan rejects.
  REJECTED: suppressing the rule at the site — leaves a runtime restatement of a compile-time fact in the functional core and teaches the next reader that the ban has exceptions.
- @gift @encoding @brand @raw-sites — The planner reader gate admits content as a loose record and casts it to the branded shape, so stored ids reach the extractor unvalidated; a forged brand (`as EncodedGiftId`) fails inside Zod on the base id rather than returning null. The content extractor and the planner validator are the two sites that meet raw storage, both gate with `safeParse`, and a new raw site must do the same.
- @gift @encoding @single-pattern — One regex defines the encoded language, in the brand schema. The decoder pattern and the test that checked the two agreed are gone; the round trip over every schema-accepted id stays as the totality proof, and `GIFT_ID_LENGTH` joins `GIFT_ID_PATTERN` in the id constants, since positional decoding needs the body length as a number.

## Takeaway
- takeaway: once a brand proves a value's shape, a decoder over the brand should be total; a failure branch it cannot reach is a restated check that forces every caller to pretend otherwise.
