package de.lb.mp3tag.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.lb.mp3tag.actions.Action
import de.lb.mp3tag.actions.ActionGroup
import de.lb.mp3tag.actions.CaseMode
import de.lb.mp3tag.actions.describe

/**
 * Action groups: list, create/edit, delete, and apply to the current targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsDialog(
    groups: List<ActionGroup>,
    targetCount: Int,
    onApply: (ActionGroup) -> Unit,
    onSave: (ActionGroup) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<ActionGroup?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val editingGroup = editing
            if (editingGroup == null) {
                GroupList(
                    groups = groups,
                    targetCount = targetCount,
                    onApply = onApply,
                    onEdit = { editing = it },
                    onDelete = onDelete,
                    onNew = { editing = ActionGroup("", emptyList()) },
                    onDismiss = onDismiss,
                )
            } else {
                GroupEditor(
                    group = editingGroup,
                    onSave = { group ->
                        onSave(group)
                        editing = null
                    },
                    onCancel = { editing = null },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupList(
    groups: List<ActionGroup>,
    targetCount: Int,
    onApply: (ActionGroup) -> Unit,
    onEdit: (ActionGroup) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text("Actions") },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            },
            actions = {
                TextButton(onClick = onNew) { Text("New group") }
            },
        )
        if (groups.isEmpty()) {
            Text(
                text = "No action groups yet. Create one to batch text replacements, " +
                    "case conversion, field formatting and more.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(groups, key = { it.name }) { group ->
                ListItem(
                    headlineContent = { Text(group.name) },
                    supportingContent = { Text("${group.actions.size} action(s)") },
                    trailingContent = {
                        Row {
                            TextButton(
                                onClick = { onApply(group) },
                                enabled = targetCount > 0 && group.actions.isNotEmpty(),
                            ) {
                                Text("Apply ($targetCount)")
                            }
                            IconButton(onClick = { onDelete(group.name) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete group")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onEdit(group) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupEditor(
    group: ActionGroup,
    onSave: (ActionGroup) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    val actions = remember { group.actions.toMutableStateList() }
    var showAddForm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.imePadding()) {
        TopAppBar(
            title = { Text(if (group.name.isEmpty()) "New action group" else "Edit group") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            },
            actions = {
                TextButton(
                    onClick = { onSave(ActionGroup(name.trim(), actions.toList())) },
                    enabled = name.isNotBlank() && actions.isNotEmpty(),
                ) {
                    Text("Save")
                }
            },
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Group name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(actions) { index, action ->
                ListItem(
                    headlineContent = { Text(action.describe()) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val a = actions[index]
                                        actions[index] = actions[index - 1]
                                        actions[index - 1] = a
                                    }
                                },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = {
                                    if (index < actions.lastIndex) {
                                        val a = actions[index]
                                        actions[index] = actions[index + 1]
                                        actions[index + 1] = a
                                    }
                                },
                                enabled = index < actions.lastIndex,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            IconButton(onClick = { actions.removeAt(index) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove action")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
            item(key = "add") {
                if (showAddForm) {
                    AddActionForm(
                        onAdd = { action ->
                            actions.add(action)
                            showAddForm = false
                        },
                        onCancel = { showAddForm = false },
                    )
                } else {
                    OutlinedButton(
                        onClick = { showAddForm = true },
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add action")
                    }
                }
            }
        }
    }
}

private enum class ActionType(val label: String) {
    CASE("Case conversion"),
    REPLACE("Replace"),
    REGEX_REPLACE("Replace with regular expression"),
    FORMAT_VALUE("Format value"),
    GUESS_VALUES("Guess values"),
    REMOVE_FIELDS("Remove fields"),
    REMOVE_EXCEPT("Remove all fields except"),
    SPLIT_FIELD("Split field by separator"),
}

@Composable
private fun AddActionForm(
    onAdd: (Action) -> Unit,
    onCancel: () -> Unit,
) {
    var type by remember { mutableStateOf(ActionType.REPLACE) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("TITLE") }
    var param1 by remember { mutableStateOf("") }
    var param2 by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var caseMode by remember { mutableStateOf(CaseMode.CAPS) }
    var caseMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedButton(onClick = { typeMenuOpen = true }) {
            Text(type.label)
        }
        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
            for (t in ActionType.entries) {
                DropdownMenuItem(
                    text = { Text(t.label) },
                    onClick = { type = t; typeMenuOpen = false },
                )
            }
        }

        if (type != ActionType.GUESS_VALUES && type != ActionType.REMOVE_FIELDS &&
            type != ActionType.REMOVE_EXCEPT
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.uppercase() },
                label = { Text("Field") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }

        when (type) {
            ActionType.CASE -> {
                OutlinedButton(onClick = { caseMenuOpen = true }) {
                    Text("Mode: ${caseMode.name}")
                }
                DropdownMenu(expanded = caseMenuOpen, onDismissRequest = { caseMenuOpen = false }) {
                    for (mode in CaseMode.entries) {
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = { caseMode = mode; caseMenuOpen = false },
                        )
                    }
                }
            }
            ActionType.REPLACE -> {
                ParamField("Replace", param1) { param1 = it }
                ParamField("With", param2) { param2 = it }
                Row {
                    Checkbox(checked = matchCase, onCheckedChange = { matchCase = it })
                    Text(
                        "Match case",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
            ActionType.REGEX_REPLACE -> {
                ParamField("Regular expression", param1) { param1 = it }
                ParamField("Replacement", param2) { param2 = it }
            }
            ActionType.FORMAT_VALUE -> ParamField("Format string", param1) { param1 = it }
            ActionType.GUESS_VALUES -> {
                ParamField("Source format (e.g. %title%)", param1) { param1 = it }
                ParamField("Guessing pattern (e.g. %artist% - %title%)", param2) { param2 = it }
            }
            ActionType.REMOVE_FIELDS -> ParamField("Fields (comma-separated)", param1) { param1 = it }
            ActionType.REMOVE_EXCEPT -> ParamField("Fields to keep (comma-separated)", param1) { param1 = it }
            ActionType.SPLIT_FIELD -> ParamField("Separator", param1) { param1 = it }
        }

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                val action = buildAction(type, field, param1, param2, matchCase, caseMode)
                if (action != null) onAdd(action)
            }) {
                Text("Add")
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ParamField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

private fun buildAction(
    type: ActionType,
    field: String,
    param1: String,
    param2: String,
    matchCase: Boolean,
    caseMode: CaseMode,
): Action? {
    fun fields(text: String) = text.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    return when (type) {
        ActionType.CASE -> if (field.isBlank()) null else Action.CaseConversion(field, caseMode)
        ActionType.REPLACE ->
            if (field.isBlank() || param1.isEmpty()) null
            else Action.Replace(field, param1, param2, matchCase)
        ActionType.REGEX_REPLACE ->
            if (field.isBlank() || param1.isEmpty()) null
            else Action.RegexReplace(field, param1, param2)
        ActionType.FORMAT_VALUE ->
            if (field.isBlank() || param1.isEmpty()) null else Action.FormatValue(field, param1)
        ActionType.GUESS_VALUES ->
            if (param1.isEmpty() || param2.isEmpty()) null else Action.GuessValues(param1, param2)
        ActionType.REMOVE_FIELDS ->
            fields(param1).ifEmpty { null }?.let { Action.RemoveFields(it) }
        ActionType.REMOVE_EXCEPT ->
            fields(param1).ifEmpty { null }?.let { Action.RemoveAllExcept(it) }
        ActionType.SPLIT_FIELD ->
            if (field.isBlank() || param1.isEmpty()) null else Action.SplitField(field, param1)
    }
}
