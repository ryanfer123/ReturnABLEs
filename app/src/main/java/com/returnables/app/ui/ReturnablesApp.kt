package com.returnables.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.returnables.app.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val readableDate = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.getDefault())

// Save only Bundle-safe primitives; editing identity and receipt reuse survive recreation.
private val PurchaseSeedSaver = listSaver<Purchase?, String>(
    save = { p -> if (p == null) listOf("none") else listOf("purchase", p.id, p.title, p.merchant,
        p.purchasedOn.toString(), p.amountMinor?.toString().orEmpty(), p.currency,
        p.returnBy?.toString().orEmpty(), p.warrantyUntil?.toString().orEmpty(), p.notes,
        p.receiptName.orEmpty(), p.receiptMime.orEmpty(), p.ocrText, p.status.name,
        p.createdAt.toString(), p.updatedAt.toString()) },
    restore = { v -> if (v.firstOrNull() == "none") null else runCatching {
        Purchase(id=v[1],title=v[2],merchant=v[3],purchasedOn=LocalDate.parse(v[4]),
            amountMinor=v[5].toLongOrNull(),currency=v[6],returnBy=v[7].takeIf{it.isNotEmpty()}?.let(LocalDate::parse),
            warrantyUntil=v[8].takeIf{it.isNotEmpty()}?.let(LocalDate::parse),notes=v[9],
            receiptName=v[10].takeIf{it.isNotEmpty()},receiptMime=v[11].takeIf{it.isNotEmpty()},
            ocrText=v[12],status=PurchaseStatus.valueOf(v[13]),createdAt=v[14].toLong(),updatedAt=v[15].toLong())
    }.getOrNull() }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnablesApp(state: ReturnUiState, actions: ReturnActions, darkTheme: Boolean = isSystemInDarkTheme()) {
    var route by rememberSaveable { mutableStateOf("home") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSeed by rememberSaveable(stateSaver = PurchaseSeedSaver) { mutableStateOf<Purchase?>(null) }
    var discardEditor by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val screenKeyboard=LocalSoftwareKeyboardController.current
    val screenFocus=LocalFocusManager.current
    LaunchedEffect(route) { screenKeyboard?.hide();screenFocus.clearFocus() }
    val selected = state.purchases.firstOrNull { it.id == selectedId }
    fun finishDiscard() { actions.clearReceiptDraft(); discardEditor=false; route=if(selected!=null) "detail" else "home" }
    fun leave() { if(route=="edit") discardEditor=true else route="home" }
    BackHandler(enabled = route != "home") { if(!state.busy) leave() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it,withDismissAction=true); actions.clearMessage() }
    }
    ReturnablesTheme(darkTheme=darkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(if (route == "home") "returnABLEs" else when(route) { "edit" -> if(editorSeed?.title.isNullOrEmpty()) "Add purchase" else "Edit purchase"; "settings" -> "Settings"; else -> "Purchase" }, fontWeight = FontWeight.Bold) },
                    navigationIcon = { if(route != "home") IconButton(onClick = { leave() }, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = { if(route == "home") IconButton(onClick = { route = "settings" }) { Icon(Icons.Default.Settings, "Settings") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            floatingActionButton = {
                if(route == "home") ExtendedFloatingActionButton(
                    modifier = Modifier.testTag("add-purchase").semantics(mergeDescendants = true) { contentDescription = "Add purchase" },
                    onClick = { actions.clearReceiptDraft(); selectedId=null; editorSeed=null; route="edit" },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add purchase", modifier = Modifier.clearAndSetSemantics { }) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    AnimatedContent(targetState=route to selectedId,
                        transitionSpec={ (fadeIn(tween(180)) + slideInVertically(tween(180)){it/32}) togetherWith fadeOut(tween(100)) },
                        label="Ledger screen") { screen ->
                    val displayedPurchase=state.purchases.firstOrNull { it.id==screen.second }
                    when(screen.first) {
                        "home" -> Home(state.purchases) { selectedId=it.id; route="detail" }
                        "settings" -> Settings(state, actions)
                        "edit" -> PurchaseEditor(editorSeed, state, actions, cancel={discardEditor=true}, done={ route="home"; selectedId=null; actions.clearReceiptDraft() })
                        else -> if(displayedPurchase != null) PurchaseDetails(displayedPurchase, state.busy, actions,
                            edit = { actions.clearReceiptDraft(); editorSeed=displayedPurchase; route="edit" },
                            another = {
                                actions.clearReceiptDraft()
                                val now=System.currentTimeMillis()
                                editorSeed=displayedPurchase.copy(id=UUID.randomUUID().toString(),title="",amountMinor=null,returnBy=null,warrantyUntil=null,notes="",status=PurchaseStatus.ACTIVE,createdAt=now,updatedAt=now)
                                selectedId=null; route="edit"
                            }) else Text("This purchase is no longer available.", Modifier.padding(24.dp))
                    }
                    }
                }
            }
        }
        if(discardEditor) AlertDialog(onDismissRequest={if(!state.busy) discardEditor=false},title={Text("Discard these edits?")},text={Text("Your saved purchases stay as they are. Unsaved changes and the new attachment will be discarded.")},confirmButton={TextButton(onClick={finishDiscard()},enabled=!state.busy){Text("Discard edits")}},dismissButton={TextButton(onClick={discardEditor=false},enabled=!state.busy){Text("Keep editing")}})
    }
}

@Composable
private fun Home(purchases: List<Purchase>, open: (Purchase) -> Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LedgerFilter.ALL.name) }
    var sort by rememberSaveable { mutableStateOf(LedgerSort.DEADLINE.name) }
    var receiptsOnly by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val today=LocalDate.now()
    val visible=LedgerQuery.select(purchases,today,search,LedgerFilter.valueOf(filter),LedgerSort.valueOf(sort),receiptsOnly)
    val overdue=LedgerQuery.overdueCount(purchases,today)
    val soon=LedgerQuery.dueSoonCount(purchases,today)
    val filters=listOf(LedgerFilter.ALL to "All",LedgerFilter.UPCOMING to "Upcoming",LedgerFilter.THIS_WEEK to "This week",LedgerFilter.OVERDUE to "Overdue",LedgerFilter.CLOSED to "Closed")
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("purchase-ledger"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(10.dp),border=BorderStroke(2.dp,MaterialTheme.colorScheme.outline)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Keep the receipt.\nKnow the date.",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.onPrimary)
                    Text("Your purchases, ready when it matters.",color=MaterialTheme.colorScheme.onPrimary,style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if(purchases.isNotEmpty()) item {
            Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=RoundedCornerShape(6.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick={filter=LedgerFilter.OVERDUE.name},modifier=Modifier.heightIn(min=48.dp)) { Text("$overdue overdue",color=MaterialTheme.colorScheme.onTertiaryContainer) }
                    TextButton(onClick={filter=LedgerFilter.THIS_WEEK.name},modifier=Modifier.heightIn(min=48.dp)) { Text("$soon due this week",color=MaterialTheme.colorScheme.onTertiaryContainer) }
                }
            }
        }
        item { OutlinedTextField(search,{search=it},label={Text("Search purchases")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}),leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            filters.forEach { (value,name) -> FilterChip(selected=filter==value.name,onClick={filter=value.name},label={Text(name)},modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(6.dp)) }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=receiptsOnly,onClick={receiptsOnly=!receiptsOnly},label={Text("Receipts only")},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(6.dp))
            Box(Modifier.weight(1f)) {
                TextButton(onClick={sortMenu=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("ledger-sort")) { Text("Sort: "+when(LedgerSort.valueOf(sort)){LedgerSort.DEADLINE->"Deadline";LedgerSort.NEWEST->"Newest";LedgerSort.TITLE->"Title"}) }
                DropdownMenu(expanded=sortMenu,onDismissRequest={sortMenu=false}) {
                    listOf(LedgerSort.DEADLINE to "Deadline",LedgerSort.NEWEST to "Newest",LedgerSort.TITLE to "Title").forEach { (value,label) -> DropdownMenuItem(text={Text(label)},onClick={sort=value.name;sortMenu=false}) }
                }
            }
        } }
        item { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(when(LedgerFilter.valueOf(filter)){LedgerFilter.UPCOMING->"Next on your calendar";LedgerFilter.OVERDUE->"Dates to revisit";LedgerFilter.THIS_WEEK->"This week's dates";else->"Your purchase ledger"},style=MaterialTheme.typography.titleLarge)
            Text("${visible.size} ${if(visible.size==1) "purchase" else "purchases"}"+if(receiptsOnly) " with receipts" else "",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("ledger-result-count").semantics { liveRegion=LiveRegionMode.Polite })
            if(filter==LedgerFilter.OVERDUE.name) Text("Active items with a past date. A later warranty may still be upcoming. Check the merchant’s terms.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if(visible.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(purchases.isEmpty()) "Your first receipt belongs here." else "No purchases in this view.",style=MaterialTheme.typography.titleLarge)
                Text(if(purchases.isEmpty()) "Tap Add purchase to record an item. Attach its receipt and confirm the dates that matter." else "Try All, turn off Receipts only, or change your search. Items without future deadlines are still in All.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visible,key={it.id}) { p -> PurchaseRow(p,today,filter==LedgerFilter.OVERDUE.name) { open(p) } }
        item { Text("Dates are reminders you confirm, not a merchant’s return policy. Check the original terms before acting.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PurchaseRow(p: Purchase,today: LocalDate,preferOverdue:Boolean=false,onClick:()->Unit) {
    val deadlines=DeadlineRules.forPurchase(p,today)
    val next=if(preferOverdue) deadlines.firstOrNull { it.daysRemaining < 0 } ?: deadlines.firstOrNull() else deadlines.firstOrNull { it.daysRemaining >= 0 } ?: deadlines.firstOrNull()
    Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline),modifier=Modifier.fillMaxWidth().testTag("purchase-row-${p.id}").clickable(onClick=onClick)) {
        Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(p.title,style=MaterialTheme.typography.titleMedium)
                Text(p.merchant,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(next!=null) "${if(next.kind==DeadlineKind.RETURN) "Return" else "Warranty"} · ${next.date.format(readableDate)}" else if(p.status==PurchaseStatus.ACTIVE) "No deadline recorded" else p.status.name.lowercase().replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.bodyMedium)
            }
            if(next!=null) Surface(color=if(next.daysRemaining<0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp)) {
                Text(when { next.daysRemaining<0 -> "Expired"; next.daysRemaining==0L -> "Today"; else -> "${next.daysRemaining} days\nleft" },Modifier.padding(12.dp),fontWeight=FontWeight.Bold,color=if(next.daysRemaining<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PurchaseDetails(p: Purchase,busy:Boolean,actions:ReturnActions,edit:()->Unit,another:()->Unit) {
    var pendingStatus by remember { mutableStateOf<PurchaseStatus?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(p.merchant,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
        Text(p.title,style=MaterialTheme.typography.headlineLarge)
        Text("Purchased ${p.purchasedOn.format(readableDate)}",style=MaterialTheme.typography.bodyLarge)
        p.amountMinor?.let { Text("${p.currency} ${Money.format(it,p.currency)}",style=MaterialTheme.typography.titleLarge) }
        Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline)) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Dates you confirmed",style=MaterialTheme.typography.titleMedium)
            Info("Return by",p.returnBy?.format(readableDate) ?: "Not recorded")
            Info("Warranty until",p.warrantyUntil?.format(readableDate) ?: "Not recorded")
            Text("${p.status.name.lowercase().replaceFirstChar{it.uppercase()}} · Verify merchant terms before a return or claim.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(p.status==PurchaseStatus.ACTIVE && p.returnBy!=null) OutlinedButton(onClick={actions.addToCalendar(p,DeadlineKind.RETURN)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Add return date to calendar") }
            if(p.status==PurchaseStatus.ACTIVE && p.warrantyUntil!=null) OutlinedButton(onClick={actions.addToCalendar(p,DeadlineKind.WARRANTY)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Add warranty date to calendar") }
            if(p.status==PurchaseStatus.ACTIVE && (p.returnBy!=null || p.warrantyUntil!=null)) Text("Opens a calendar draft. Review the date and save it in your calendar app.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if(p.receiptName!=null) {
            Text("Original receipt",style=MaterialTheme.typography.titleLarge)
            Text(p.receiptName,style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick={actions.openReceipt(p)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Open receipt") }
        } else Text("No original receipt attached.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(p.notes.isNotBlank()) { Text("Your notes",style=MaterialTheme.typography.titleLarge); Text(p.notes) }
        if(p.ocrText.isNotBlank()) { var expanded by remember { mutableStateOf(false) }; TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide extracted text" else "Review extracted text") }; if(expanded) Text(p.ocrText,style=MaterialTheme.typography.bodyMedium) }
        Button(onClick=edit,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Edit purchase") }
        OutlinedButton(onClick=another,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Add another item from this receipt") }
        OutlinedButton(onClick={actions.shareEvidence(p)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Share evidence") }
        Text("Sharing sends your notes and purchase details to the app you choose. Review personal information first.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Purchase status",style=MaterialTheme.typography.titleLarge)
        PurchaseStatus.entries.filter{it!=p.status}.forEach { status ->
            OutlinedButton(onClick={pendingStatus=status},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(when(status){PurchaseStatus.ACTIVE->"Mark active";PurchaseStatus.RETURNED->"Mark returned";PurchaseStatus.CLAIMED->"Mark claimed";PurchaseStatus.ARCHIVED->"Archive purchase"}) }
        }
        Spacer(Modifier.height(20.dp))
    }
    pendingStatus?.let { status -> AlertDialog(onDismissRequest={pendingStatus=null},title={Text("Update this purchase?")},text={Text("Set status to ${status.name.lowercase()}. This records your action and does not contact the merchant. Closed items stop deadline reminders.")},confirmButton={TextButton(onClick={actions.save(p.copy(status=status,updatedAt=System.currentTimeMillis())){pendingStatus=null}},enabled=!busy){Text("Confirm")}},dismissButton={TextButton(onClick={pendingStatus=null},enabled=!busy){Text("Cancel")}}) }
}

@Composable
private fun Info(label:String,value:String) { Column(verticalArrangement=Arrangement.spacedBy(3.dp)) { Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.titleMedium) } }

@Composable
private fun PurchaseEditor(seed:Purchase?,state:ReturnUiState,actions:ReturnActions,cancel:()->Unit,done:()->Unit) {
    val id=rememberSaveable(seed?.id) { seed?.id ?: UUID.randomUUID().toString() }
    val created=rememberSaveable(id) { seed?.createdAt ?: System.currentTimeMillis() }
    var title by rememberSaveable(id) { mutableStateOf(seed?.title ?: "") }
    var merchant by rememberSaveable(id) { mutableStateOf(seed?.merchant ?: "") }
    var purchased by rememberSaveable(id) { mutableStateOf((seed?.purchasedOn ?: LocalDate.now()).toString()) }
    var returnDate by rememberSaveable(id) { mutableStateOf(seed?.returnBy?.toString() ?: "") }
    var warranty by rememberSaveable(id) { mutableStateOf(seed?.warrantyUntil?.toString() ?: "") }
    var currency by rememberSaveable(id) { mutableStateOf(seed?.currency ?: "INR") }
    var amount by rememberSaveable(id) { mutableStateOf(seed?.amountMinor?.let { Money.format(it,seed.currency) } ?: "") }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes ?: "") }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    val draft=state.receiptDraft
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(if(seed?.title.isNullOrBlank()) "Give this purchase\na place to live." else "Keep the details current.",style=MaterialTheme.typography.headlineMedium)
        Text("One item per entry. You can reuse a receipt for other items after saving.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Receipt",style=MaterialTheme.typography.titleLarge)
        Button(onClick=actions::captureReceipt,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Take receipt photo") }
        OutlinedButton(onClick=actions::attachReceipt,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Import receipt image or PDF") }
        Text(draft?.fileName ?: seed?.receiptName ?: "A receipt is optional. You can attach it later.",style=MaterialTheme.typography.bodyMedium)
        if(state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working on your receipt…",style=MaterialTheme.typography.bodyMedium) }
        if(draft!=null) {
            Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(10.dp),modifier=Modifier.animateContentSize()) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Review receipt suggestions",style=MaterialTheme.typography.titleMedium)
                Text("Nothing below changes a date or amount until you choose it. Check the receipt; extraction can be wrong.",style=MaterialTheme.typography.bodyMedium)
                draft.suggestions.merchantGuess?.let { guess -> OutlinedButton(onClick={merchant=guess},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Use merchant: $guess") } }
                draft.suggestions.dateCandidates.forEach { candidate ->
                    Text(candidate.sourceLine,style=MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick={purchased=candidate.date.toString()},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Use purchase date ${candidate.date}${if(candidate.ambiguous) " (ambiguous)" else ""}") }
                }
                draft.suggestions.totalCandidates.forEach { candidate ->
                    Text(candidate.sourceLine,style=MaterialTheme.typography.bodyMedium)
                    val candidateCurrency=candidate.currency ?: "INR"
                    OutlinedButton(onClick={currency=candidateCurrency;amount=Money.format(candidate.minor,candidateCurrency)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Use ${candidateCurrency} ${Money.format(candidate.minor,candidateCurrency)}${if(candidate.currency==null) " (confirm currency)" else ""}") }
                }
                if(draft.suggestions.dateCandidates.isEmpty() && draft.suggestions.totalCandidates.isEmpty()) Text("No reliable date or total found. Enter the details below.",style=MaterialTheme.typography.bodyMedium)
                var showText by remember(draft) { mutableStateOf(false) }
                TextButton(onClick={showText=!showText}) { Text(if(showText) "Hide extracted text" else "Show extracted text") }
                if(showText) Text(draft.text.ifBlank { "No text extracted. The original file remains attached." },style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick=actions::clearReceiptDraft,enabled=!state.busy) { Text("Discard this new attachment") }
            } }
        }
        Text("Purchase details",style=MaterialTheme.typography.titleLarge)
        Field("Item title",title,{title=it},!state.busy)
        Field("Merchant",merchant,{merchant=it},!state.busy)
        DateField("Purchase date",purchased,{purchased=it},!state.busy)
        Field("Currency code (for example INR)",currency,{currency=it.uppercase(Locale.ROOT)},!state.busy)
        Field("Amount (optional)",amount,{amount=it},!state.busy,KeyboardType.Decimal)
        Text("Deadlines you confirm",style=MaterialTheme.typography.titleLarge)
        Text("Use dates from the merchant’s terms. We do not infer a return policy or warranty from a receipt.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        DateField("Return by (optional)",returnDate,{returnDate=it},!state.busy)
        DateField("Warranty until (optional)",warranty,{warranty=it},!state.busy)
        OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},enabled=!state.busy,minLines=3,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
        if(errors.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(8.dp),modifier=Modifier.animateContentSize().semantics(mergeDescendants=true) { liveRegion=LiveRegionMode.Polite }) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text("Check these details",fontWeight=FontWeight.Bold); errors.forEach { Text(it,color=MaterialTheme.colorScheme.error) } } }
        Button(onClick={
            val problems=mutableListOf<String>()
            fun date(value:String,label:String,optional:Boolean=false):LocalDate? {
                if(optional && value.isBlank()) return null
                return runCatching { LocalDate.parse(value.trim()) }.getOrElse { problems.add("$label must be a valid date in YYYY-MM-DD format.");null }
            }
            val bought=date(purchased,"Purchase date")
            val returns=date(returnDate,"Return date",true)
            val warrantyDate=date(warranty,"Warranty date",true)
            val minor=if(amount.isBlank()) null else runCatching{Money.parseMinor(amount,currency.trim())}.getOrElse { problems.add(it.message ?: "Check the amount.");null }
            if(bought!=null) {
                val p=Purchase(id=id,title=title.trim(),merchant=merchant.trim(),purchasedOn=bought,amountMinor=minor,currency=currency.trim(),returnBy=returns,warrantyUntil=warrantyDate,notes=notes,receiptName=draft?.fileName ?: seed?.receiptName,receiptMime=draft?.mimeType ?: seed?.receiptMime,ocrText=draft?.text ?: seed?.ocrText.orEmpty(),status=seed?.status ?: PurchaseStatus.ACTIVE,createdAt=created,updatedAt=maxOf(created,System.currentTimeMillis()))
                problems.addAll(PurchaseRules.validate(p))
                if(problems.isEmpty()) actions.save(p,done)
            }
            errors=problems.distinct()
        },enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(state.busy) "Saving…" else "Save purchase") }
        TextButton(onClick=cancel,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Cancel editing") }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Field(label:String,value:String,change:(String)->Unit,enabled:Boolean,type:KeyboardType=KeyboardType.Text) {
    OutlinedTextField(value,change,label={Text(label)},enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=type),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label:String,value:String,change:(String)->Unit,enabled:Boolean) {
    var picker by remember { mutableStateOf(false) }
    OutlinedTextField(value,change,label={Text(label)},placeholder={Text("YYYY-MM-DD")},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),trailingIcon={IconButton(onClick={picker=true},enabled=enabled){Icon(Icons.Default.DateRange,"Choose $label")}})
    if(picker) {
        val initial=runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val dateState=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={dateState.selectedDateMillis?.let{change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};picker=false},enabled=dateState.selectedDateMillis!=null){Text("Use date")}},dismissButton={TextButton(onClick={picker=false}){Text("Cancel")}}) { DatePicker(state=dateState) }
    }
}

@Composable
private fun Settings(state:ReturnUiState,actions:ReturnActions) {
    var backupWarning by remember { mutableStateOf(false) }
    var restoreWarning by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("Your receipts.\nYour control.",style=MaterialTheme.typography.headlineLarge)
        Text("Reminders",style=MaterialTheme.typography.titleLarge)
        Text(if(state.notificationsEnabled) "Notifications are allowed." else "Notifications are not currently allowed.",style=MaterialTheme.typography.titleMedium)
        Text("Reminders help you revisit dates you recorded. Android schedules background work approximately; battery settings and device restrictions may delay delivery. Keep important dates in your calendar too.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=actions::requestNotifications,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(if(state.notificationsEnabled) "Review notification settings" else "Enable reminders") }
        HorizontalDivider()
        Text("Backup & restore",style=MaterialTheme.typography.titleLarge)
        Text("Export a copy before changing phones. A backup includes purchase details, notes and receipt files. Keep it somewhere only you can access.")
        Button(onClick={backupWarning=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Export backup") }
        OutlinedButton(onClick={restoreWarning=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Restore backup") }
        HorizontalDivider()
        Text("Privacy, in plain language",style=MaterialTheme.typography.titleLarge)
        Text("Purchases and receipt files stay in this app’s local storage. Receipt text is processed on your device. Sharing or exporting creates a copy outside the app; you choose where it goes.")
        Text("Receipts may contain addresses, payment references and other personal details. Review evidence before sharing. Uninstalling the app can remove local records; keep a backup you trust.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("returnABLEs",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
        Text("A personal ledger, not a return-policy guarantee.",style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
    }
    if(backupWarning) AlertDialog(onDismissRequest={backupWarning=false},title={Text("This backup is not encrypted")},text={Text("The exported file contains readable purchase information and receipts. Anyone with the file can read it. Choose a private location and protect the copy.")},confirmButton={TextButton(onClick={backupWarning=false;actions.exportBackup()}){Text("Choose backup location")}},dismissButton={TextButton(onClick={backupWarning=false}){Text("Cancel")}})
    if(restoreWarning) AlertDialog(onDismissRequest={restoreWarning=false},title={Text("Restore a trusted backup")},text={Text("Only open a backup you created and trust. Review the restored purchases and reminder dates afterward.")},confirmButton={TextButton(onClick={restoreWarning=false;actions.restoreBackup()}){Text("Choose backup file")}},dismissButton={TextButton(onClick={restoreWarning=false}){Text("Cancel")}})
}

@Preview(showBackground=true,showSystemUi=true)
@Composable
private fun EmptyLedgerPreview() { ReturnablesApp(ReturnUiState(),PreviewActions) }
@Preview(showBackground=true,fontScale=1.4f)
@Composable
private fun PurchaseLedgerPreview() { ReturnablesApp(ReturnUiState(purchases=listOf(Purchase("preview","Everyday headphones","Local electronics",LocalDate.now().minusDays(5),amountMinor=249900,returnBy=LocalDate.now().plusDays(9)))),PreviewActions) }
private object PreviewActions:ReturnActions {
    override fun save(purchase:Purchase,onComplete:()->Unit)=onComplete()
    override fun attachReceipt(){};override fun captureReceipt(){};override fun clearReceiptDraft(){}
    override fun openReceipt(purchase:Purchase){};override fun shareEvidence(purchase:Purchase){}
    override fun exportBackup(){};override fun restoreBackup(){};override fun requestNotifications(){};override fun clearMessage(){}
}
