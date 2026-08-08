package com.example.spendtracker.ui

import android.graphics.Bitmap
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spendtracker.domain.model.BillSplitDetails
import com.example.spendtracker.domain.model.IncomingPayment
import com.example.spendtracker.domain.model.Transaction
import com.example.spendtracker.domain.model.SplitParticipantEdit
import com.example.spendtracker.share.BillSplitImageGenerator
import com.example.spendtracker.share.BillSplitTextGenerator
import com.example.spendtracker.ui.home.SelectedSplitState

@Composable
fun TransactionBillSplitDialog(
    transaction: Transaction,
    state: SelectedSplitState,
    defaultAccountName: String,
    defaultPayId: String,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, String, String) -> Unit,
    onAssign: (Long, Long) -> Unit,
    onMarkPaid: (Long) -> Unit,
    onUndoPaid: (Long) -> Unit,
    onEditPeople: (Long, List<SplitParticipantEdit>) -> Unit,
    onUndoCombination: (Long) -> Unit,
    onCancelSplit: (Long) -> Unit,
    onCloseSplit: (Long) -> Unit,
    onReopenSplit: (Long, Set<Long>) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(transaction.merchant, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${formatAud(transaction.amountCents)} · ${formatAustralianDate(transaction.timestamp)}")
                    }
                    TextButton(onDismiss) { Text("Close") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                when {
                    state.transactionId != transaction.id || state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.details == null -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No bill split yet", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text("Create a split, add participant names, and track exact repayments.")
                                Spacer(Modifier.height(20.dp))
                                Button({ showCreate = true }) { Text("Create Bill Split") }
                            }
                        }
                        if (state.lineItems.isNotEmpty()) {
                            item { Text("Included transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                            items(state.lineItems, key = { "unsplit-line-${it.id}" }) { lineItem ->
                                ListItem(
                                    headlineContent = { Text(lineItem.title) },
                                    supportingContent = { Text(formatAustralianDate(lineItem.timestamp)) },
                                    trailingContent = { Text(formatAud(lineItem.amountCents), fontWeight = FontWeight.SemiBold) }
                                )
                            }
                            item {
                                OutlinedButton(
                                    { onUndoCombination(transaction.id); onDismiss() },
                                    Modifier.fillMaxWidth()
                                ) { Text("Undo Combination & Choose Again") }
                            }
                        }
                    }
                    else -> BillSplitDetailsContent(
                        transaction, state.details, onAssign, onMarkPaid, onUndoPaid, onEditPeople,
                        { transactionId -> onUndoCombination(transactionId); onDismiss() },
                        onCancelSplit, onCloseSplit, onReopenSplit
                    )
                }
            }
        }
    }
    if (showCreate) CreateBillSplitDialog(transaction, defaultAccountName, defaultPayId, { showCreate = false }) { title, names, account, payId ->
        onCreate(title, names, account, payId)
        showCreate = false
    }
}

@Composable
private fun CreateBillSplitDialog(
    transaction: Transaction,
    defaultAccountName: String,
    defaultPayId: String,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, String, String) -> Unit
) {
    var title by remember { mutableStateOf(transaction.merchant) }
    var countText by remember { mutableStateOf("2") }
    var namesText by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf(defaultAccountName) }
    var payId by remember { mutableStateOf(defaultPayId) }
    val count = countText.toIntOrNull()?.takeIf { it in 2..50 }
    val enteredNames = namesText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val names = count?.let { total -> List(total - 1) { index -> enteredNames.getOrNull(index).orEmpty() } }.orEmpty()
    val perPerson = count?.let { transaction.amountCents / it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create bill split") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(countText, { countText = it.filter(Char::isDigit) }, label = { Text("Total people, including me") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(namesText, { namesText = it }, label = { Text("Other people (optional, one per line)") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                item { Text("Leave all names blank to count exact payments automatically in arrival order. If you enter names, unmatched notifications stay available for manual assignment.", style = MaterialTheme.typography.bodySmall) }
                item { OutlinedTextField(accountName, { accountName = it }, label = { Text("My account name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(payId, { payId = it }, label = { Text("My PayID") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                perPerson?.let { item { Text("Each person pays exactly ${formatAud(it)}", fontWeight = FontWeight.Bold) } }
            }
        },
        confirmButton = { Button({ onCreate(title, names, accountName, payId) }, enabled = count != null && accountName.isNotBlank() && payId.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CreateCombinedSplitDialog(
    transactions: List<Transaction>,
    defaultAccountName: String,
    defaultPayId: String,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, String, String) -> Unit
) {
    if (transactions.isEmpty()) return
    val combined = Transaction(
        id = transactions.first().id,
        merchant = "Combined ${transactions.size} transactions",
        amountCents = transactions.sumOf { it.amountCents },
        timestamp = transactions.maxOf { it.timestamp },
        source = "COMBINED"
    )
    CreateBillSplitDialog(combined, defaultAccountName, defaultPayId, onDismiss, onCreate)
}

@Composable
private fun BillSplitDetailsContent(
    transaction: Transaction,
    split: BillSplitDetails,
    onAssign: (Long, Long) -> Unit,
    onMarkPaid: (Long) -> Unit,
    onUndoPaid: (Long) -> Unit,
    onEditPeople: (Long, List<SplitParticipantEdit>) -> Unit,
    onUndoCombination: (Long) -> Unit,
    onCancelSplit: (Long) -> Unit,
    onCloseSplit: (Long) -> Unit,
    onReopenSplit: (Long, Set<Long>) -> Unit
) {
    var confirmImage by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var assigningPayment by remember { mutableStateOf<IncomingPayment?>(null) }
    var editingPeople by remember { mutableStateOf<List<SplitParticipantEdit>?>(null) }
    var confirmUndoCombination by remember { mutableStateOf(false) }
    var confirmCancelSplit by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var showReopen by remember { mutableStateOf(false) }
    var reopenIds by remember { mutableStateOf(setOf<Long>()) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (split.isCompleted) androidx.compose.ui.graphics.Color(0xFFE1F4E6) else androidx.compose.ui.graphics.Color(0xFFFFE7E7))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${formatAud(split.perPersonCents)} each", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Combined total: ${formatAud(split.totalCents)}")
                    Text("Your final spending after everyone pays: ${formatAud(split.perPersonCents)}", fontWeight = FontWeight.SemiBold)
                    Text(when {
                        split.isClosed -> "${split.paidCount} / ${split.participants.size} Paid — Closed ✓"
                        split.isCompleted -> "${split.paidCount} / ${split.participants.size} Paid — Completed ✓"
                        else -> "${split.paidCount} / ${split.participants.size} Paid"
                    })
                }
            }
        }
        if (split.lineItems.isNotEmpty()) {
            item { Text("Included transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(split.lineItems, key = { "line-${it.id}" }) { lineItem ->
                ListItem(
                    headlineContent = { Text(lineItem.title) },
                    supportingContent = { Text(formatAustralianDate(lineItem.timestamp)) },
                    trailingContent = { Text(formatAud(lineItem.amountCents), fontWeight = FontWeight.SemiBold) }
                )
            }
            item {
                OutlinedButton(
                    { confirmUndoCombination = true },
                    Modifier.fillMaxWidth(),
                    enabled = split.payments.isEmpty() && split.participants.none { !it.isOwner && it.isPaid }
                ) { Text("Undo Combination & Choose Again") }
                if (split.payments.isNotEmpty() || split.participants.any { !it.isOwner && it.isPaid }) {
                    Text("The combination is locked after a repayment is recorded.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Participants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton({
                    editingPeople = split.participants.filterNot { it.isOwner }.map {
                        SplitParticipantEdit(it.id, if (split.autoAssignAnonymous) "" else it.name)
                    }
                }) { Text("Edit People") }
            }
        }
        if (split.autoAssignAnonymous) item { Text("Exact payments are assigned automatically in arrival order.", style = MaterialTheme.typography.bodySmall) }
        items(split.participants, key = { it.id }) { participant ->
            ListItem(
                headlineContent = { Text(participant.name) },
                supportingContent = { Text(when {
                    participant.isPaid -> "✓ Paid"
                    participant.isWaived -> "Covered by me"
                    else -> "○ Not Paid"
                }) },
                trailingContent = {
                    if (!participant.isPaid && !participant.isWaived) TextButton({ onMarkPaid(participant.id) }) { Text("Mark paid") }
                    else if (participant.isWaived) Text("Covered", style = MaterialTheme.typography.bodySmall)
                    else if (participant.isOwner) Text("You", color = androidx.compose.ui.graphics.Color(0xFF177245), fontWeight = FontWeight.Bold)
                    else if (!split.isClosed) TextButton({ onUndoPaid(participant.id) }) { Text("Undo paid") }
                }
            )
        }
        if (split.unassignedPayments.isNotEmpty()) {
            item { Text("Unassigned Payments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(split.unassignedPayments, key = { it.id }) { payment ->
                ListItem(
                    headlineContent = { Text("${formatAud(payment.amountCents)} received") },
                    supportingContent = { Text("From ${payment.senderName ?: "unknown sender"} · ${formatAustralianDate(payment.receivedAt)}") },
                    trailingContent = { Button({ assigningPayment = payment }) { Text("Assign") } }
                )
            }
        }
        item {
            Text("Payment details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("PayID: ${split.payId}")
            Text("Account Name: ${split.accountName}")
        }
        if (!split.isClosed && split.participants.any { !it.isOwner && !it.isPaid && !it.isWaived }) {
            item { OutlinedButton({ confirmClose = true }, Modifier.fillMaxWidth()) { Text("Cancel Remaining — Keep Paid") } }
        }
        if (split.isClosed && split.participants.any { !it.isOwner && !it.isPaid && it.isWaived }) {
            item { Button({ showReopen = true }, Modifier.fillMaxWidth()) { Text("Reopen Split") } }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ confirmImage = true }, Modifier.weight(1f).height(52.dp)) { Text("Share Image") }
                OutlinedButton({ previewText = BillSplitTextGenerator.generate(split, transaction.timestamp) }, Modifier.weight(1f).height(52.dp)) { Text("Share Text") }
            }
        }
        item {
            OutlinedButton(
                { confirmCancelSplit = true },
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Cancel Split") }
        }
    }
    if (assigningPayment != null) AlertDialog(
        onDismissRequest = { assigningPayment = null },
        title = { Text("Who sent this payment?") },
        text = {
            Column {
                split.participants.filterNot { it.isPaid }.forEach { participant ->
                    TextButton({ onAssign(requireNotNull(assigningPayment).id, participant.id); assigningPayment = null }, Modifier.fillMaxWidth()) { Text(participant.name) }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton({ assigningPayment = null }) { Text("Cancel") } }
    )
    if (confirmUndoCombination) AlertDialog(
        onDismissRequest = { confirmUndoCombination = false },
        title = { Text("Undo this combination?") },
        text = { Text("The large combined transaction and its new split will be removed. Every original transaction and any original unpaid split will return to the list so you can select a different combination.") },
        confirmButton = {
            Button({ onUndoCombination(transaction.id); confirmUndoCombination = false }) { Text("Undo Combination") }
        },
        dismissButton = { TextButton({ confirmUndoCombination = false }) { Text("Cancel") } }
    )
    if (confirmCancelSplit) AlertDialog(
        onDismissRequest = { confirmCancelSplit = false },
        title = { Text("Cancel this split?") },
        text = { Text("The split status, participants, and green/red colour will be removed. The expense and combined item list will remain as a normal transaction, so you can split it again. Payments actually received will remain in Earnings.") },
        confirmButton = {
            Button(
                { onCancelSplit(split.id); confirmCancelSplit = false },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Cancel Split") }
        },
        dismissButton = { TextButton({ confirmCancelSplit = false }) { Text("Keep Split") } }
    )
    editingPeople?.let { edits ->
        val canChangeCount = !split.isClosed && split.participants.none { !it.isOwner && it.isPaid } && split.payments.isEmpty()
        AlertDialog(
            onDismissRequest = { editingPeople = null },
            title = { Text("Edit people in split") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("You are always included. Names may be changed without affecting payment status.") }
                    items(edits.size) { index ->
                        val edit = edits[index]
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = edit.name,
                                onValueChange = { name -> editingPeople = edits.toMutableList().also { it[index] = edit.copy(name = name) } },
                                label = { Text("Person ${index + 1} (optional)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            TextButton(
                                onClick = { editingPeople = edits.toMutableList().also { it.removeAt(index) } },
                                enabled = canChangeCount && edits.size > 1
                            ) { Text("Remove") }
                        }
                    }
                    item {
                        OutlinedButton(
                            { editingPeople = edits + SplitParticipantEdit(null, "") },
                            Modifier.fillMaxWidth(),
                            enabled = canChangeCount && edits.size < 49
                        ) { Text("Add Person") }
                    }
                    if (!canChangeCount) item {
                        Text("People cannot be added or removed after a repayment is recorded or the split is closed. You can still correct their names.", style = MaterialTheme.typography.bodySmall)
                    }
                    item { Text("New amount: ${formatAud(split.totalCents / (edits.size + 1))} each", fontWeight = FontWeight.SemiBold) }
                }
            },
            confirmButton = {
                Button({ onEditPeople(split.id, edits); editingPeople = null }, enabled = edits.isNotEmpty()) { Text("Save") }
            },
            dismissButton = { TextButton({ editingPeople = null }) { Text("Cancel") } }
        )
    }
    if (confirmImage) AlertDialog(
        onDismissRequest = { confirmImage = false },
        title = { Text("Confirm shared payment details") },
        text = { Text("The image will include:\n\nPayID: ${split.payId}\nAccount Name: ${split.accountName}\n\nNo other banking information will be included.") },
        confirmButton = { Button({ previewBitmap = BillSplitImageGenerator.generate(split, transaction.timestamp); confirmImage = false }) { Text("Preview Image") } },
        dismissButton = { TextButton({ confirmImage = false }) { Text("Cancel") } }
    )
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Stop splitting the remaining amount?") },
        text = { Text("The split will close at its current state. Everyone already paid will remain paid. The unpaid shares will be covered by you, and the transaction will turn green.") },
        confirmButton = { Button({ onCloseSplit(split.id); confirmClose = false }) { Text("Keep Paid & Close") } },
        dismissButton = { TextButton({ confirmClose = false }) { Text("Cancel") } }
    )
    if (showReopen) AlertDialog(
        onDismissRequest = { showReopen = false; reopenIds = emptySet() },
        title = { Text("Choose unpaid people") },
        text = {
            Column {
                Text("Only people who have not paid can be included.")
                split.participants.filter { !it.isOwner && !it.isPaid && it.isWaived }.forEach { participant ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(participant.id in reopenIds, { checked -> reopenIds = if (checked) reopenIds + participant.id else reopenIds - participant.id })
                        Text(participant.name)
                    }
                }
            }
        },
        confirmButton = { Button({ onReopenSplit(split.id, reopenIds); showReopen = false; reopenIds = emptySet() }, enabled = reopenIds.isNotEmpty()) { Text("Reopen") } },
        dismissButton = { TextButton({ showReopen = false; reopenIds = emptySet() }) { Text("Cancel") } }
    )
    previewBitmap?.let { bitmap -> ShareImagePreview(bitmap) { previewBitmap = null } }
    previewText?.let { text -> ShareTextPreview(text) { previewText = null } }
}

@Composable
private fun ShareTextPreview(text: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Payment text preview") },
        text = { SelectionContainer { Text(text) } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Bill split payment details", text))
                }) { Text("Copy Text") }
                Button({ BillSplitTextGenerator.share(context, text) }) { Text("Share Text") }
            }
        },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun ShareImagePreview(bitmap: Bitmap, onCancel: () -> Unit) {
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) } }
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("Share image preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Confirm your PayID and account name before saving or sharing.", style = MaterialTheme.typography.bodySmall)
                Image(bitmap.asImageBitmap(), "Bill split share image preview", Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
                    Button({ saveLauncher.launch("expense-tracker-bill-split.png") }, Modifier.weight(1f)) { Text("Save Image") }
                    Button({ BillSplitImageGenerator.share(context, bitmap) }, Modifier.weight(1f)) { Text("Share Image") }
                }
            }
        }
    }
}
