# LLD Problems

My low-level design learning journal: common design problems, working implementations, and the reasoning behind each solution.

## Problems

| # | Problem | Language | Status |
| --- | --- | --- | --- |
| 001 | [Parking Lot](problems/001-parking-lot/README.md) | Java | Initial solution reviewed; behavioral checks pass |
| 002 | [Cache](problems/002-cache/README.md) | Java | Initial solution reviewed; LRU/FIFO behavioral checks pass |
| 003 | [BookMyShow](problems/003-bookmyshow/README.md) | Java | Initial solution reviewed; seat-locking checks pass |

## Workflow

1. Create `problems/NNN-problem-name/` and copy the problem template into its `README.md`.
2. Capture requirements, assumptions, and the public API before refining the design.
3. Keep implementation files inside the problem folder; use a `tests/` subfolder when applicable.
4. Review responsibilities, invariants, extensibility, edge cases, and tradeoffs.
5. Record improvements and update the problem index above.

Current solutions use Java with dependency-free compilation and behavioral checks. See each problem for its run commands.
