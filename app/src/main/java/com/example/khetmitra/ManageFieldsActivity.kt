package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ManageFieldsActivity : AppCompatActivity() {
    private lateinit var recyclerFields: RecyclerView
    private lateinit var adapter: ManageFieldsAdapter
    private val farmsList = mutableListOf<FarmEntry>()
    private var langCode: String = TranslateLanguage.ENGLISH
    private lateinit var btnHeaderEdit: MaterialCardView
    private lateinit var ivHeaderEditIcon: ImageView
    private var cropOptions: List<String> = listOf("Not Selected")
    private var translatedCropOptionsList: List<String> = emptyList()
    private val translatedFarmNames = mutableMapOf<String, String>()

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_fields)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { navigateToHome() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateToHome() }
        })

        recyclerFields = findViewById(R.id.recyclerFields)
        recyclerFields.layoutManager = LinearLayoutManager(this)
        adapter = ManageFieldsAdapter(farmsList)
        recyclerFields.adapter = adapter
        findViewById<MaterialCardView>(R.id.btnAddField).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("OPEN_MAP_FRAGMENT", true)
            )
            finish()
        }
        btnHeaderEdit  = findViewById(R.id.btnHeaderEdit)
        ivHeaderEditIcon = btnHeaderEdit.findViewById(R.id.ivEditFieldIcon)
        btnHeaderEdit.setOnClickListener {
            val isNowDeleteMode = adapter.toggleDeleteMode()
            ivHeaderEditIcon.setImageResource(
                if (isNowDeleteMode) R.drawable.round_check_24
                else R.drawable.round_delete_24
            )
            btnHeaderEdit.setCardBackgroundColor(
                if (isNowDeleteMode) "#B71C1C".toColorInt()
                else "#2D6A4F".toColorInt()
            )
        }
        loadCropsFromDatabase()
        if (langCode != TranslateLanguage.ENGLISH) {
            findViewById<View>(android.R.id.content).post {
                TranslationHelper.translateViewHierarchy(
                    findViewById(android.R.id.content), langCode
                ) {}
            }
        }
    }

    private fun navigateToHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private suspend fun translateDynamicText(text: String): String =
        suspendCancellableCoroutine { continuation ->
            if (langCode == TranslateLanguage.ENGLISH) {
                continuation.resumeWith(Result.success(text))
                return@suspendCancellableCoroutine
            }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(langCode)
                .build()
            val client = Translation.getClient(options)

            client.downloadModelIfNeeded().addOnSuccessListener {
                client.translate(text)
                    .addOnSuccessListener { result -> continuation.resumeWith(Result.success(result)) }
                    .addOnFailureListener { continuation.resumeWith(Result.success(text)) }
            }.addOnFailureListener {
                continuation.resumeWith(Result.success(text))
            }
        }

    private fun loadCropsFromDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fetchedCrops = SupabaseManager.client
                    .postgrest["crops"]
                    .select {
                        filter { eq("status", 1) }
                        filter { eq("crop_group_id", 1) }
                        order("crop_name", Order.ASCENDING)
                    }
                    .decodeList<CropRow>()
                val englishCrops: List<String> = listOf("Not Selected") + fetchedCrops.map { it.cropName }
                val translatedCrops = englishCrops.map { crop: String ->
                    async {
                        if (crop == "Not Selected") t(crop) else translateDynamicText(crop)
                    }
                }.awaitAll()
                withContext(Dispatchers.Main) {
                    cropOptions = englishCrops
                    translatedCropOptionsList = translatedCrops
                    fetchFarmsFromDatabase()
                }
            } catch (e: Exception) {
                Log.e("ManageFields", "loadCrops failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, "Could not load crops: ${e.message}", Toast.LENGTH_SHORT).show()
                    fetchFarmsFromDatabase()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchFarmsFromDatabase() {
        Toast.makeText(this, t("Loading your fields..."), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull() ?: return@launch
                val fetchedFarms = SupabaseManager.client.postgrest["farms"]
                    .select { filter { eq("farmer_id", user.id) } }
                    .decodeList<FarmEntry>()

                fetchedFarms.map { farm ->
                    async {
                        val rawName = farm.name ?: ""
                        val farmId = farm.id ?: ""

                        if (rawName.matches(Regex("Farm \\d+"))) {
                            val num = rawName.substringAfter("Farm ")
                            translatedFarmNames[farmId] = "${t("Farm")} ${d(num)}"
                        } else {
                            translatedFarmNames[farmId] = translateDynamicText(rawName)
                        }
                    }
                }.awaitAll()

                withContext(Dispatchers.Main) {
                    farmsList.clear()
                    farmsList.addAll(fetchedFarms)
                    adapter.notifyDataSetChanged()
                    if (farmsList.isEmpty())
                        Toast.makeText(this@ManageFieldsActivity, t("No fields found. Add one!"), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateFieldInDatabase(
        fieldId: String, newName: String, currentSoil: String,
        newCrop: String, position: Int, onSuccess: () -> Unit
    ) {
        val cleanName = newName.trim()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull() ?: return@launch
                val existing = SupabaseManager.client.postgrest["farms"]
                    .select { filter { eq("farmer_id", user.id); ilike("name", cleanName); neq("id", fieldId) } }
                    .decodeList<FarmEntry>()
                if (existing.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ManageFieldsActivity, t("A farm with this name already exists!"), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, t("Saving..."), Toast.LENGTH_SHORT).show()
                }
                SupabaseManager.client.postgrest["farms"].update({
                    set("name", cleanName)
                    set("soil_type", currentSoil)
                    set("crop", newCrop)
                }) { filter { eq("id", fieldId) } }
                withContext(Dispatchers.Main) {
                    farmsList[position] = farmsList[position].copy(name = cleanName, soil_type = currentSoil, crop = newCrop)
                    onSuccess()
                    adapter.notifyItemChanged(position)
                    Toast.makeText(this@ManageFieldsActivity, t("Changes saved!"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteFieldFromDatabase(fieldId: String, position: Int) {
        Toast.makeText(this, t("Deleting field..."), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val deletedRows = SupabaseManager.client.postgrest["farms"].delete {
                    select()
                    filter { eq("id", fieldId) }
                }.decodeList<FarmEntry>()

                withContext(Dispatchers.Main) {
                    if (deletedRows.isNotEmpty()) {
                        farmsList.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        adapter.notifyItemRangeChanged(position, farmsList.size)
                        Toast.makeText(this@ManageFieldsActivity, t("Field deleted"), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ManageFieldsActivity, t("Failed: Database blocked deletion. Check RLS policies."), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(field: FarmEntry, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(t("Delete Field"))
            .setMessage(t("Are you sure you want to delete this field? This cannot be undone."))
            .setPositiveButton(t("Delete")) { dialog, _ ->
                if (field.id != null) deleteFieldFromDatabase(field.id, position)
                dialog.dismiss()
            }
            .setNegativeButton(t("Cancel")) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun translateToEnglish(text: String, sourceLang: String, onResult: (String) -> Unit) {
        if (sourceLang == TranslateLanguage.ENGLISH) { onResult(text); return }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        onResult(translated.split(" ").joinToString(" ") {
                            it.replaceFirstChar { c -> c.uppercase() }
                        })
                        translator.close()
                    }
                    .addOnFailureListener { onResult(text); translator.close() }
            }
            .addOnFailureListener { onResult(text); translator.close() }
    }

    inner class ManageFieldsAdapter(private val fields: List<FarmEntry>) :
        RecyclerView.Adapter<ManageFieldsAdapter.FieldViewHolder>() {
        private var isDeleteMode = false
        private val editingRows = mutableSetOf<String>()
        @SuppressLint("NotifyDataSetChanged")
        fun toggleDeleteMode(): Boolean {
            isDeleteMode = !isDeleteMode
            editingRows.clear()
            notifyDataSetChanged()
            return isDeleteMode
        }

        inner class FieldViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val etFieldName: EditText = view.findViewById(R.id.etFieldName)
            val tvFieldSize: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.tvFieldSize)
            val tvSoilType: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.tvSoilType)
            val layoutFieldSize: TextInputLayout = view.findViewById(R.id.layoutFieldSize)
            val layoutSoilType: TextInputLayout = view.findViewById(R.id.layoutSoilType)
            val layoutCrop: TextInputLayout = view.findViewById(R.id.layoutCrop)
            val spinnerCrop: AutoCompleteTextView = view.findViewById(R.id.spinnerCrop)
            val btnEditField: MaterialCardView = view.findViewById(R.id.btnEditField)
            val ivEditIcon: ImageView = btnEditField.findViewById(R.id.ivEditFieldIcon)
            val btnAction: MaterialCardView = view.findViewById(R.id.btnAction)
            val tvBtnActionLabel: TextView = btnAction.findViewById(R.id.tvBtnActionLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_field_card, parent, false)
            return FieldViewHolder(view)
        }

        @Suppress("DEPRECATION")
        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: FieldViewHolder, position: Int) {
            val field = fields[position]
            val cropIndex = cropOptions.indexOf(field.crop ?: "Not Selected")
            val displayCrop = if (cropIndex != -1 && translatedCropOptionsList.isNotEmpty()) {
                translatedCropOptionsList[cropIndex]
            } else {
                field.crop ?: t("Not Selected")
            }
            // Field size
            val areaParts = field.land_size.split(" ")
            if (areaParts.size == 2) {
                holder.tvFieldSize.setText(d(areaParts[0]))
                holder.layoutFieldSize.suffixText = t(areaParts[1])
            } else {
                holder.tvFieldSize.setText(field.land_size)
                holder.layoutFieldSize.suffixText = null
            }
            // Field name
            val translatedName = translatedFarmNames[field.id] ?: field.name ?: "Unknown"
            holder.tvSoilType.setText(t(field.soil_type))
            holder.layoutFieldSize.hint = t("Field Size")
            holder.layoutSoilType.hint = t("Soil Type")
            holder.layoutCrop.hint = t("Crop")
            val isEditing = editingRows.contains(field.id)
            holder.etFieldName.filters = arrayOf()
            val greenColor = "#52B788".toColorInt()
            val defaultGrey = "#E8EDE0".toColorInt()
            val activeTextColor = "#1A3C2E".toColorInt()
            val disabledTextColor = "#A0A0A0".toColorInt()
            val isActive = isEditing && !isDeleteMode
            val currentBoxColor = if (isActive) greenColor else defaultGrey
            val currentTextColor = if (isActive) activeTextColor else disabledTextColor
            val strokeStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf()
                ),
                intArrayOf(greenColor, defaultGrey, currentBoxColor)
            )
            listOf(holder.layoutFieldSize, holder.layoutSoilType, holder.layoutCrop).forEach { layout ->
                layout.setBoxStrokeColorStateList(strokeStateList)
                layout.boxStrokeColor = currentBoxColor
            }
            holder.tvFieldSize.setTextColor(currentTextColor)
            holder.tvSoilType.setTextColor(currentTextColor)
            holder.spinnerCrop.setTextColor(currentTextColor)
            holder.etFieldName.setTextColor(currentTextColor)
            if (isDeleteMode) {
                holder.ivEditIcon.setImageResource(android.R.drawable.ic_menu_delete)
                holder.ivEditIcon.setColorFilter(Color.RED)
                holder.etFieldName.isEnabled = false
                holder.etFieldName.setBackgroundResource(0)
                holder.layoutCrop.isEnabled = false
                holder.layoutCrop.endIconMode = TextInputLayout.END_ICON_NONE
                holder.spinnerCrop.setAdapter(null)
                holder.spinnerCrop.setText(displayCrop, false)
                holder.btnEditField.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        showDeleteConfirmationDialog(fields[pos], pos)
                    }
                }
            } else {
                holder.ivEditIcon.clearColorFilter()
                if (isEditing) {
                    holder.ivEditIcon.setImageResource(R.drawable.round_check_24)
                    holder.etFieldName.setText(translatedName)
                    holder.etFieldName.isEnabled = true
                    val typedValue = android.util.TypedValue()
                    holder.itemView.context.theme.resolveAttribute(android.R.attr.editTextBackground, typedValue, true)
                    holder.etFieldName.setBackgroundResource(typedValue.resourceId)
                    holder.etFieldName.requestFocus()
                    holder.layoutCrop.isEnabled = true
                    holder.layoutCrop.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
                    val cropAdapter = ArrayAdapter(
                        this@ManageFieldsActivity,
                        R.layout.custom_spinner_dropdown_item,
                        translatedCropOptionsList
                    )
                    holder.spinnerCrop.setAdapter(cropAdapter)
                    holder.spinnerCrop.setDropDownBackgroundResource(R.drawable.bg_spinner_dropdown)
                    holder.spinnerCrop.setText(displayCrop, false)
                    holder.btnEditField.setOnClickListener {
                        val selectedCropUi = holder.spinnerCrop.text.toString()
                        val matchIndex = translatedCropOptionsList.indexOf(selectedCropUi)
                        val selectedCrop = if (matchIndex != -1) cropOptions[matchIndex] else "Not Selected"
                        val newNameNative = holder.etFieldName.text.toString().takeIf { it.isNotBlank() } ?: translatedName
                        Toast.makeText(this@ManageFieldsActivity, t("Saving..."), Toast.LENGTH_SHORT).show()
                        translatedFarmNames[field.id!!] = newNameNative
                        translateToEnglish(newNameNative, langCode) { englishName ->
                            updateFieldInDatabase(field.id, englishName, field.soil_type, selectedCrop, holder.adapterPosition) {
                                editingRows.remove(field.id)
                            }
                        }
                    }
                } else {
                    holder.ivEditIcon.setImageResource(R.drawable.round_edit_24)
                    holder.etFieldName.setText(translatedName)
                    holder.etFieldName.isEnabled = false
                    holder.etFieldName.setBackgroundResource(0)
                    holder.layoutCrop.isEnabled = false
                    holder.layoutCrop.endIconMode = TextInputLayout.END_ICON_NONE
                    holder.spinnerCrop.setAdapter(null)
                    holder.spinnerCrop.setText(displayCrop, false)
                    holder.btnEditField.setOnClickListener {
                        editingRows.add(field.id!!)
                        notifyItemChanged(holder.adapterPosition)
                    }
                }
            }
            holder.tvBtnActionLabel.text = t("VIEW SOIL REPORT")
            holder.btnAction.setOnClickListener {
                startActivity(
                    Intent(this@ManageFieldsActivity, SoilReportActivity::class.java).apply {
                        putExtra("FARM_NAME",        field.name ?: "My Farm")
                        putExtra("FARM_SIZE",        field.land_size)
                        putExtra("FARM_COORDINATES", field.coordinates)
                        putExtra("POLYGON_ID",       field.polygon_id)
                    }
                )
            }
        }
        override fun getItemCount() = fields.size
    }
}