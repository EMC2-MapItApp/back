#!/usr/bin/env python3
"""
Migration script: PostgreSQL -> MongoDB Atlas

Usage:
  - Create a `.env` file based on `.env.example` with your credentials.
  - Install dependencies: `pip install -r requirements.txt`
  - Run: `python migrate_postgres_to_mongo.py`

The script migrates `users`, `user_profile_details`, and `places`.
"""
import os
import sys
import json
import logging
from typing import Any, Dict

from dotenv import load_dotenv
import psycopg2
import psycopg2.extras
from pymongo import MongoClient, UpdateOne
from bson import DBRef

load_dotenv()

LOG = logging.getLogger("pg2mongo")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

PG_DSN = os.getenv("PG_DSN")  # e.g. postgresql://user:pass@host:5432/dbname
MONGODB_URI = os.getenv("MONGODB_URI")  # e.g. mongodb+srv://user:pass@cluster0.../admin
MONGODB_DB = os.getenv("MONGODB_DATABASE", "mapit_db")

BATCH_SIZE = int(os.getenv("MIGRATION_BATCH_SIZE", "500"))


def pg_connect():
    if not PG_DSN:
        LOG.error("PG_DSN is not set. See .env.example")
        sys.exit(1)
    return psycopg2.connect(PG_DSN)


def mongo_connect():
    if not MONGODB_URI:
        LOG.error("MONGODB_URI is not set. See .env.example")
        sys.exit(1)
    return MongoClient(MONGODB_URI)


def fetch_users(pg_conn):
    sql = """
    SELECT u.id::text AS id, u.name, u.email, u.password_hash AS password_hash, u.user_type,
      (SELECT json_agg(capability_id) FROM user_unlocked_capabilities uc WHERE uc.user_id = u.id) AS unlocked_capabilities,
      (SELECT json_agg(location_type_id) FROM user_favorite_location_types f WHERE f.user_id = u.id) AS favorite_location_type_ids
    FROM users u
    """
    with pg_conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        for row in cur:
            yield row


def fetch_profile_details(pg_conn):
    sql = "SELECT user_id::text AS user_id, phone, city, province, bio, birth_date, level, xp, avatar_url, created_at, updated_at FROM user_profile_details"
    with pg_conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        for row in cur:
            yield row


def fetch_places(pg_conn):
    sql = "SELECT p.id::text AS id, p.owner_id::text AS owner_id, p.name, p.description, p.location_type_id, p.lat, p.lng, p.address, p.metadata FROM places p"
    with pg_conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql)
        for row in cur:
            yield row


def migrate_profiles(pg_conn, mongo_db):
    LOG.info("Migrating profile details...")
    coll = mongo_db.user_profile_details
    ops = []
    count = 0
    for row in fetch_profile_details(pg_conn):
        doc = {k: (v.isoformat() if hasattr(v, 'isoformat') else v) for k, v in row.items()}
        _id = doc.pop('user_id')
        ops.append(UpdateOne({'_id': _id}, {'$set': doc}, upsert=True))
        count += 1
        if len(ops) >= BATCH_SIZE:
            coll.bulk_write(ops)
            ops = []
    if ops:
        coll.bulk_write(ops)
    LOG.info(f"Migrated {count} profile documents")


def migrate_users(pg_conn, mongo_db):
    LOG.info("Migrating users...")
    coll = mongo_db.users
    ops = []
    count = 0
    for row in fetch_users(pg_conn):
        user_id = row['id']
        doc = {
            '_id': user_id,
            'name': row.get('name'),
            'email': row.get('email'),
            'passwordHash': row.get('password_hash'),
            'userType': row.get('user_type'),
            'unlockedCapabilities': row.get('unlocked_capabilities') or [],
            'favoriteLocationTypeIds': row.get('favorite_location_type_ids') or [],
        }
        # reference profile details if exists
        # we'll create a DBRef so Spring Data @DBRef can resolve it
        # only set if profile doc exists in DB
        if mongo_db.user_profile_details.count_documents({'_id': user_id}, limit=1):
            doc['profileDetails'] = DBRef('user_profile_details', user_id)

        ops.append(UpdateOne({'_id': user_id}, {'$set': doc}, upsert=True))
        count += 1
        if len(ops) >= BATCH_SIZE:
            coll.bulk_write(ops)
            ops = []
    if ops:
        coll.bulk_write(ops)
    LOG.info(f"Migrated {count} user documents")


def migrate_places(pg_conn, mongo_db):
    LOG.info("Migrating places...")
    coll = mongo_db.places
    ops = []
    count = 0
    for row in fetch_places(pg_conn):
        _id = row['id']
        doc = {
            '_id': _id,
            'ownerId': row.get('owner_id'),
            'name': row.get('name'),
            'description': row.get('description'),
            'locationTypeId': row.get('location_type_id'),
            'lat': float(row['lat']) if row.get('lat') is not None else None,
            'lng': float(row['lng']) if row.get('lng') is not None else None,
            'address': row.get('address'),
            'metadata': row.get('metadata') if isinstance(row.get('metadata'), dict) else (json.loads(row['metadata']) if row.get('metadata') else None)
        }
        ops.append(UpdateOne({'_id': _id}, {'$set': doc}, upsert=True))
        count += 1
        if len(ops) >= BATCH_SIZE:
            coll.bulk_write(ops)
            ops = []
    if ops:
        coll.bulk_write(ops)
    LOG.info(f"Migrated {count} place documents")


def main():
    LOG.info("Starting migration")
    pg_conn = pg_connect()
    mc = mongo_connect()
    try:
        mongo_db = mc[MONGODB_DB]
        migrate_profiles(pg_conn, mongo_db)
        migrate_users(pg_conn, mongo_db)
        migrate_places(pg_conn, mongo_db)
    finally:
        pg_conn.close()
        mc.close()
    LOG.info("Migration finished")


if __name__ == '__main__':
    main()
