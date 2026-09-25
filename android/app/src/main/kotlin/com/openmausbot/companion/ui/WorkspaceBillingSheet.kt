package com.openmausbot.companion.ui

import com.openmausbot.companion.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.ModelBillingPrice
import com.openmausbot.companion.core.WorkspaceBillingConfig
import kotlinx.coroutines.launch

private data class BillingRow(
    val model: String = "",
    val input: String = "",
    val output: String = "",
    val cached: String = "",
)

/** Edits the same entitled host price list as Windows Settings → Usage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceBillingSheet(onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    var baseline by remember { mutableStateOf<WorkspaceBillingConfig?>(null) }
    var currency by remember { mutableStateOf("USD") }
    var rows by remember { mutableStateOf(listOf(BillingRow())) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val status = session.configStatus() ?: throw IllegalStateException("Could not load the computer's settings.")
            if (status.edition?.features?.contains("billing") != true) {
                throw IllegalStateException("This computer does not have model price settings.")
            }
            val current = status.billing ?: WorkspaceBillingConfig()
            baseline = current
            currency = current.currency
            rows = current.prices.map { (model, price) ->
                BillingRow(model, price.inputPerMillion.toString(), price.outputPerMillion.toString(),
                    price.cachedInputPerMillion?.toString().orEmpty())
            }.ifEmpty { listOf(BillingRow()) }
        } catch (failure: Exception) { error = failure.message ?: "Could not load model prices." }
        finally { loading = false }
    }

    fun update(index: Int, change: (BillingRow) -> BillingRow) {
        rows = rows.mapIndexed { position, row -> if (position == index) change(row) else row }
        error = null
    }

    fun save() {
        val normalizedCurrency = currency.trim().uppercase()
        if (!normalizedCurrency.matches(Regex("[A-Z]{3}"))) {
            error = "Enter a three-letter currency code, such as USD."
            return
        }
        val prices = linkedMapOf<String, ModelBillingPrice>()
        for (row in rows) {
            if (row.model.isBlank() && row.input.isBlank() && row.output.isBlank() && row.cached.isBlank()) continue
            val model = row.model.trim()
            if (model.isEmpty() || model.length > 160 || model in prices) {
                error = "Each price row needs a unique model name of at most 160 characters."
                return
            }
            val input = row.input.toDoubleOrNull()
            val output = row.output.toDoubleOrNull()
            val cached = if (row.cached.isBlank()) null else row.cached.toDoubleOrNull()
            if (input == null || output == null || (row.cached.isNotBlank() && cached == null) ||
                listOfNotNull(input, output, cached).any { !it.isFinite() || it !in 0.0..1_000_000.0 }) {
                error = "Enter valid nonnegative input and output rates for $model; cached input is optional."
                return
            }
            prices[model] = ModelBillingPrice(input, output, cached)
        }
        val expected = baseline ?: return
        val requested = WorkspaceBillingConfig(normalizedCurrency, prices)
        busy = true
        error = null
        scope.launch {
            try {
                val latest = session.configStatus() ?: throw IllegalStateException("Could not recheck the computer's prices.")
                if (latest.edition?.features?.contains("billing") != true ||
                    (latest.billing ?: WorkspaceBillingConfig()) != expected) {
                    throw IllegalStateException("Model prices changed on the computer. Reopen this screen before saving.")
                }
                val saved = session.updateWorkspaceBilling(requested)
                if (saved.billing != requested) throw IllegalStateException("The computer did not confirm the saved model prices.")
                onDismiss()
            } catch (failure: Exception) { error = failure.message ?: "Could not save model prices." }
            finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ui_model_prices_5f9f78a), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ui_prices_per_million_tokens_used_by_the_pair_7286bc9),
                style = MaterialTheme.typography.bodySmall)
            if (loading || busy) CircularProgressIndicator()
            if (!loading && baseline != null) {
                OutlinedTextField(currency, onValueChange = { currency = it.take(3); error = null },
                    label = { Text(stringResource(R.string.ui_currency_e070de2)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                rows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(row.model, onValueChange = { value -> update(index) { it.copy(model = value.take(160)) } },
                            label = { Text(stringResource(R.string.ui_model_id_or_default_d70af9a)) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(row.input, onValueChange = { value -> update(index) { it.copy(input = value) } },
                                label = { Text(stringResource(R.string.ui_input_b568d47)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                enabled = !busy, modifier = Modifier.weight(1f))
                            OutlinedTextField(row.output, onValueChange = { value -> update(index) { it.copy(output = value) } },
                                label = { Text(stringResource(R.string.ui_output_4bed336)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                enabled = !busy, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(row.cached, onValueChange = { value -> update(index) { it.copy(cached = value) } },
                            label = { Text(stringResource(R.string.ui_cached_input_optional_c4f63ef)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !busy, modifier = Modifier.fillMaxWidth())
                        TextButton(enabled = !busy, onClick = { rows = rows.filterIndexed { position, _ -> position != index }; error = null }) {
                            Text(stringResource(R.string.ui_remove_row_bd7f4b4), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TextButton(enabled = !busy, onClick = { rows = rows + BillingRow() }) { Text(stringResource(R.string.ui_add_model_b6a86ca)) }
                TextButton(enabled = !busy, onClick = ::save) { Text(stringResource(R.string.ui_save_prices_e452a2f)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ui_done_e9b450d)) }
        }
    }
}
