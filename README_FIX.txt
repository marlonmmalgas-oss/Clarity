CLARITY COMBINED FIX

This patch is based on the current MainActivity.kt in marlonmmalgas-oss/Clarity.

Fixes included:
- Transaction dialog waits for the database insert to complete before closing.
- Expense and income transactions are explicitly stored with the correct type.
- Validation and database errors are shown inside the transaction dialog instead of silently dismissing it.
- Category loading is shown instead of presenting an unusable blank category selector.
- Budget has a selectable Expense category and separate monthly category spending limits.
- Budget shows actual-versus-planned income and expenses.
- Home shows a monthly plan snapshot.
- No Room schema changes; existing transaction records are preserved.

Apply from the Codespace repository root:
1. Extract this ZIP into the repository root.
2. Run: python3 apply_clarity_fix.py
3. Build and inspect the result before pushing.

The patch script checks each expected source section and stops without writing if the source differs.
