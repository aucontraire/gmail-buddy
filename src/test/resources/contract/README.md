# Batch response contract fixtures

Canonical JSON examples of what `BatchOperationResponse` (the response of the feature-005 batch
endpoints — `batchTrash` / `batchUntrash` / `batchModifyLabels`) emits on the wire, for the three
outcome shapes: **full success**, **partial failure**, **total failure**.

These are the source-of-truth examples of this API's contract. `BatchOperationResponseContractTest`
serializes the real DTO (via `ResponseMapper.toBatchOperationResponse`) and asserts it matches these
fixtures, so an accidental change to the wire shape (field names, `status` vocabulary, per-id
outcome structure) fails the build instead of only breaking a downstream consumer at runtime.

Notes:
- The non-deterministic `metadata` object (`durationMs` varies per run) is intentionally **omitted**
  here; the test asserts it is present with numeric `durationMs`/`quotaUsed` but excludes it from the
  pinned shape.
- Object-key order (e.g. `failedOperations`) is not significant — the test compares JSON trees.
- Any consumer of this API may vendor these files to pin the same contract on its own side.
