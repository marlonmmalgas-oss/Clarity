
from pathlib import Path
p=Path("app/src/main/java/za/co/clarity/MainActivity.kt")
if not p.exists():
    raise SystemExit("Run this from the Clarity repository root. MainActivity.kt not found.")
s=p.read_text()
def once(old,new,label):
    global s
    n=s.count(old)
    if n!=1: raise SystemExit(f"Stopped: expected one match for {label}, found {n}. No file changed.")
    s=s.replace(old,new,1)

if "import androidx.compose.ui.platform.LocalContext" not in s:
    s=s.replace("import androidx.compose.ui.unit.sp\n","import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.platform.LocalContext\nimport org.json.JSONObject\n")

once('''1 -> BudgetScreen(plans, tx, onSave = { runSave { savePlan(it) } })''',
     '''1 -> BudgetScreen(plans, tx, categories, onSave = { runSave { savePlan(it) } })''',"Budget call")
once('''0 -> Overview(tx, income, expenses, monthIncome, monthExpense, onAdd = { dialog = "transaction" })''',
     '''0 -> Overview(tx, income, expenses, monthIncome, monthExpense, plans, onAdd = { dialog = "transaction" })''',"Home call")
once('''mi: Double, me: Double, onAdd: () -> Unit
) {''',
     '''mi: Double, me: Double, plans: List<MonthlyPlan>, onAdd: () -> Unit
) {''',"Overview signature")
once('''        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("All-time income", money(income), Mint, Modifier.weight(1f))
                SmallStat("All-time spending", money(expenses), Red, Modifier.weight(1f))
            }
        }
        item { Text("Cash flow"''',
     '''        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("All-time income", money(income), Mint, Modifier.weight(1f))
                SmallStat("All-time spending", money(expenses), Red, Modifier.weight(1f))
            }
        }
        item { HomeBudgetSnapshot(tx, plans) }
        item { Text("Cash flow"''',"Home budget card")

# Replace the whole budget screen, retaining the existing Room MonthlyPlan table.
a=s.index("@Composable\nprivate fun BudgetScreen(")
b=s.index("@Composable\nprivate fun SummaryLine",a)
budget=r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetScreen(plans: List<MonthlyPlan>, tx: List<MoneyTransaction>, categories: List<MoneyCategory>, onSave: (MonthlyPlan) -> Unit) {
    val context= LocalContext.current
    val prefs= remember { context.getSharedPreferences("clarity_category_limits", android.content.Context.MODE_PRIVATE) }
    var yearly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(monthKeyFor(System.currentTimeMillis())) }
    var incomeText by remember(selected,plans) { mutableStateOf((plans.find{it.monthKey==selected}?.incomeEstimate ?: 0.0).toString()) }
    var expenseText by remember(selected,plans) { mutableStateOf((plans.find{it.monthKey==selected}?.expenseEstimate ?: 0.0).toString()) }
    var savingsText by remember(selected,plans) { mutableStateOf((plans.find{it.monthKey==selected}?.savingsEstimate ?: 0.0).toString()) }
    var chosen by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var limits by remember(selected) {
        val j=try { JSONObject(prefs.getString(selected,"{}") ?: "{}") } catch (_:Exception) { JSONObject() }
        mutableStateOf(j.keys().asSequence().associateWith { j.optDouble(it) }.filterValues { it>0 })
    }
    val expenseCats=categories.filter{it.type=="Expense"}.sortedBy{it.name}
    LaunchedEffect(expenseCats) { if(chosen !in expenseCats.map{it.name}) chosen=expenseCats.firstOrNull()?.name.orEmpty() }
    val actual=tx.filter{monthKeyFor(it.date)==selected}
    val ai=actual.filter{it.type=="Income"}.sumOf{it.amount}
    val ae=actual.filter{it.type=="Expense"}.sumOf{it.amount}
    val plan=plans.find{it.monthKey==selected} ?: MonthlyPlan(selected)
    val year=selected.take(4).toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
    val months=(1..12).map{"%04d-%02d".format(year,it)}
    val yearTx=tx.filter{monthKeyFor(it.date).startsWith("$year-")}
    val yearPlans=months.map{k->plans.find{it.monthKey==k} ?: MonthlyPlan(k)}
    fun store(next:Map<String,Double>) {
        val j=JSONObject(); next.forEach{(k,v)->j.put(k,v)}
        prefs.edit().putString(selected,j.toString()).apply(); limits=next
    }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(13.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!yearly,onClick={yearly=false},label={Text("Monthly")})
            FilterChip(selected=yearly,onClick={yearly=true},label={Text("Yearly")})
        } }
        if(yearly) {
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton(onClick={selected="${year-1}-01"}){Icon(Icons.Default.ChevronLeft,null,tint=Mint)}
                Text("$year annual overview",color=White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                IconButton(onClick={selected="${year+1}-01"}){Icon(Icons.Default.ChevronRight,null,tint=Mint)}
            } }
            item { Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("YEAR AT A GLANCE",color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold)
                    PlanCompareLine("Income",yearTx.filter{it.type=="Income"}.sumOf{it.amount},yearPlans.sumOf{it.incomeEstimate},Mint)
                    PlanCompareLine("Expenses",yearTx.filter{it.type=="Expense"}.sumOf{it.amount},yearPlans.sumOf{it.expenseEstimate},Red)
                    SummaryLine("Savings targets",yearPlans.sumOf{it.savingsEstimate},Blue)
                }
            } }
            item { Text("Monthly breakdown",color=White,fontSize=18.sp,fontWeight=FontWeight.Bold) }
            items(months) { key ->
                val p=yearPlans.first{it.monthKey==key}; val m=tx.filter{monthKeyFor(it.date)==key}
                Card(onClick={selected=key;yearly=false},colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text(monthLabel(key),color=White,fontWeight=FontWeight.Bold)
                        PlanCompareLine("Income",m.filter{it.type=="Income"}.sumOf{it.amount},p.incomeEstimate,Mint)
                        PlanCompareLine("Expenses",m.filter{it.type=="Expense"}.sumOf{it.amount},p.expenseEstimate,Red)
                    }
                }
            }
        } else {
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton(onClick={selected=shiftMonth(selected,-1)}){Icon(Icons.Default.ChevronLeft,null,tint=Mint)}
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(monthLabel(selected),color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text("MONTHLY PLAN",color=Muted,fontSize=9.sp,letterSpacing=1.5.sp)
                }
                IconButton(onClick={selected=shiftMonth(selected,1)}){Icon(Icons.Default.ChevronRight,null,tint=Mint)}
            } }
            item { Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("ACTUAL VS PLAN",color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)
                    PlanCompareLine("Income",ai,plan.incomeEstimate,Mint)
                    PlanCompareLine("Expenses",ae,plan.expenseEstimate,Red)
                    HorizontalDivider(color=Panel2)
                    SummaryLine("Actual net",ai-ae,White)
                    SummaryLine("Savings target",plan.savingsEstimate,Blue)
                }
            } }
            item { Text("Monthly targets",color=White,fontSize=18.sp,fontWeight=FontWeight.Bold) }
            item { BudgetInput("Planned income (R)",incomeText,{incomeText=it},Mint) }
            item { BudgetInput("Planned total expenses (R)",expenseText,{expenseText=it},Red) }
            item { BudgetInput("Savings target (R)",savingsText,{savingsText=it},Blue) }
            item { Button(onClick={onSave(plan.copy(incomeEstimate=incomeText.toDoubleOrNull()?:0.0,expenseEstimate=expenseText.toDoubleOrNull()?:0.0,savingsEstimate=savingsText.toDoubleOrNull()?:0.0))},
                modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.CheckCircle,null); Spacer(Modifier.width(7.dp)); Text("Save monthly plan",fontWeight=FontWeight.Bold)
            } }
            item { Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Expense category limits",color=White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Text("Set a separate monthly spending limit for each category.",color=Muted,fontSize=12.sp)
            } }
            item { Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(expenseCats.isEmpty()) Text("Add expense categories in More first.",color=Muted,fontSize=12.sp)
                    else {
                        ExposedDropdownMenuBox(expanded=menu,onExpandedChange={menu=it}) {
                            OutlinedTextField(value=chosen,onValueChange={},readOnly=true,label={Text("Expense category")},
                                trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(menu)},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                expenseCats.forEach{c->DropdownMenuItem(text={Text(c.name)},onClick={chosen=c.name;menu=false})}
                            }
                        }
                        OutlinedTextField(value=amount,onValueChange={amount=it},label={Text("Monthly limit (R)")},singleLine=true,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                        Button(onClick={val v=amount.toDoubleOrNull();if(chosen.isNotBlank()&&v!=null&&v>0){store(limits+(chosen to v));amount=""}},
                            modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp)) {
                            Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Add / update limit")
                        }
                    }
                    if(limits.isEmpty()) Text("No category limits set for this month.",color=Muted,fontSize=12.sp)
                    limits.toSortedMap().forEach{(name,target)->
                        val spent=actual.filter{it.type=="Expense"&&it.category==name}.sumOf{it.amount}
                        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(name,color=White,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                                Text("${money(spent)} / ${money(target)}",color=if(spent>target)Red else Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                            }
                            LinearProgressIndicator(progress={(spent/target).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(7.dp),
                                color=if(spent>target)Red else Mint,trackColor=Panel2)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                TextButton(onClick={store(limits-name)}){Text("Remove",color=Muted,fontSize=10.sp)}
                            }
                        }
                    }
                }
            } }
            item { Text("Transactions this month",color=White,fontSize=17.sp,fontWeight=FontWeight.Bold) }
            if(actual.isEmpty()) item { Text("No transactions recorded for this month yet.",color=Muted,fontSize=12.sp) }
            items(actual,key={it.id}){TransactionRow(it)}
        }
    }
}

@Composable
private fun PlanCompareLine(label:String,actual:Double,planned:Double,color:Color) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,color=White,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            Text("${money(actual)} / ${money(planned)}",color=color,fontSize=12.sp,fontWeight=FontWeight.Bold)
        }
        LinearProgressIndicator(progress={if(planned>0)(actual/planned).toFloat().coerceIn(0f,1f) else 0f},
            modifier=Modifier.fillMaxWidth().height(6.dp),color=color,trackColor=Panel2)
        Text(if(planned<=0)"No target set" else "${(actual/planned*100).toInt()}% of plan",color=Muted,fontSize=10.sp)
    }
}

@Composable
private fun HomeBudgetSnapshot(tx:List<MoneyTransaction>,plans:List<MonthlyPlan>) {
    val key=monthKeyFor(System.currentTimeMillis())
    val p=plans.find{it.monthKey==key}?:MonthlyPlan(key)
    val m=tx.filter{monthKeyFor(it.date)==key}
    val i=m.filter{it.type=="Income"}.sumOf{it.amount}
    val e=m.filter{it.type=="Expense"}.sumOf{it.amount}
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("MONTHLY PLAN",color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)
                Text(monthLabel(key),color=Muted,fontSize=10.sp)
            }
            PlanCompareLine("Income",i,p.incomeEstimate,Mint)
            PlanCompareLine("Expenses",e,p.expenseEstimate,Red)
            if(p.incomeEstimate==0.0&&p.expenseEstimate==0.0) Text("Set targets in Budget to track progress here.",color=Muted,fontSize=11.sp)
        }
    }
}

'''
s=s[:a]+budget+s[b:]

# Make transaction saving wait for the Room insert to finish. Previously the
# dialog closed immediately, even if the database insert failed.
old_call = """TransactionDialog(categories, onDismiss = { dialog = "" }) { title, amount, type, cat, note ->
                runSave { addTx(MoneyTransaction(title = title, amount = amount, type = type, category = cat, note = note)) }
                dialog = ""
            }"""
new_call = """TransactionDialog(categories, onDismiss = { dialog = "" }) { title, amount, type, cat, note ->
                addTx(MoneyTransaction(title = title.trim(), amount = amount, type = if (type == "Income") "Income" else "Expense", category = cat.trim(), note = note.trim()))
            }"""
once(old_call, new_call, "transaction save callback")

old_sig = """    onSave: (String, Double, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }"""
new_sig = """    onSave: suspend (String, Double, String, String, String) -> Unit
) {
    val saveScope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }"""
once(old_sig, new_sig, "transaction dialog callback signature")

old_confirm = """        confirmButton = {
            TextButton(onClick = {
                val n = amount.toDoubleOrNull()
                if (title.isNotBlank() && n != null && n > 0 && category.isNotBlank()) onSave(title.trim(), n, type, category, note.trim())
            }) { Text("Save", color = Mint) }
        },"""
new_confirm = """        confirmButton = {
            TextButton(enabled = !saving && filtered.isNotEmpty(), onClick = {
                val n = amount.trim().replace(",", ".").toDoubleOrNull()
                when {
                    title.isBlank() -> saveError = "Enter a description."
                    n == null || n <= 0 -> saveError = "Enter a valid amount greater than zero."
                    category.isBlank() -> saveError = "Choose a category for this transaction."
                    else -> saveScope.launch {
                        saving = true
                        saveError = ""
                        try {
                            onSave(title.trim(), n, type, category, note.trim())
                            onDismiss()
                        } catch (e: Exception) {
                            saveError = "Could not save: ${e.localizedMessage ?: "database error"}"
                        } finally {
                            saving = false
                        }
                    }
                }
            }) { Text(if (saving) "Saving…" else "Save", color = Mint) }
        },"""
once(old_confirm, new_confirm, "transaction save button")

old_note = """                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }"""
new_note = """                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
                if (filtered.isEmpty()) Text("Expense/income categories are still loading. Please wait a moment.", color = Red, fontSize = 11.sp)
                if (saveError.isNotBlank()) Text(saveError, color = Red, fontSize = 12.sp)
            }"""
once(old_note, new_note, "transaction validation feedback")

p.write_text(s)
print("PATCH_APPLIED: Budget category limits, actual-vs-plan comparisons, and Home monthly plan summary.")
print("No Room schema migration; existing transactions and budgets remain in place.")
