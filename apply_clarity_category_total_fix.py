from pathlib import Path

path = Path("app/src/main/java/za/co/clarity/MainActivity.kt")
if not path.is_file():
    raise SystemExit("MainActivity.kt not found. Run this from the Clarity repository root.")

source = path.read_text(encoding="utf-8")

def replace_once(old, new, label):
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Stopped safely: expected 1 match for {label}, found {count}. No source changes made.")
    source = source.replace(old, new, 1)

replace_once(
    '''    fun store(next:Map<String,Double>) {
        val j=JSONObject(); next.forEach{(k,v)->j.put(k,v)}
        prefs.edit().putString(selected,j.toString()).apply(); limits=next
    }''',
    '''    fun store(next:Map<String,Double>) {
        val j=JSONObject(); next.forEach{(k,v)->j.put(k,v)}
        val categoryTotal = next.values.sum()
        prefs.edit().putString(selected,j.toString()).apply()
        limits=next
        expenseText=categoryTotal.toString()
        onSave(plan.copy(expenseEstimate=categoryTotal))
    }''',
    "category-limit save function"
)

replace_once(
    '''            item { BudgetInput("Planned total expenses (R)",expenseText,{expenseText=it},Red) }''',
    '''            item {
                val displayedExpensePlan = if (limits.isNotEmpty()) limits.values.sum().toString() else expenseText
                BudgetInput(
                    "Planned total expenses (R)",
                    displayedExpensePlan,
                    { typed -> if (limits.isEmpty()) expenseText=typed },
                    Red
                )
            }''',
    "planned expenses field"
)

replace_once(
    '''            item { Button(onClick={onSave(plan.copy(incomeEstimate=incomeText.toDoubleOrNull()?:0.0,expenseEstimate=expenseText.toDoubleOrNull()?:0.0,savingsEstimate=savingsText.toDoubleOrNull()?:0.0))},''',
    '''            item { Button(onClick={
                val categoryTotal = limits.values.sum()
                val plannedExpenses = if (limits.isNotEmpty()) categoryTotal else (expenseText.toDoubleOrNull() ?: 0.0)
                onSave(plan.copy(
                    incomeEstimate=incomeText.toDoubleOrNull()?:0.0,
                    expenseEstimate=plannedExpenses,
                    savingsEstimate=savingsText.toDoubleOrNull()?:0.0
                ))
            },''',
    "monthly plan save calculation"
)

path.write_text(source, encoding="utf-8")
print("PATCH_APPLIED: category limits now total automatically into the monthly expense plan.")
print("Each add/update/remove saves the recalculated total to the monthly plan.")
