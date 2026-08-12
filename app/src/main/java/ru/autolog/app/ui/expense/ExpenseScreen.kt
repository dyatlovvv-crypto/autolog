package ru.autolog.app.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.autolog.app.AutologApp
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors

object ExpenseTags {
    const val SCREEN = "expense_screen"
    const val SAVE = "expense_save"
    const val CATEGORY = "expense_category"
    const val AMOUNT = "expense_amount"
    const val ODOMETER = "expense_odometer"
    const val DESCRIPTION = "expense_description"
    const val DELETE = "expense_delete"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onDeleted: (() -> Unit)? = null,
    editExpenseId: Long? = null,
    fixedCategory: String? = null,
) {
    val vm: ExpenseViewModel = viewModel(
        key = "expense-${editExpenseId ?: fixedCategory ?: "new"}",
        factory = ExpenseVmFactory(editExpenseId, fixedCategory),
    )
    val state by vm.form.collectAsStateWithLifecycle()
    vm.activeVehicle.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = state.editId != null

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ExpenseTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        when {
                            editing -> state.category.ifBlank { "Расход" }
                            fixedCategory != null -> fixedCategory
                            else -> "Новый расход"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
                actions = {
                    if (editing && onDeleted != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.testTag(ExpenseTags.DELETE),
                        ) { Text("Удалить") }
                    }
                },
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .rubberBandOverscroll()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassPanel {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.categoryLocked) {
                        Text(
                            state.category,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag(ExpenseTags.CATEGORY),
                        )
                    } else {
                        OutlinedTextField(
                            value = state.category,
                            onValueChange = vm::onCategory,
                            label = { Text("Категория") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ExpenseTags.CATEGORY),
                            singleLine = true,
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                    }
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = vm::onAmount,
                        label = { Text("Сумма, ₽") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ExpenseTags.AMOUNT),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = state.odometer,
                        onValueChange = vm::onOdometer,
                        label = { Text("Пробег, км (необязательно)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ExpenseTags.ODOMETER),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = vm::onDescription,
                        label = { Text("Комментарий") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ExpenseTags.DESCRIPTION),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = vm::save,
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ExpenseTags.SAVE),
                    ) {
                        Text(
                            when {
                                state.saving -> "Сохранение…"
                                editing -> "Сохранить изменения"
                                else -> "Сохранить"
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete && onDeleted != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить расход?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDeleted)
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

private class ExpenseVmFactory(
    private val editExpenseId: Long?,
    private val fixedCategory: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return ExpenseViewModel(
            AutologApp.instance.container.repository,
            editExpenseId,
            extras.createSavedStateHandle(),
            fixedCategory,
        ) as T
    }
}
