package com.example.spendtracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendtracker.domain.parser.CurrencyParser
import com.example.spendtracker.ui.home.HomeViewModel
import com.example.spendtracker.ui.settings.SettingsViewModel
import com.example.spendtracker.domain.model.Transaction
import java.util.Calendar

private enum class Page { HOME, EARNINGS, HISTORY, SETTINGS, RECYCLE_BIN }

@Composable
fun SpendTrackerApp(notificationAccessEnabled: Boolean, openNotificationSettings: () -> Unit) {
    var onboardingDismissed by rememberSaveable { mutableStateOf(false) }
    if (!notificationAccessEnabled && !onboardingDismissed) {
        OnboardingScreen(openNotificationSettings) { onboardingDismissed = true }
        return
    }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    BackHandler(enabled = page != Page.HOME) { page = Page.HOME }
    when (page) {
        Page.HOME -> HomeScreen(notificationAccessEnabled, { page = Page.SETTINGS }, openNotificationSettings, { page = Page.EARNINGS }, { page = Page.HISTORY })
        Page.EARNINGS -> EarningsScreen({ page = Page.HOME }, { page = Page.HISTORY }, { page = Page.SETTINGS })
        Page.HISTORY -> HistoryScreen({ page = Page.HOME }, { page = Page.EARNINGS }, { page = Page.SETTINGS })
        Page.SETTINGS -> SettingsScreen(
            accessEnabled = notificationAccessEnabled,
            openNotificationSettings = openNotificationSettings,
            openRecycleBin = { page = Page.RECYCLE_BIN },
            goBack = { page = Page.HOME }
        )
        Page.RECYCLE_BIN -> RecycleBinScreen(goBack = { page = Page.SETTINGS })
    }
}

@Composable
private fun OnboardingScreen(openSettings: () -> Unit, continueWithout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Expense Tracker", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("Enable notification access", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Android needs this access so Expense Tracker can recognise CommBank purchase notifications. Processing stays on this device: no notification text is uploaded, no banking credentials are requested, and the app cannot access your bank account.")
        Spacer(Modifier.height(12.dp))
        Text("CommBank transaction notifications must also be enabled. Unrelated apps are ignored.")
        Spacer(Modifier.height(24.dp))
        Button(openSettings, Modifier.fillMaxWidth()) { Text("Open notification access settings") }
        TextButton(continueWithout, Modifier.align(Alignment.CenterHorizontally)) { Text("Continue with manual entry") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(accessEnabled: Boolean, openSettingsPage: () -> Unit, openNotificationSettings: () -> Unit, openEarnings: () -> Unit, openHistory: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val selectedSplit by vm.selectedSplit.collectAsStateWithLifecycle()
    var showReset by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCombinedSplit by remember { mutableStateOf(false) }
    val selectedTransactions = snapshot.transactions.filter { it.id in selectedIds }
    BackHandler(enabled = selecting) { selecting = false; selectedIds = emptySet() }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(if (selecting) "${selectedIds.size} selected" else "Expense Tracker", fontWeight = FontWeight.Bold) },
            actions = {
                if (selecting) TextButton({ selecting = false; selectedIds = emptySet() }) { Text("Cancel") }
                else IconButton(openSettingsPage) { Icon(Icons.Default.Settings, "Settings") }
            }
        ) },
        bottomBar = { MainBottomBar(Page.HOME, {}, openEarnings, openHistory) },
        floatingActionButton = { if (!selecting) ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add Transaction") }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!accessEnabled) item { AssistChip(onClick = openNotificationSettings, label = { Text("Notification access is off — tap to enable") }) }
            item { Text(if (snapshot.periodStartedAt > 0) "Current period since ${formatAustralianDate(snapshot.periodStartedAt)}" else "Starting current spending period…", style = MaterialTheme.typography.bodyMedium) }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Spent since last payday")
                        Text(formatAud(snapshot.totalCents), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        if (snapshot.reimbursementsCents > 0) Text("${formatAud(snapshot.grossCents)} purchases − ${formatAud(snapshot.reimbursementsCents)} split repayments")
                    }
                }
            }
            item { Button({ showReset = true }, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset") } }
            item { Text("Recent transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item { Text("Press and hold a transaction to select, delete, or Sum & Split.", style = MaterialTheme.typography.bodySmall) }
            if (selecting) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ confirmDelete = true }, Modifier.weight(1f), enabled = selectedIds.isNotEmpty()) { Text("Delete") }
                    Button({ showCombinedSplit = true }, Modifier.weight(1f), enabled = selectedTransactions.size >= 2 && selectedTransactions.none { it.hasSplit }) { Text("Sum & Split") }
                }
                if (selectedTransactions.any { it.hasSplit }) Text("Transactions already belonging to a split cannot be included in another split.", style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot.transactions.isEmpty()) item { Text("No transactions in this spending period yet.", modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center) }
            items(snapshot.transactions, key = { it.id }) { transaction ->
                ListItem(
                    modifier = Modifier.combinedClickable(
                        onLongClick = { selecting = true; selectedIds = selectedIds + transaction.id },
                        onClick = {
                            if (selecting) selectedIds = if (transaction.id in selectedIds) selectedIds - transaction.id else selectedIds + transaction.id
                            else { selectedTransaction = transaction; vm.selectTransaction(transaction.id) }
                        }
                    ),
                    headlineContent = { Text(transaction.merchant, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column {
                            Text(formatAustralianDate(transaction.timestamp))
                            if (transaction.hasSplit) Text(
                                when {
                                    transaction.splitClosed -> "Split closed ✓"
                                    transaction.splitCompleted -> "Split completed ✓"
                                    else -> "Split: ${transaction.splitPaidCount} / ${transaction.splitParticipantCount} paid"
                                },
                                color = if (transaction.splitCompleted) androidx.compose.ui.graphics.Color(0xFF177245) else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingContent = {
                        if (selecting) Checkbox(transaction.id in selectedIds, { checked -> selectedIds = if (checked) selectedIds + transaction.id else selectedIds - transaction.id })
                        else Text(formatAud(transaction.amountCents), fontWeight = FontWeight.Bold)
                    }
                    ,colors = ListItemDefaults.colors(containerColor = when {
                        transaction.splitCompleted -> androidx.compose.ui.graphics.Color(0xFFE1F4E6)
                        transaction.hasSplit -> androidx.compose.ui.graphics.Color(0xFFFFE7E7)
                        else -> MaterialTheme.colorScheme.surface
                    })
                )
                HorizontalDivider()
            }
        }
    }
    if (showReset) ResetOptionsDialog(
        onDismiss = { showReset = false },
        resetSpending = { vm.reset(); showReset = false },
        resetIncome = { vm.resetIncome(); showReset = false },
        resetBoth = { vm.resetBoth(); showReset = false }
    )
    if (showAdd) AddTransactionDialog(onDismiss = { showAdd = false }) { merchant, cents, timestamp -> vm.addManual(merchant, cents, timestamp); showAdd = false }
    selectedTransaction?.let { transaction ->
        TransactionBillSplitDialog(
            transaction = transaction,
            state = selectedSplit,
            defaultAccountName = vm.savedAccountName,
            defaultPayId = vm.savedPayId,
            onDismiss = { selectedTransaction = null; vm.selectTransaction(null) },
            onCreate = { title, names, accountName, payId -> vm.createSplit(transaction, title, names, accountName, payId) },
            onAssign = vm::assignPayment,
            onMarkPaid = vm::markParticipantPaid,
            onUndoPaid = vm::undoParticipantPaid,
            onCloseSplit = vm::closeSplit,
            onReopenSplit = vm::reopenSplit
        )
    }
    if (confirmDelete) DeleteTransactionsDialog(selectedIds.size, { confirmDelete = false }) {
        vm.deleteTransactions(selectedIds); selectedIds = emptySet(); selecting = false; confirmDelete = false
    }
    if (showCombinedSplit) CreateCombinedSplitDialog(selectedTransactions, vm.savedAccountName, vm.savedPayId, { showCombinedSplit = false }) { title, names, account, payId ->
        vm.createCombinedSplit(selectedTransactions, title, names, account, payId, true)
        selectedIds = emptySet(); selecting = false; showCombinedSplit = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarningsScreen(openSpending: () -> Unit, openHistory: () -> Unit, openSettingsPage: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val income by vm.incomeSnapshot.collectAsStateWithLifecycle()
    var showReset by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Earnings", fontWeight = FontWeight.Bold) }, actions = { IconButton(openSettingsPage) { Icon(Icons.Default.Settings, "Settings") } }) },
        bottomBar = { MainBottomBar(Page.EARNINGS, openSpending, {}, openHistory) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (income.periodStartedAt > 0) "Income period since ${formatAustralianDate(income.periodStartedAt)}" else "Starting current income period…") }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE1F4E6))) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Income received")
                        Text(formatAud(income.totalCents), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Button({ showReset = true }, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset") } }
            item { Text("Recent earnings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (income.earnings.isEmpty()) item { Text("No unmatched incoming payments in this income period.", Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center) }
            items(income.earnings, key = { it.id }) { payment ->
                ListItem(
                    headlineContent = { Text(payment.senderName ?: "Payment received", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(formatAustralianDate(payment.receivedAt)) },
                    trailingContent = { Text(formatAud(payment.amountCents), color = androidx.compose.ui.graphics.Color(0xFF177245), fontWeight = FontWeight.Bold) }
                )
                HorizontalDivider()
            }
        }
    }
    if (showReset) ResetOptionsDialog(
        onDismiss = { showReset = false },
        resetSpending = { vm.reset(); showReset = false },
        resetIncome = { vm.resetIncome(); showReset = false },
        resetBoth = { vm.resetBoth(); showReset = false }
    )
}

@Composable
private fun MainBottomBar(selectedPage: Page, openSpending: () -> Unit, openEarnings: () -> Unit, openHistory: () -> Unit) {
    NavigationBar {
        NavigationBarItem(selected = selectedPage == Page.HOME, onClick = openSpending, icon = { Text("$") }, label = { Text("Spending") })
        NavigationBarItem(selected = selectedPage == Page.EARNINGS, onClick = openEarnings, icon = { Text("+") }, label = { Text("Earnings") })
        NavigationBarItem(selected = selectedPage == Page.HISTORY, onClick = openHistory, icon = { Text("≡") }, label = { Text("History") })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(openSpending: () -> Unit, openEarnings: () -> Unit, openSettingsPage: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val transactions by vm.allTransactions.collectAsStateWithLifecycle()
    val selectedSplit by vm.selectedSplit.collectAsStateWithLifecycle()
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCombinedSplit by remember { mutableStateOf(false) }
    val selectedTransactions = transactions.filter { it.id in selectedIds }
    BackHandler(enabled = selecting) { selecting = false; selectedIds = emptySet() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${selectedIds.size} selected" else "All History", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selecting) TextButton({ selecting = false; selectedIds = emptySet() }) { Text("Cancel") }
                    else IconButton(openSettingsPage) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        bottomBar = { MainBottomBar(Page.HISTORY, openSpending, openEarnings, {}) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text("Includes transactions from previous reset periods and CSV imports.", Modifier.padding(bottom = 12.dp)) }
            item { Text("Press and hold a transaction to select, delete, or Sum & Split.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
            if (selecting) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ confirmDelete = true }, Modifier.weight(1f), enabled = selectedIds.isNotEmpty()) { Text("Delete") }
                    Button(
                        { showCombinedSplit = true },
                        Modifier.weight(1f),
                        enabled = selectedTransactions.size >= 2 && selectedTransactions.none { it.hasSplit }
                    ) { Text("Sum & Split") }
                }
                if (selectedTransactions.any { it.hasSplit }) Text("Transactions already belonging to a split cannot be included in another split.", style = MaterialTheme.typography.bodySmall)
            }
            if (transactions.isEmpty()) item { Text("No transaction history yet.", Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center) }
            items(transactions, key = { it.id }) { transaction ->
                ListItem(
                    modifier = Modifier.combinedClickable(
                        onLongClick = { selecting = true; selectedIds = selectedIds + transaction.id },
                        onClick = {
                            if (selecting) selectedIds = if (transaction.id in selectedIds) selectedIds - transaction.id else selectedIds + transaction.id
                            else { selectedTransaction = transaction; vm.selectTransaction(transaction.id) }
                        }
                    ),
                    headlineContent = { Text(transaction.merchant, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column {
                            Text(formatAustralianDate(transaction.timestamp))
                            Text(if (transaction.source == "CSV_IMPORT") "Imported from CSV" else transaction.source.replace('_', ' ').lowercase())
                        }
                    },
                    trailingContent = {
                        if (selecting) Checkbox(transaction.id in selectedIds, { checked ->
                            selectedIds = if (checked) selectedIds + transaction.id else selectedIds - transaction.id
                        }) else Text(formatAud(transaction.amountCents), fontWeight = FontWeight.Bold)
                    },
                    colors = ListItemDefaults.colors(containerColor = when {
                        transaction.splitCompleted -> androidx.compose.ui.graphics.Color(0xFFE1F4E6)
                        transaction.hasSplit -> androidx.compose.ui.graphics.Color(0xFFFFE7E7)
                        else -> MaterialTheme.colorScheme.surface
                    })
                )
                HorizontalDivider()
            }
        }
    }
    selectedTransaction?.let { transaction ->
        TransactionBillSplitDialog(
            transaction = transaction,
            state = selectedSplit,
            defaultAccountName = vm.savedAccountName,
            defaultPayId = vm.savedPayId,
            onDismiss = { selectedTransaction = null; vm.selectTransaction(null) },
            onCreate = { title, names, accountName, payId -> vm.createSplit(transaction, title, names, accountName, payId) },
            onAssign = vm::assignPayment,
            onMarkPaid = vm::markParticipantPaid,
            onUndoPaid = vm::undoParticipantPaid,
            onCloseSplit = vm::closeSplit,
            onReopenSplit = vm::reopenSplit
        )
    }
    if (confirmDelete) DeleteTransactionsDialog(selectedIds.size, { confirmDelete = false }) {
        vm.deleteTransactions(selectedIds); selectedIds = emptySet(); selecting = false; confirmDelete = false
    }
    if (showCombinedSplit) CreateCombinedSplitDialog(
        transactions = selectedTransactions,
        defaultAccountName = vm.savedAccountName,
        defaultPayId = vm.savedPayId,
        onDismiss = { showCombinedSplit = false },
        onCreate = { title, names, account, payId ->
            vm.createCombinedSplit(selectedTransactions, title, names, account, payId, false)
            selectedIds = emptySet()
            selecting = false
            showCombinedSplit = false
        }
    )
}

@Composable
private fun DeleteTransactionsDialog(count: Int, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $count transaction${if (count == 1) "" else "s"}?") },
        text = { Text("The selected transactions will move to the Recycle Bin. You can restore them later from Settings.") },
        confirmButton = { Button(onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Move to Bin") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ResetOptionsDialog(onDismiss: () -> Unit, resetSpending: () -> Unit, resetIncome: () -> Unit, resetBoth: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What would you like to reset?") },
        text = { Text("A new period will start now. Existing spending and income records remain in local history.") },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(resetSpending, Modifier.fillMaxWidth()) { Text("Reset Spending") }
                Button(resetIncome, Modifier.fillMaxWidth()) { Text("Reset Income") }
                Button(resetBoth, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Reset Both") }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddTransactionDialog(onDismiss: () -> Unit, onAdd: (String, Long, Long) -> Unit) {
    val context = LocalContext.current
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cents = CurrencyParser.parseCents(amount)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add transaction") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (AUD)") }, prefix = { Text("$") }, singleLine = true)
            OutlinedButton({ pickDateTime(context, timestamp) { timestamp = it } }, Modifier.fillMaxWidth()) { Text(formatAustralianDate(timestamp)) }
        }
    }, confirmButton = { Button({ onAdd(merchant.trim(), cents!!, timestamp) }, enabled = merchant.isNotBlank() && cents != null) { Text("Add") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

private fun pickDateTime(context: Context, initial: Long, result: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            calendar.set(year, month, day, hour, minute, 0); result(calendar.timeInMillis)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(accessEnabled: Boolean, openNotificationSettings: () -> Unit, openRecycleBin: () -> Unit, goBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var packageName by remember { mutableStateOf(vm.initialPackage) }
    var currency by remember { mutableStateOf(vm.initialCurrency) }
    var accountName by remember { mutableStateOf(vm.initialAccountName) }
    var payId by remember { mutableStateOf(vm.initialPayId) }
    var sample by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(pendingCsv) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val csv = runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() } }.getOrNull()
            if (csv == null) importResult = "Could not read the selected file."
            else vm.importCsv(csv) { result -> importResult = result }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { TextButton(goBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Notification access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (accessEnabled) "Enabled" else "Not enabled")
                OutlinedButton(openNotificationSettings) { Text("Open Android settings") }
            }
            item {
                Text("CommBank configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(packageName, { packageName = it }, label = { Text("Verified CommBank package name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(currency, { currency = it }, label = { Text("Currency") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
            }
            item {
                Text("Default payment details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("These details prefill new bill splits. They are stored only on this device and included in shared text/images only after your confirmation.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(accountName, { accountName = it }, label = { Text("My account name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(payId, { payId = it }, label = { Text("My PayID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button({ vm.save(packageName, currency, accountName, payId) }, enabled = packageName.isNotBlank() && currency.isNotBlank()) { Text("Save settings") }
            }
            item {
                Text("Test notification parser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(sample, { sample = it }, label = { Text("Paste sample notification text") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Spacer(Modifier.height(8.dp))
                OutlinedButton({
                    testResult = vm.test(sample)?.let {
                        "Type: ${it.type}\n${it.nameLabel}: ${it.name}\nAmount: ${formatAud(it.amountCents)}"
                    } ?: "No supported purchase or incoming payment found"
                }) { Text("Preview result") }
                testResult?.let { Text(it, Modifier.padding(top = 8.dp)) }
            }
            item {
                Text("Your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.export { csv -> pendingCsv = csv; exportLauncher.launch("expense-tracker.csv") } }, Modifier.weight(1f)) { Text("Export CSV") }
                    OutlinedButton({ importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) }, Modifier.weight(1f)) { Text("Import CSV") }
                }
                importResult?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(openRecycleBin, Modifier.fillMaxWidth()) { Text("Recycle Bin") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton({ confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete all local data") }
            }
            item {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Expense Tracker works fully offline. For bill splits it also stores participant names, parsed incoming sender names, payment status, and only the PayID/account name you explicitly enter. It does not request internet access, banking credentials, SMS access, analytics, or accessibility access. Raw notification text is parsed in memory and is never stored or uploaded.")
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete all local data?") }, text = { Text("This permanently removes every transaction and spending-period boundary from this device.") }, confirmButton = { Button({ vm.deleteAll(); confirmDelete = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecycleBinScreen(goBack: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val transactions by vm.deletedTransactions.collectAsStateWithLifecycle()
    var permanentlyDelete by remember { mutableStateOf<Transaction?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(goBack) { Text("Back") } },
                actions = {
                    TextButton({ confirmEmpty = true }, enabled = transactions.isNotEmpty()) {
                        Text("Empty Bin", color = if (transactions.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Deleted transactions stay here until you restore them or empty the bin.", style = MaterialTheme.typography.bodyMedium) }
            if (transactions.isEmpty()) item {
                Text("Recycle Bin is empty.", Modifier.fillMaxWidth().padding(vertical = 40.dp), textAlign = TextAlign.Center)
            }
            items(transactions, key = { it.id }) { transaction ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(transaction.merchant, fontWeight = FontWeight.SemiBold)
                                Text(formatAustralianDate(transaction.timestamp), style = MaterialTheme.typography.bodySmall)
                                if (transaction.hasSplit) Text("Includes bill split", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(formatAud(transaction.amountCents), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ vm.restoreTransactions(setOf(transaction.id)) }, Modifier.weight(1f)) { Text("Restore") }
                            OutlinedButton(
                                { permanentlyDelete = transaction },
                                Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete Forever") }
                        }
                    }
                }
            }
        }
    }

    permanentlyDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { permanentlyDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("“${transaction.merchant}” and its attached bill split cannot be recovered.") },
            confirmButton = {
                Button(
                    { vm.permanentlyDeleteTransactions(setOf(transaction.id)); permanentlyDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton({ permanentlyDelete = null }) { Text("Cancel") } }
        )
    }

    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Empty Recycle Bin?") },
        text = { Text("Permanently delete all ${transactions.size} transaction${if (transactions.size == 1) "" else "s"} in the bin? This cannot be undone.") },
        confirmButton = {
            Button(
                { vm.emptyRecycleBin(); confirmEmpty = false },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Empty Bin") }
        },
        dismissButton = { TextButton({ confirmEmpty = false }) { Text("Cancel") } }
    )
}
