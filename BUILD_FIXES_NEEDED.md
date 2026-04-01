# Build Fixes Applied

This document is historical context, not a live build-status report.

It summarizes notable build/runtime fixes that were applied during earlier stabilization work:

- Koin Compose dependency alignment
- Coil dependency rollback from the unsupported newer line
- Gemini authentication and response-handling fixes
- process-death recovery for pending captures
- Android theme and manifest/build cleanup
- zip-slip and path-normalization hardening in migration/export paths
- Gradle configuration cleanup, including removal of a hardcoded Java home

For the current runnable and verification workflow, use:

- [README.md](README.md)
- [RUNNING_THE_APP.md](RUNNING_THE_APP.md)
- [TESTING.md](TESTING.md)

In this workspace, Gradle commands could not be re-run during the documentation refresh because `java` is not installed and `JAVA_HOME` is unset.
