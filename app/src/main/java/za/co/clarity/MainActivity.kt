package za.co.clarity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val Ink = Color(0xFF080D16)
private val Panel = Color(0xFF111B2A)
private val Panel2 = Color(0xFF172438)
private val Mint = Color(0xFF39D6A0)
private val Muted = Color(0xFF91A0B5)
private val White = Color(0xFFF3F7FC)
private val Red = Color(0xFFFF7185)
private val Blue = Color(0xFF62A8FF)

private fun money(v: Double): String =
    "R" + NumberFormat.getNumberInstance(Locale("en", "ZA")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }.format(v)

private fun monthKeyFor(time: Long) =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))

private fun monthLabel(key: String) =
    try {
        SimpleDateFormat("MMMM yyyy", Locale.US).format(
            SimpleDateFormat("yyyy-MM", Locale.US).parse(key)!!
        )
    } catch (_: Exception) { key }

private fun shiftMonth(key: String, amount: Int): String {
    val cal = Calendar.getInstance()
    cal.time = SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: Date()
    cal.add(Calendar.MONTH, amount)
    return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = ClarityDatabase.get(this).dao()
        lifecycleScope.launch {
            listOf(
                MoneyCategory("Food", "Expense"),
                MoneyCategory("Transport", "Expense"),
                MoneyCategory("Housing", "Expense"),
                MoneyCategory("Bills", "Expense"),
                MoneyCategory("Shopping", "Expense"),
                MoneyCategory("Health", "Expense"),
                MoneyCategory("Entertainment", "Expense"),
                MoneyCategory("Other", "Expense"),
                MoneyCategory("Salary", "Income"),
                MoneyCategory("Business", "Income"),
                MoneyCategory("Other income", "Income")
            ).forEach { dao.addCategory(it) }
        }
        setContent {
            val tx by dao.transactions().collectAsState(initial = emptyList())
            val categories by dao.categories().collectAsState(initial = emptyList())
            val goals by dao.goals().collectAsState(initial = emptyList())
            val plans by dao.plans().collectAsState(initial = emptyList())
            ClarityApp(
                tx, categories, goals, plans,
                addTx = { dao.addTransaction(it) },
                deleteTx = { dao.deleteTransaction(it) },
                addCat = { name, type -> dao.addCategory(MoneyCategory(name, type)) },
                addGoal = { dao.addGoal(it) },
                updateGoal = { id, amount -> dao.updateGoal(id, amount) },
                deleteGoal = { dao.deleteGoal(it) },
                savePlan = { dao.savePlan(it) }
            )
        }
    }
}

@Composable
private fun ClarityApp(
    tx: List<MoneyTransaction>,
    categories: List<MoneyCategory>,
    goals: List<SavingsGoal>,
    plans: List<MonthlyPlan>,
    addTx: suspend (MoneyTransaction) -> Unit,
    deleteTx: suspend (Long) -> Unit,
    addCat: suspend (String, String) -> Unit,
    addGoal: suspend (SavingsGoal) -> Unit,
    updateGoal: suspend (Long, Double) -> Unit,
    deleteGoal: suspend (Long) -> Unit,
    savePlan: suspend (MonthlyPlan) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var noticeIsError by remember { mutableStateOf(false) }

    fun runSave(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                noticeIsError = false
                notice = "Saved successfully"
            } catch (e: Exception) {
                noticeIsError = true
                notice = "Save failed: ${e.localizedMessage ?: "Please try again"}"
            }
        }
    }

    val income = tx.filter { it.type == "Income" }.sumOf { it.amount }
    val expenses = tx.filter { it.type == "Expense" }.sumOf { it.amount }
    val currentMonth = monthKeyFor(System.currentTimeMillis())
    val monthTx = tx.filter { monthKeyFor(it.date) == currentMonth }
    val monthIncome = monthTx.filter { it.type == "Income" }.sumOf { it.amount }
    val monthExpense = monthTx.filter { it.type == "Expense" }.sumOf { it.amount }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Mint, onPrimary = Ink,
        onBackground = White, onSurface = White, secondary = Blue
    )) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = White) {
                    listOf("Home", "Budget", "Activity", "Goals", "More").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                Icon(when (i) {
                                    0 -> Icons.Default.Dashboard
                                    1 -> Icons.Default.CalendarMonth
                                    2 -> Icons.Default.ReceiptLong
                                    3 -> Icons.Default.Savings
                                    else -> Icons.Default.Settings
                                }, null)
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Mint, selectedTextColor = Mint,
                                indicatorColor = Panel2, unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CLARITY", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text(
                            when (tab) { 0 -> "Your money, in focus"; 1 -> "Budget planner"; 2 -> "Transactions"; 3 -> "Savings goals"; else -> "Manage categories" },
                            color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_clarity_logo),
                        contentDescription = "CLARITY logo",
                        modifier = Modifier.size(48.dp)
                    )
                }
                when (tab) {
                    0 -> Overview(tx, income, expenses, monthIncome, monthExpense, onAdd = { dialog = "transaction" })
                    1 -> BudgetScreen(plans, tx, onSave = { runSave { savePlan(it) } })
                    2 -> ActivityScreen(tx, onAdd = { dialog = "transaction" }, onDelete = { id -> runSave { deleteTx(id) } })
                    3 -> GoalsScreen(
                        goals,
                        onAdd = { dialog = "goal" },
                        onUpdate = { g -> runSave { updateGoal(g.id, g.saved) } },
                        onDelete = { id -> runSave { deleteGoal(id) } }
                    )
                    else -> MoreScreen(categories, onAdd = { dialog = "category" })
                }
            }
        }

        if (dialog == "transaction") {
            TransactionDialog(categories, onDismiss = { dialog = "" }) { title, amount, type, cat, note ->
                runSave { addTx(MoneyTransaction(title = title, amount = amount, type = type, category = cat, note = note)) }
                dialog = ""
            }
        }
        if (dialog == "category") {
            CategoryDialog(onDismiss = { dialog = "" }) { name, type ->
                runSave { addCat(name, type) }
                dialog = ""
            }
        }
        if (dialog == "goal") {
            GoalDialog(onDismiss = { dialog = "" }) { name, target ->
                runSave { addGoal(SavingsGoal(name = name, target = target)) }
                dialog = ""
            }
        }
        if (notice.isNotBlank()) {
            LaunchedEffect(notice) {
                kotlinx.coroutines.delay(2800)
                notice = ""
            }
            SnackbarHost(
                hostState = remember { SnackbarHostState() },
                modifier = Modifier
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = if (noticeIsError) Color(0xFF51232D) else Color(0xFF123D34),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(notice, color = White, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Overview(
    tx: List<MoneyTransaction>, income: Double, expenses: Double,
    mi: Double, me: Double, onAdd: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("TOTAL BALANCE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                    Text(money(income - expenses), color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp, bottom = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric("THIS MONTH INCOME", mi, Mint, Modifier.weight(1f))
                        Metric("THIS MONTH EXPENSES", me, Red, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("All-time income", money(income), Mint, Modifier.weight(1f))
                SmallStat("All-time spending", money(expenses), Red, Modifier.weight(1f))
            }
        }
        item { Text("Cash flow", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { CashFlowChart(tx) }
        item { Text("Expense breakdown", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { SpendingChart(tx) }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add transaction", fontWeight = FontWeight.Bold)
            }
        }
        item { Text("Recent activity", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(tx.take(4)) { TransactionRow(it) }
    }
}

@Composable
private fun Metric(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel2, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = .5.sp)
        Text(money(value), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SmallStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CashFlowChart(tx: List<MoneyTransaction>) {
    val daily = (1..31).map { day ->
        tx.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            c.get(Calendar.DAY_OF_MONTH) == day && monthKeyFor(it.date) == monthKeyFor(System.currentTimeMillis())
        }.sumOf { if (it.type == "Income") it.amount else -it.amount }
    }
    val cumulative = daily.runningFold(0.0) { a, b -> a + b }.drop(1)
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Cumulative daily net", color = Muted, fontSize = 11.sp)
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
                if (cumulative.any { it != 0.0 }) {
                    val max = (cumulative.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                    val min = (cumulative.minOrNull() ?: 0.0).coerceAtMost(0.0)
                    val span = (max - min).coerceAtLeast(1.0)
                    val pts = cumulative.mapIndexed { i, v ->
                        Offset(size.width * i / (cumulative.size - 1).coerceAtLeast(1), size.height - (((v - min) / span).toFloat() * size.height))
                    }
                    for (i in 0 until pts.size - 1) drawLine(Mint, pts[i], pts[i + 1], 4f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun SpendingChart(tx: List<MoneyTransaction>) {
    val data = tx.filter { it.type == "Expense" && monthKeyFor(it.date) == monthKeyFor(System.currentTimeMillis()) }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }.take(5)
    val total = data.sumOf { it.second }
    val colors = listOf(Mint, Blue, Color(0xFFFFB454), Color(0xFFB79CFF), Red)
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (total <= 0) Text("No expenses recorded this month yet.", color = Muted, fontSize = 13.sp)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(112.dp)) {
                    var start = -90f
                    data.forEachIndexed { i, pair ->
                        val sweep = (pair.second / total * 360).toFloat()
                        drawArc(colors[i % colors.size], start, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 19.dp.toPx()))
                        start += sweep
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(colors[i % colors.size], CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text(p.first, color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(money(p.second), color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetScreen(
    plans: List<MonthlyPlan>,
    tx: List<MoneyTransaction>,
    onSave: (MonthlyPlan) -> Unit
) {
    var yearly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(monthKeyFor(System.currentTimeMillis())) }
    var incomeText by remember(selected, plans) { mutableStateOf((plans.find { it.monthKey == selected }?.incomeEstimate ?: 0.0).toString()) }
    var expenseText by remember(selected, plans) { mutableStateOf((plans.find { it.monthKey == selected }?.expenseEstimate ?: 0.0).toString()) }
    var savingsText by remember(selected, plans) { mutableStateOf((plans.find { it.monthKey == selected }?.savingsEstimate ?: 0.0).toString()) }
    val actual = tx.filter { monthKeyFor(it.date) == selected }
    val actualIncome = actual.filter { it.type == "Income" }.sumOf { it.amount }
    val actualExpense = actual.filter { it.type == "Expense" }.sumOf { it.amount }
    val selectedPlan = plans.find { it.monthKey == selected } ?: MonthlyPlan(selected)
    val year = selected.take(4).toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
    val yearMonths = (1..12).map { "%04d-%02d".format(year, it) }
    val yearTx = tx.filter { monthKeyFor(it.date).startsWith("$year-") }
    val yearPlans = yearMonths.map { key -> plans.find { it.monthKey == key } ?: MonthlyPlan(key) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!yearly, { yearly = false }, label = { Text("Monthly") })
                FilterChip(yearly, { yearly = true }, label = { Text("Yearly") })
            }
        }
        if (yearly) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { selected = "${year - 1}-01" }) { Icon(Icons.Default.ChevronLeft, null, tint = Mint) }
                    Text("$year annual overview", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { selected = "${year + 1}-01" }) { Icon(Icons.Default.ChevronRight, null, tint = Mint) }
                }
            }
            item {
                val plannedIncome = yearPlans.sumOf { it.incomeEstimate }
                val plannedExpense = yearPlans.sumOf { it.expenseEstimate }
                val plannedSavings = yearPlans.sumOf { it.savingsEstimate }
                val realIncome = yearTx.filter { it.type == "Income" }.sumOf { it.amount }
                val realExpense = yearTx.filter { it.type == "Expense" }.sumOf { it.amount }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("YEAR TOTALS", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                        SummaryLine("Planned income", plannedIncome, Mint)
                        SummaryLine("Actual income", realIncome, Mint)
                        SummaryLine("Planned expenses", plannedExpense, Red)
                        SummaryLine("Actual expenses", realExpense, Red)
                        SummaryLine("Savings targets", plannedSavings, Blue)
                    }
                }
            }
            item { Text("12 monthly plans", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            items(yearMonths) { key ->
                val p = yearPlans.first { it.monthKey == key }
                val mtx = tx.filter { monthKeyFor(it.date) == key }
                val ai = mtx.filter { it.type == "Income" }.sumOf { it.amount }
                val ae = mtx.filter { it.type == "Expense" }.sumOf { it.amount }
                Card(
                    onClick = { selected = key; yearly = false },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(monthLabel(key), color = White, fontWeight = FontWeight.Bold)
                        Text("Income ${money(ai)} / ${money(p.incomeEstimate)} planned", color = Mint, fontSize = 12.sp)
                        Text("Expenses ${money(ae)} / ${money(p.expenseEstimate)} planned", color = Red, fontSize = 12.sp)
                        Text("Savings target ${money(p.savingsEstimate)}", color = Blue, fontSize = 12.sp)
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { selected = shiftMonth(selected, -1) }) { Icon(Icons.Default.ChevronLeft, null, tint = Mint) }
                    Text(monthLabel(selected), color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { selected = shiftMonth(selected, 1) }) { Icon(Icons.Default.ChevronRight, null, tint = Mint) }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ACTUAL VS PLAN", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                        SummaryLine("Income", actualIncome, Mint)
                        SummaryLine("Expenses", actualExpense, Red)
                        SummaryLine("Net", actualIncome - actualExpense, White)
                    }
                }
            }
            item { BudgetInput("Planned income", incomeText, { incomeText = it }, Mint) }
            item { BudgetInput("Planned expenses", expenseText, { expenseText = it }, Red) }
            item { BudgetInput("Savings target", savingsText, { savingsText = it }, Blue) }
            item {
                Button(
                    onClick = {
                        onSave(selectedPlan.copy(
                            incomeEstimate = incomeText.toDoubleOrNull() ?: 0.0,
                            expenseEstimate = expenseText.toDoubleOrNull() ?: 0.0,
                            savingsEstimate = savingsText.toDoubleOrNull() ?: 0.0
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Save $selected budget") }
            }
            item { Text("Transactions this month", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (actual.isEmpty()) item { Text("No transactions recorded for this month.", color = Muted, fontSize = 12.sp) }
            items(actual) { TransactionRow(it) }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: Double, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(money(value), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun BudgetInput(label: String, value: String, onValue: (String) -> Unit, color: Color) {
    OutlinedTextField(
        value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = color, unfocusedBorderColor = Panel2,
            focusedTextColor = White, unfocusedTextColor = White,
            focusedLabelColor = color, unfocusedLabelColor = Muted
        )
    )
}

@Composable
private fun ActivityScreen(tx: List<MoneyTransaction>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add transaction")
        }
        Spacer(Modifier.height(12.dp))
        if (tx.isEmpty()) EmptyState("No transactions yet", "Add your first income or expense.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(tx, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TransactionRow(item) }
                    IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(t: MoneyTransaction) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Panel2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (t.type == "Income") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (t.type == "Income") Mint else Red)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("${t.category} · ${SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(t.date))}", color = Muted, fontSize = 10.sp)
            if (t.note.isNotBlank()) Text(t.note, color = Muted, fontSize = 10.sp)
        }
        Text((if (t.type == "Income") "+" else "−") + money(t.amount), color = if (t.type == "Income") Mint else Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun GoalsScreen(
    goals: List<SavingsGoal>, onAdd: () -> Unit,
    onUpdate: (SavingsGoal) -> Unit, onDelete: (Long) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Create savings goal")
        }
        Spacer(Modifier.height(12.dp))
        if (goals.isEmpty()) EmptyState("Start saving with purpose", "Create a goal and track your progress.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(goals, key = { it.id }) { g ->
                var add by remember(g.id) { mutableStateOf("") }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(g.name, color = White, fontWeight = FontWeight.Bold)
                            Text("${(g.saved / g.target.coerceAtLeast(1.0) * 100).toInt()}%", color = Mint)
                        }
                        LinearProgressIndicator(
                            progress = { (g.saved / g.target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(), color = Mint, trackColor = Panel2
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(money(g.saved), color = Mint); Text("Target ${money(g.target)}", color = Muted, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(add, { add = it }, modifier = Modifier.weight(1f), label = { Text("Add saved amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            TextButton(onClick = {
                                val n = add.toDoubleOrNull()
                                if (n != null && n > 0) { onUpdate(g.copy(saved = g.saved + n)); add = "" }
                            }) { Text("Add") }
                            IconButton(onClick = { onDelete(g.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(categories: List<MoneyCategory>, onAdd: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text("EXPENSE CATEGORIES", color = Muted, fontSize = 11.sp, letterSpacing = 1.3.sp)
                    categories.filter { it.type == "Expense" }.forEach {
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.name, color = White); Text("Expense", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text("INCOME CATEGORIES", color = Muted, fontSize = 11.sp, letterSpacing = 1.3.sp)
                    categories.filter { it.type == "Income" }.forEach {
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.name, color = White); Text("Income", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Add custom category")
                    }
                }
            }
        }
        item {
            Text("Your information is stored locally on this device. Uninstalling the app or clearing app storage can delete it.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inbox, null, tint = Muted, modifier = Modifier.size(38.dp))
        Text(title, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(sub, color = Muted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDialog(
    categories: List<MoneyCategory>,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Expense") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val filtered = categories.filter { it.type == type }
    LaunchedEffect(type, filtered) {
        if (filtered.none { it.name == category }) category = filtered.firstOrNull()?.name ?: ""
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("New transaction", color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (ZAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "Expense", { type = "Expense" }, label = { Text("Expense") })
                    FilterChip(type == "Income", { type = "Income" }, label = { Text("Income") })
                }
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded, { expanded = it }) {
                    OutlinedTextField(
                        category, {}, readOnly = true, label = { Text("$type category") },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        filtered.forEach { item ->
                            DropdownMenuItem(text = { Text(item.name) }, onClick = { category = item.name; expanded = false })
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = amount.toDoubleOrNull()
                if (title.isNotBlank() && n != null && n > 0 && category.isNotBlank()) onSave(title.trim(), n, type, category, note.trim())
            }) { Text("Save", color = Mint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun CategoryDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Expense") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("New category", color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == "Expense", { type = "Expense" }, label = { Text("Expense") })
                    FilterChip(type == "Income", { type = "Income" }, label = { Text("Income") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), type) }) { Text("Add", color = Mint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun GoalDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("New savings goal", color = White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Goal name") })
                OutlinedTextField(target, { target = it }, label = { Text("Target amount (ZAR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = target.toDoubleOrNull()
                if (name.isNotBlank() && n != null && n > 0) onSave(name.trim(), n)
            }) { Text("Create", color = Mint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}
