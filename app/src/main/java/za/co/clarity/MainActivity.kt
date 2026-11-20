package za.co.clarity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
private fun money(v: Double): String = "R" + NumberFormat.getNumberInstance(Locale("en","ZA")).apply { maximumFractionDigits=2; minimumFractionDigits=2 }.format(v)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = ClarityDatabase.get(this).dao()
        lifecycleScope.launch {
            listOf("Food","Transport","Housing","Bills","Shopping","Health","Entertainment","Salary","Other").forEach {
                dao.addCategory(MoneyCategory(it, if (it=="Salary") "Income" else "Expense"))
            }
        }
        setContent {
            val tx by dao.transactions().collectAsState(initial = emptyList())
            val categories by dao.categories().collectAsState(initial = emptyList())
            val goals by dao.goals().collectAsState(initial = emptyList())
            ClarityApp(tx, categories, goals,
                addTx = { dao.addTransaction(it) },
                deleteTx = { dao.deleteTransaction(it) },
                addCat = { dao.addCategory(MoneyCategory(it)) },
                addGoal = { dao.addGoal(it) },
                updateGoal = { id, amount -> dao.updateGoal(id, amount) },
                deleteGoal = { dao.deleteGoal(it) },
                loadPlan = { dao.plan(monthKey()) },
                savePlan = { dao.savePlan(it) }
            )
        }
    }
}

private fun monthKey() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

@Composable
private fun ClarityApp(
    tx: List<MoneyTransaction>, categories: List<MoneyCategory>, goals: List<SavingsGoal>,
    addTx: suspend (MoneyTransaction)->Unit, deleteTx: suspend (Long)->Unit,
    addCat: suspend (String)->Unit, addGoal: suspend (SavingsGoal)->Unit,
    updateGoal: suspend (Long,Double)->Unit, deleteGoal: suspend (Long)->Unit,
    loadPlan: suspend ()->MonthlyPlan?, savePlan: suspend (MonthlyPlan)->Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf(MonthlyPlan(monthKey())) }
    LaunchedEffect(Unit) { plan = loadPlan() ?: MonthlyPlan(monthKey()) }
    val income = tx.filter { it.type=="Income" }.sumOf { it.amount }
    val expenses = tx.filter { it.type=="Expense" }.sumOf { it.amount }
    val balance = income-expenses
    val thisMonthTx = tx.filter { monthKeyFor(it.date)==monthKey() }
    val monthIncome = thisMonthTx.filter { it.type=="Income" }.sumOf { it.amount }
    val monthExpense = thisMonthTx.filter { it.type=="Expense" }.sumOf { it.amount }

    MaterialTheme(colorScheme = darkColorScheme(
        background=Ink, surface=Panel, primary=Mint, onPrimary=Ink,
        onBackground=White, onSurface=White, secondary=Color(0xFF62A8FF)
    )) {
        Scaffold(containerColor=Ink, bottomBar = {
            NavigationBar(containerColor=Panel, contentColor=White) {
                listOf("Home","Plan","Activity","Goals","More").forEachIndexed { i, label ->
                    NavigationBarItem(selected=tab==i, onClick={tab=i},
                        icon={ Icon(when(i){0->Icons.Default.Dashboard;1->Icons.Default.Tune;2->Icons.Default.ReceiptLong;3->Icons.Default.Savings;else->Icons.Default.Settings}, null) },
                        label={ Text(label, fontSize=10.sp) }, colors=NavigationBarItemDefaults.colors(selectedIconColor=Mint, selectedTextColor=Mint, indicatorColor=Panel2, unselectedIconColor=Muted, unselectedTextColor=Muted))
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=18.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    Column {
                        Text("CLARITY", color=Mint, fontSize=12.sp, fontWeight=FontWeight.Bold, letterSpacing=3.sp)
                        Text(when(tab){0->"Your money, in focus";1->"Monthly plan";2->"Transactions";3->"Savings goals";else->"Manage your money"}, color=White, fontSize=22.sp, fontWeight=FontWeight.Bold)
                    }
                    Box(Modifier.size(42.dp).background(Panel2,CircleShape), contentAlignment=Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet,null,tint=Mint) }
                }
                when(tab) {
                    0 -> Overview(tx, income, expenses, balance, monthIncome, monthExpense, onAdd={dialog="transaction"})
                    1 -> PlanScreen(plan, monthIncome, monthExpense, onChange={plan=it;scope.launch{savePlan(it)}})
                    2 -> ActivityScreen(tx, onAdd={dialog="transaction"}, onDelete={scope.launch{deleteTx(it)}})
                    3 -> GoalsScreen(goals, onAdd={dialog="goal"}, onUpdate={g -> scope.launch{updateGoal(g.id,g.saved)}} , onDelete={scope.launch{deleteGoal(it)}})
                    else -> MoreScreen(categories, onAdd={dialog="category"})
                }
            }
        }
        if(dialog=="transaction") TransactionDialog(categories, onDismiss={dialog=""}, onSave={ title,amount,type,cat,note ->
            scope.launch { addTx(MoneyTransaction(title=title,amount=amount,type=type,category=cat,note=note)) }; dialog=""
        })
        if(dialog=="category") SimpleEntryDialog("New category","Category name", {dialog=""}) { scope.launch{addCat(it)};dialog="" }
        if(dialog=="goal") GoalDialog({dialog=""}) { name,target -> scope.launch{addGoal(SavingsGoal(name=name,target=target))};dialog="" }
    }
}

private fun monthKeyFor(time: Long)=SimpleDateFormat("yyyy-MM",Locale.US).format(Date(time))

@Composable private fun Overview(tx:List<MoneyTransaction>, income:Double, expenses:Double, balance:Double, mi:Double, me:Double, onAdd:()->Unit) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp), contentPadding=PaddingValues(bottom=18.dp)) {
        item {
            Card(colors=CardDefaults.cardColors(containerColor=Panel), shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("TOTAL BALANCE",color=Muted,fontSize=11.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp)
                    Text(money(balance),color=White,fontSize=32.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=7.dp,bottom=16.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Metric("INCOME",mi,Mint,Modifier.weight(1f))
                        Metric("EXPENSES",me,Red,Modifier.weight(1f))
                    }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            SmallStat("All-time income",money(income),Mint,Modifier.weight(1f))
            SmallStat("All-time spending",money(expenses),Red,Modifier.weight(1f))
        } }
        item { SectionTitle("Cash flow","This month") }
        item { CashFlowChart(tx) }
        item { SectionTitle("Spending breakdown","By category") }
        item { SpendingChart(tx) }
        item { Button(onClick=onAdd, modifier=Modifier.fillMaxWidth().height(54.dp), shape=RoundedCornerShape(15.dp)) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp)); Text("Add transaction",fontWeight=FontWeight.Bold) } }
        item { Text("Recent activity",color=White,fontSize=18.sp,fontWeight=FontWeight.Bold) }
        items(tx.take(4)) { TransactionRow(it) }
    }
}

@Composable private fun Metric(label:String,value:Double,color:Color,modifier:Modifier=Modifier) {
    Column(modifier.background(Panel2,RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label,color=Muted,fontSize=10.sp,letterSpacing=1.sp)
        Text(money(value),color=color,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp))
    }
}
@Composable private fun SmallStat(label:String,value:String,color:Color,modifier:Modifier) {
    Column(modifier.background(Panel,RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(label,color=Muted,fontSize=11.sp); Text(value,color=color,fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.padding(top=6.dp))
    }
}
@Composable private fun SectionTitle(title:String,sub:String) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,color=White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(sub,color=Muted,fontSize=11.sp)} }

@Composable private fun CashFlowChart(tx:List<MoneyTransaction>) {
    val days = (1..30).map { d -> tx.filter { val c=Calendar.getInstance().apply{timeInMillis=it.date}; c.get(Calendar.DAY_OF_MONTH)==d && monthKeyFor(it.date)==monthKey() }.sumOf { if(it.type=="Income") it.amount else -it.amount } }
    val cumulative=days.runningFold(0.0){a,b->a+b}.drop(1)
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Daily net cash flow",color=Muted,fontSize=11.sp)
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top=12.dp)) {
                if(cumulative.isNotEmpty()) {
                    val max=(cumulative.maxOrNull()?:1.0).coerceAtLeast(1.0); val min=(cumulative.minOrNull()?:0.0).coerceAtMost(0.0)
                    val span=(max-min).coerceAtLeast(1.0)
                    val pts=cumulative.mapIndexed { i,v -> Offset(if(cumulative.size==1) 0f else size.width*i/(cumulative.size-1), size.height-( ((v-min)/span).toFloat()*size.height)) }
                    for(i in 0 until pts.size-1) drawLine(Mint,pts[i],pts[i+1],4f,cap=StrokeCap.Round)
                    pts.forEach { drawCircle(Mint,3f,it) }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("1",color=Muted,fontSize=10.sp);Text("15",color=Muted,fontSize=10.sp);Text("30",color=Muted,fontSize=10.sp)}
        }
    }
}
@Composable private fun SpendingChart(tx:List<MoneyTransaction>) {
    val data=tx.filter{it.type=="Expense" && monthKeyFor(it.date)==monthKey()}.groupBy{it.category}.mapValues{e->e.value.sumOf{it.amount}}.toList().sortedByDescending{it.second}.take(5)
    val total=data.sumOf{it.second}
    val colors=listOf(Mint,Color(0xFF62A8FF),Color(0xFFFFB454),Color(0xFFB79CFF),Red)
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if(total<=0) Text("No expenses recorded this month yet.",color=Muted,fontSize=13.sp)
            else {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Canvas(Modifier.size(116.dp)) {
                        var start=-90f
                        data.forEachIndexed { i,pair -> val sweep=(pair.second/total*360).toFloat(); drawArc(colors[i%colors.size],start,sweep,false,style=androidx.compose.ui.graphics.drawscope.Stroke(width=19.dp.toPx())); start+=sweep }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        data.forEachIndexed { i,p -> Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(colors[i%colors.size],CircleShape)); Spacer(Modifier.width(7.dp))
                            Text(p.first,color=White,fontSize=12.sp,modifier=Modifier.weight(1f)); Text(money(p.second),color=Muted,fontSize=11.sp)
                        } }
                    }
                }
            }
        }
    }
}

@Composable private fun PlanScreen(plan:MonthlyPlan, income:Double, expense:Double, onChange:(MonthlyPlan)->Unit) {
    var inc by remember(plan.monthKey){mutableStateOf(plan.incomeEstimate.toString())}
    var exp by remember(plan.monthKey){mutableStateOf(plan.expenseEstimate.toString())}
    var sav by remember(plan.monthKey){mutableStateOf(plan.savingsEstimate.toString())}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item { Text("Set your targets for ${SimpleDateFormat("MMMM yyyy",Locale.US).format(Date())}. Actuals update automatically from transactions.",color=Muted,fontSize=13.sp) }
        item { PlanInput("Estimated income",inc,{inc=it},Mint) }
        item { PlanInput("Estimated expenses",exp,{exp=it},Red) }
        item { PlanInput("Savings target",sav,{sav=it},Color(0xFF62A8FF)) }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("ACTUAL PERFORMANCE",color=Muted,fontSize=11.sp,letterSpacing=1.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Income",color=Muted);Text(money(income),color=Mint,fontWeight=FontWeight.Bold)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Expenses",color=Muted);Text(money(expense),color=Red,fontWeight=FontWeight.Bold)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Net",color=Muted);Text(money(income-expense),color=White,fontWeight=FontWeight.Bold)}
        }}}
        item { Button(onClick={onChange(plan.copy(incomeEstimate=inc.toDoubleOrNull()?:0.0,expenseEstimate=exp.toDoubleOrNull()?:0.0,savingsEstimate=sav.toDoubleOrNull()?:0.0))},modifier=Modifier.fillMaxWidth().height(52.dp)){Text("Save monthly plan")} }
    }
}
@Composable private fun PlanInput(label:String,value:String,onValue:(String)->Unit,color:Color) {
    OutlinedTextField(value,onValue,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=color,unfocusedBorderColor=Panel2,focusedTextColor=White,unfocusedTextColor=White,focusedLabelColor=color,unfocusedLabelColor=Muted))
}

@Composable private fun ActivityScreen(tx:List<MoneyTransaction>,onAdd:()->Unit,onDelete:(Long)->Unit) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Add transaction")}
        Spacer(Modifier.height(12.dp))
        if(tx.isEmpty()) EmptyState("No transactions yet","Add your first income or expense.")
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            items(tx,key={it.id}) { item -> Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)){TransactionRow(item)}
                IconButton(onClick={onDelete(item.id)}){Icon(Icons.Default.DeleteOutline,null,tint=Muted)}
            } }
        }
    }
}
@Composable private fun TransactionRow(t:MoneyTransaction) {
    Row(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(14.dp)).padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Panel2,CircleShape),contentAlignment=Alignment.Center){Icon(if(t.type=="Income") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,null,tint=if(t.type=="Income")Mint else Red)}
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title,color=White,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            Text("${t.category} · ${SimpleDateFormat("d MMM",Locale.US).format(Date(t.date))}",color=Muted,fontSize=10.sp)
        }
        Text((if(t.type=="Income")"+" else "−")+money(t.amount),color=if(t.type=="Income")Mint else Red,fontWeight=FontWeight.Bold,fontSize=12.sp)
    }
}

@Composable private fun GoalsScreen(goals:List<SavingsGoal>,onAdd:()->Unit,onUpdate:(SavingsGoal)->Unit,onDelete:(Long)->Unit) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().height(50.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Create savings goal")}
        Spacer(Modifier.height(12.dp))
        if(goals.isEmpty()) EmptyState("Start saving with purpose","Create a goal and track your progress.")
        else LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            items(goals,key={it.id}) { g ->
                var add by remember(g.id){mutableStateOf("")}
                Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){Text(g.name,color=White,fontWeight=FontWeight.Bold);Text("${(g.saved/g.target.coerceAtLeast(1.0)*100).toInt()}%",color=Mint)}
                        LinearProgressIndicator(progress={(g.saved/g.target.coerceAtLeast(1.0)).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Mint,trackColor=Panel2)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(money(g.saved),color=Mint);Text("Target ${money(g.target)}",color=Muted,fontSize=12.sp)}
                        Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(add,{add=it},modifier=Modifier.weight(1f),label={Text("Add saved amount")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));TextButton(onClick={val n=add.toDoubleOrNull();if(n!=null&&n>0){onUpdate(g.copy(saved=g.saved+n));add=""}}){Text("Add")};IconButton(onClick={onDelete(g.id)} ){Icon(Icons.Default.DeleteOutline,null,tint=Muted)}}
                    }
                }
            }
        }
    }
}
@Composable private fun MoreScreen(categories:List<MoneyCategory>,onAdd:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(17.dp)){
            Text("CATEGORIES",color=Muted,fontSize=11.sp,letterSpacing=1.3.sp)
            Spacer(Modifier.height(10.dp))
            categories.forEach { Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(it.name,color=White);Text(it.type,color=Muted,fontSize=11.sp)} }
            Spacer(Modifier.height(8.dp));Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(5.dp));Text("Add custom category")}
        }}
        Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(17.dp)){
            Text("YOUR DATA",color=Muted,fontSize=11.sp,letterSpacing=1.3.sp)
            Text("Your budget is stored locally on this device using an SQLite database. It remains available when you close and reopen the app.",color=White,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
            Text("Back up your device regularly. Uninstalling the app or clearing its storage can remove local data.",color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
        }}
    }
}

@Composable private fun EmptyState(title:String,sub:String) { Column(Modifier.fillMaxWidth().padding(35.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Inbox,null,tint=Muted,modifier=Modifier.size(38.dp));Text(title,color=White,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp));Text(sub,color=Muted,fontSize=12.sp)} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TransactionDialog(categories:List<MoneyCategory>,onDismiss:()->Unit,onSave:(String,Double,String,String,String)->Unit) {
    var title by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var type by remember{mutableStateOf("Expense")};var category by remember{mutableStateOf("Other")};var note by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text("New transaction",color=White)},text={
        Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title,{title=it},label={Text("Description")},singleLine=true)
            OutlinedTextField(amount,{amount=it},label={Text("Amount (ZAR)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(type=="Expense",{type="Expense"},label={Text("Expense")});FilterChip(type=="Income",{type="Income"},label={Text("Income")})}
            var expanded by remember{mutableStateOf(false)}
            ExposedDropdownMenuBox(expanded,{expanded=it}) {
                OutlinedTextField(category,{category=it},readOnly=true,label={Text("Category")},modifier=Modifier.menuAnchor().fillMaxWidth(),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
                ExposedDropdownMenu(expanded,{expanded=false}) { categories.forEach{DropdownMenuItem(text={Text(it.name)},onClick={category=it.name;expanded=false})} }
            }
            OutlinedTextField(note,{note=it},label={Text("Note (optional)")},singleLine=true)
        }
    },confirmButton={TextButton(onClick={val n=amount.toDoubleOrNull();if(title.isNotBlank()&&n!=null&&n>0)onSave(title,n,type,category,note)}){Text("Save",color=Mint)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel",color=Muted)}})
}
@Composable private fun SimpleEntryDialog(title:String,label:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    var value by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text(title,color=White)},text={OutlinedTextField(value,{value=it},label={Text(label)})},confirmButton={TextButton(onClick={if(value.isNotBlank())onSave(value.trim())}){Text("Add",color=Mint)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel",color=Muted)}})
}
@Composable private fun GoalDialog(onDismiss:()->Unit,onSave:(String,Double)->Unit) {
    var name by remember{mutableStateOf("")};var target by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Panel,title={Text("New savings goal",color=White)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Goal name")});OutlinedTextField(target,{target=it},label={Text("Target amount (ZAR)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))}},confirmButton={TextButton(onClick={val n=target.toDoubleOrNull();if(name.isNotBlank()&&n!=null&&n>0)onSave(name,n)}){Text("Create",color=Mint)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel",color=Muted)}})
}
