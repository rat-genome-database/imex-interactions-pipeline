# imex-interactions-pipeline
Load protein interactions from IMEX database.

LOGIC

- incoming interactions are merged before loading for speedup and better reporting
- stale data processing runs only after all IMEX API requests complete successfully
