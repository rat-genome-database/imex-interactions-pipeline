# imex-interactions-pipeline
Load protein interactions from IMEX database.

LOGIC

- incoming interactions are merged before loading for speedup and consistent reporting
- stale data processing runs only after all IMEX API requests complete successfully
- to avoid processing of partial data, download retrials are implemented