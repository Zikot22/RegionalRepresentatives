package com.example.regionalrepresentatives
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
private const val BASE_URL = "http://127.0.0.1:8000"
private val client = OkHttpClient()
private val jsonType = "application/json; charset=utf-8".toMediaType()
private suspend fun api(
    path: String,
    method: String = "GET",
    body: JSONObject? = null
): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(BASE_URL + path)
    when (method) {
        "POST" -> request.post(
            (body ?: JSONObject()).toString().toRequestBody(jsonType)
        )
        "PUT" -> request.put(
            (body ?: JSONObject()).toString().toRequestBody(jsonType)
        )
        "DELETE" -> request.delete()
        else -> request.get()
    }
    client.newCall(request.build()).execute().use { response ->
        val result = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = try {
                JSONObject(result).optString("detail", result)
            } catch (_: Exception) {
                result
            }
            throw IOException(detail.ifBlank { "Ошибка HTTP ${response.code}"
            })
        }
        result
    }
}
private data class Employee(
    val id: Int = 0,
    val last: String = "",
    val first: String = "",
    val second: String = "",
    val title: String = "",
    val birthday: String = "",
    val address: String = "",
    val city: String = "",
    val region: String = "",
    val phone: String = "",
    val email: String = ""
) {
    val fullName: String
        get() = listOf(last, first, second)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    fun toJson() = JSONObject().apply {
        put("LastName", last)
        put("FirstName", first)
        put("SecondName", second)
        put("Title", title)
        put("BirthDay", birthday)
        put("Address", address)
        put("City", city)
        put("Region", region)
        put("Phone", phone)
        put("Email", email)
    }
    companion object {
        fun from(o: JSONObject) = Employee(
            id = o.optInt("Id"),
            last = o.optString("LastName"),
            first = o.optString("FirstName"),
            second = o.optString("SecondName"),
            title = o.optString("Title"),
            birthday = o.optString("BirthDay"),
            address = o.optString("Address"),
            city = o.optString("City"),
            region = o.optString("Region"),
            phone = o.optString("Phone"),
            email = o.optString("Email")
        )
    }
}
private data class Region(val id: Int, val name: String)
private data class Territory(
    val id: Int,
    val regionId: Int,
    val name: String,
    val regionName: String
)

private data class Assignment(
    val employeeId: Int,
    val territoryId: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RepresentativesScreen()
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepresentativesScreen() {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var employees by remember { mutableStateOf(listOf<Employee>()) }
    var regions by remember { mutableStateOf(listOf<Region>()) }
    var territories by remember { mutableStateOf(listOf<Territory>()) }
    var assignments by remember { mutableStateOf(listOf<Assignment>()) }
    var assignmentDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Загрузка данных...") }
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }
    var employeeDialog by remember { mutableStateOf(false) }
    var regionDialog by remember { mutableStateOf(false) }
    var territoryDialog by remember { mutableStateOf(false) }
    fun refresh() {
        scope.launch {
            loading = true
            try {
                val e = JSONArray(api("/employees"))
                employees = (0 until e.length()).map {
                    Employee.from(e.getJSONObject(it))
                }
                val r = JSONArray(api("/regions"))
                regions = (0 until r.length()).map {
                    val o = r.getJSONObject(it)
                    Region(o.optInt("Id"), o.optString("RegionDiscription"))
                }
                val t = JSONArray(api("/territories"))
                territories = (0 until t.length()).map {
                    val o = t.getJSONObject(it)
                    Territory(
                        o.optInt("Id"),
                        o.optInt("RegionId"),
                        o.optString("Discripsion"),
                        o.optString("RegionName")
                    )
                }
                var a = JSONArray(api("/employee-territories"))
                assignments = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Assignment(
                        employeeId = o.optInt("EmployeeId"),
                        territoryId = o.optInt("TerritoryId")
                    )
                }
                message = "Данные загружены"
            } catch (e: Exception) {
                message = "Ошибка подключения: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Региональные представители",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (tab) {
                    0 -> {
                        editingEmployee = null
                        employeeDialog = true
                    }
                    1 -> regionDialog = true
                    2 -> territoryDialog = true
                    3 -> assignmentDialog = true
                }
            }) {
                Text("+")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("С") },
                    label = { Text("Сотрудники") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("Р") },
                    label = { Text("Регионы") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("Т") },
                    label = { Text("Территории") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Text("Н") },
                    label = { Text("Назначения") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { refresh() }) {
                    Text("Обновить")
                }
            }
            when (tab) {
                0 -> {
                    if (employees.isEmpty() && !loading) {
                        EmptyMessage(
                            "Сотрудников пока нет",
                            "Нажми +, чтобы добавить первого сотрудника."
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(employees, key = { it.id }) { employee ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        employee.fullName,
                                        style =
                                            MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (employee.title.isNotBlank()) {
                                        Text(employee.title)
                                    }
                                    val location = listOf(
                                        employee.city,
                                        employee.region
                                    ).filter { it.isNotBlank() }
                                        .joinToString(", ")
                                    if (location.isNotBlank()) Text(location)
                                    if (employee.phone.isNotBlank()) {
                                        Text("Телефон: ${employee.phone}")
                                    }
                                    if (employee.email.isNotBlank()) {
                                        Text(employee.email)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.End
                                    ) {
                                        TextButton(onClick = {
                                            editingEmployee = employee
                                            employeeDialog = true
                                        }) {
                                            Text("Изменить")
                                        }
                                        TextButton(onClick = {
                                            scope.launch {
                                                try {
                                                    api(
                                                        "/employees/${employee.id}",
                                                        "DELETE"
                                                    )
                                                    message = "Сотрудник удалён"
                                                    refresh()
                                                } catch (e: Exception) {
                                                    message = e.message ?: "Ошибка"
                                                }
                                            }
                                        }) {
                                            Text(
                                                "Удалить",
                                                color =
                                                    MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (regions.isEmpty() && !loading) {
                        EmptyMessage(
                            "Регионов пока нет",
                            "Нажми +, чтобы добавить регион."
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(regions, key = { it.id }) { region ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Text(
                                        region.name,
                                        Modifier.weight(1f),
                                        style =
                                            MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                api("/regions/${region.id}",
                                                    "DELETE")
                                                message = "Регион удалён"
                                                refresh()
                                            } catch (e: Exception) {
                                                message = e.message ?:
                                                        "Ошибка"
                                            }
                                        }
                                    }) {
                                        Text(
                                            "Удалить",
                                            color =
                                                MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (territories.isEmpty() && !loading) {
                        EmptyMessage(
                            "Территорий пока нет",
                            "Добавь регион, затем территорию или город."
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(territories, key = { it.id }) { territory ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            territory.name,
                                            style =
                                                MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            territory.regionName,
                                            style =
                                                MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                api(
                                                    "/territories/${territory.id}",
                                                    "DELETE"
                                                )
                                                message = "Территория удалена"
                                                refresh()
                                            } catch (e: Exception) {
                                                message = e.message ?: "Ошибка"
                                            }
                                        }
                                    }) {
                                        Text(
                                            "Удалить",
                                            color =
                                                MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    if (assignments.isEmpty() && !loading) {
                        EmptyMessage(
                            "Назначений пока нет",
                            "Нажми +, чтобы назначить сотрудника на территорию."
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(
                            assignments,
                            key = { "${it.employeeId}_${it.territoryId}" }
                        ) { assignment ->
                            val employee = employees.find {
                                it.id == assignment.employeeId
                            }
                            val territory = territories.find {
                                it.id == assignment.territoryId
                            }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        employee?.fullName
                                            ?: "Сотрудник №${assignment.employeeId}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        territory?.name
                                            ?: "Территория №${assignment.territoryId}"
                                    )
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    api(
                                                        "/employee-territories/${assignment.employeeId}/${assignment.territoryId}",
                                                        "DELETE"
                                                    )
                                                    message = "Назначение удалено"
                                                    refresh()
                                                } catch (e: Exception) {
                                                    message = e.message ?: "Ошибка удаления"
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            "Удалить назначение",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
            }
        }
    }
    if (employeeDialog) {
        EmployeeEditor(
            initial = editingEmployee,
            onDismiss = { employeeDialog = false },
            onSave = { employee ->
                scope.launch {
                    try {
                        val current = editingEmployee
                        if (current == null) {
                            api("/employees", "POST", employee.toJson())
                        } else {
                            api(
                                "/employees/${current.id}",
                                "PUT",
                                employee.toJson()
                            )
                        }
                        employeeDialog = false
                        message = "Сохранено"
                        refresh()
                    } catch (e: Exception) {
                        message = e.message ?: "Ошибка сохранения"
                    }
                }
            }
        )
    }
    if (regionDialog) {
        NameEditor(
            title = "Новый регион",
            label = "Название региона",
            onDismiss = { regionDialog = false },
            onSave = { name ->
                scope.launch {
                    try {
                        api(
                            "/regions",
                            "POST",
                            JSONObject().put("RegionDiscription", name)
                        )
                        regionDialog = false
                        message = "Регион добавлен"
                        refresh()
                    } catch (e: Exception) {
                        message = e.message ?: "Ошибка"
                    }
                }
            }
        )
    }
    if (territoryDialog) {
        TerritoryEditor(
            regions = regions,
            onDismiss = { territoryDialog = false },
            onSave = { regionId, name ->
                scope.launch {
                    try {
                        api(
                            "/territories",
                            "POST",
                            JSONObject()
                                .put("RegionId", regionId)
                                .put("Discripsion", name)
                        )
                        territoryDialog = false
                        message = "Территория добавлена"
                        refresh()
                    } catch (e: Exception) {
                        message = e.message ?: "Ошибка"
                    }
                }
            }
        )
    }
    if (assignmentDialog) {
        AssignmentEditor(
            employees = employees,
            territories = territories,
            onDismiss = {
                assignmentDialog = false
            },
            onSave = { employeeId, territoryId ->
                scope.launch {
                    try {
                        val body = JSONObject()
                            .put("EmployeeId", employeeId)
                            .put("TerritoryId", territoryId)
                        api("/employee-territories", "POST", body)
                        assignmentDialog = false
                        message = "Назначение создано"
                        refresh()
                    } catch (e: Exception) {
                        message = e.message ?: "Ошибка создания назначения"
                    }
                }
            }
        )
    }
}
@Composable
private fun EmptyMessage(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
private fun EmployeeEditor(
    initial: Employee?,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit
) {
    val labels = listOf(
        "Фамилия", "Имя", "Отчество", "Должность",
        "Дата рождения (ГГГГ-ММ-ДД)", "Адрес",
        "Город", "Регион", "Телефон", "Email"
    )
    val initialValues = listOf(
        initial?.last, initial?.first, initial?.second, initial?.title,
        initial?.birthday, initial?.address, initial?.city,
        initial?.region, initial?.phone, initial?.email
    ).map { it ?: "" }
    val values = remember(initial?.id) {
        mutableStateListOf<String>().apply { addAll(initialValues) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Новый сотрудник" else
                "Редактирование")
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { values[index] = it },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (values[0].isBlank() || values[1].isBlank())
                    return@TextButton
                onSave(
                    Employee(
                        id = initial?.id ?: 0,
                        last = values[0].trim(),
                        first = values[1].trim(),
                        second = values[2].trim(),
                        title = values[3].trim(),
                        birthday = values[4].trim(),
                        address = values[5].trim(),
                        city = values[6].trim(),
                        region = values[7].trim(),
                        phone = values[8].trim(),
                        email = values[9].trim()
                    )
                )
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
@Composable
private fun NameEditor(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.isNotBlank()) onSave(value.trim())
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
@Composable
private fun TerritoryEditor(
    regions: List<Region>,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var selectedId by remember(regions) {
        mutableIntStateOf(regions.firstOrNull()?.id ?: -1)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая территория") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (regions.isEmpty()) {
                    Text("Сначала добавь регион во вкладке «Регионы».")
                } else {
                    Text("Выбери регион")
                    regions.forEach { region ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedId == region.id,
                                onClick = { selectedId = region.id }
                            )
                            Text(
                                region.name,
                                Modifier.clickable { selectedId = region.id }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Город или территория") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = regions.isNotEmpty(),
                onClick = {
                    if (value.isNotBlank()) onSave(selectedId, value.trim())
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
@Composable
private fun AssignmentEditor(
    employees: List<Employee>,
    territories: List<Territory>,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var employeeId by remember(employees) {
        mutableIntStateOf(employees.firstOrNull()?.id ?: -1)
    }
    var territoryId by remember(territories) {
        mutableIntStateOf(territories.firstOrNull()?.id ?: -1)
    }
    val canSave = employees.isNotEmpty() &&
            territories.isNotEmpty() &&
            employeeId > 0 &&
            territoryId > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое назначение") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Сотрудник",
                    style = MaterialTheme.typography.titleSmall
                )
                if (employees.isEmpty()) {
                    Text("Сначала добавь сотрудника.")
                } else {
                    employees.forEach { employee ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = employeeId == employee.id,
                                onClick = { employeeId = employee.id }
                            )
                            Text(
                                employee.fullName,
                                modifier = Modifier.clickable {
                                    employeeId = employee.id
                                }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "Территория",
                    style = MaterialTheme.typography.titleSmall
                )
            if (territories.isEmpty()) {
                Text("Сначала добавь территорию.")
            } else {
                territories.forEach { territory ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = territoryId == territory.id,
                            onClick = { territoryId = territory.id }
                        )
                        Column(
                            modifier = Modifier.clickable {
                                territoryId = territory.id
                            }
                        ) {
                            Text(territory.name)
                            Text(
                                territory.regionName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(employeeId, territoryId)
                }
            ) {
                Text("Назначить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}