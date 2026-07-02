Migration: PostgreSQL -> MongoDB Atlas
===================================

This document explains how to run the migration script that exports data from the existing PostgreSQL database and imports it into MongoDB (Atlas or local).

Prerequisites
 - Python 3.10+
 - A running PostgreSQL instance with the application's schema and data
 - A MongoDB Atlas cluster (or local MongoDB) and connection URI

Steps

1. Copy `.env.example` to `.env` and fill in credentials (Postgres DSN and `MONGODB_URI`).

2. Install Python dependencies from `scripts/requirements.txt`:

```bash
python -m pip install -r scripts/requirements.txt
```

3. Run the migration:

```bash
python scripts/migrate_postgres_to_mongo.py
```

Notes & mapping
- Users are migrated to collection `users`. Profile details go to `user_profile_details` (referenced via DBRef in `users.profileDetails`).
- Places are migrated to `places` with owner referenced by `ownerId` (string).
- Arrays and JSON fields are preserved when possible.

After migration
- Start the backend pointing to the MongoDB Atlas URI (set `spring.data.mongodb.uri` in `application.yaml` or via env var `MONGODB_URI`).
- Run integration smoke tests and verify key queries.

If you want, I can:
- Add a dry-run mode to the script.
- Add a reversible migration plan or verification checks.
