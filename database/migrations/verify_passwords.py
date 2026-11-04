"""
Verify bcrypt password hashes for the VaahanSetu users table.

Usage:
  python verify_passwords.py

This script connects to your Neon DB, fetches the current password hashes
for admin and officer1, and tests them against the expected dev passwords
(admin123 and officer123). It prints a clear PASS/FAIL report.

Useful for:
- Sanity-checking that login will work before wiring up the Java auth code
- Diagnosing "wrong password" errors
- Verifying any future hash updates

Prerequisites:
  pip install psycopg2-binary bcrypt
  $env:DB_PASSWORD = "your-neon-password"
"""

import os
import sys
import psycopg2
import bcrypt

# ----------------------------------------------------------------------
# Neon connection (reads DB_PASSWORD from env, same as Spring Boot)
# ----------------------------------------------------------------------
NEON_URL = (
    "postgresql://neondb_owner:{}@"
    "ep-rough-mode-azjj7ke6.c-3.ap-southeast-1.aws.neon.tech/"
    "vaahansetu?sslmode=require"
).format(os.environ.get("DB_PASSWORD", ""))


def fetch_user_hashes():
    """Fetch username -> password_hash for all users from Neon."""
    try:
        with psycopg2.connect(NEON_URL) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT username, password_hash FROM users ORDER BY username")
                return dict(cur.fetchall())
    except Exception as e:
        print(f"ERROR: Could not connect to Neon DB: {e}")
        print("Did you set $env:DB_PASSWORD?")
        sys.exit(1)


def verify_password(password: str, stored_hash: str) -> bool:
    """Verify a plaintext password against a bcrypt hash."""
    try:
        return bcrypt.checkpw(password.encode(), stored_hash.encode())
    except Exception as e:
        print(f"  ERROR verifying: {e}")
        return False


def main():
    print("=" * 60)
    print("  VaahanSetu -- bcrypt password verification")
    print("=" * 60)
    print()

    hashes = fetch_user_hashes()
    print(f"Found {len(hashes)} users in the database:")
    for username in hashes:
        print(f"  - {username}")
    print()

    # Define the expected dev passwords
    expected = {
        "admin":    "admin123",
        "officer1": "officer123",
    }

    print("=" * 60)
    print("  Verification report")
    print("=" * 60)
    all_pass = True
    for username, expected_pwd in expected.items():
        if username not in hashes:
            print(f"\n[SKIP] User '{username}' not found in DB")
            continue

        stored_hash = hashes[username]
        print(f"\nUser: {username}")
        print(f"  Stored hash:  {stored_hash}")

        # Format checks
        if not stored_hash.startswith(("$2a$", "$2b$", "$2y$")):
            print(f"  FAIL: Hash doesn't start with $2a$/$2b$/$2y$ -- not bcrypt")
            all_pass = False
            continue
        if len(stored_hash) != 60:
            print(f"  FAIL: Hash is {len(stored_hash)} chars, expected 60")
            all_pass = False
            continue
        if "examplehash" in stored_hash:
            print(f"  FAIL: Hash is still a placeholder ('examplehash...')")
            all_pass = False
            continue

        # Password verification
        matches = verify_password(expected_pwd, stored_hash)
        if matches:
            print(f"  PASS: Hash correctly verifies against '{expected_pwd}'")
        else:
            print(f"  FAIL: Hash does NOT verify against '{expected_pwd}'")
            print(f"        This means login with '{expected_pwd}' will fail.")
            print(f"        Either the hash is wrong, or the password is different.")
            all_pass = False

        # Negative tests -- wrong passwords should NOT match
        wrong_passwords = ["", "wrong", "password", "admin", "officer", "123"]
        for wrong in wrong_passwords:
            if wrong == expected_pwd:
                continue
            if verify_password(wrong, stored_hash):
                print(f"  FAIL: Hash WRONGLY accepts '{wrong}'")
                all_pass = False

    print()
    print("=" * 60)
    if all_pass:
        print("  ALL CHECKS PASSED -- login will work correctly")
    else:
        print("  SOME CHECKS FAILED -- see above for details")
    print("=" * 60)


if __name__ == "__main__":
    main()
