package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageFieldsActivity : AppCompatActivity() {

    private lateinit var recyclerFields: RecyclerView
    private lateinit var adapter: ManageFieldsAdapter
    private val farmsList = mutableListOf<FarmEntry>()
    private var langCode: String = TranslateLanguage.ENGLISH

    private val cropOptions = listOf(
        "Not Selected",
        "Rice", "Wheat",
        "Cotton", "Soybean",
        "Pulses", "Groundnut",
        "Sugarcane",
        "Maize",
        "Cashew", "Rubber",
        "Millets", "Bajra",
        "Tea", "Coffee",
        "Barley", "Tobacco",
        "Jute"
    )

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_fields)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            navigateToHome()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome()
            }
        })

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        recyclerFields = findViewById(R.id.recyclerFields)
        recyclerFields.layoutManager = LinearLayoutManager(this)
        adapter = ManageFieldsAdapter(farmsList)
        recyclerFields.adapter = adapter

        findViewById<MaterialButton>(R.id.btnAddField).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("OPEN_MAP_FRAGMENT", true)
            startActivity(intent)
            finish()
        }

        val btnHeaderEdit = findViewById<ImageView>(R.id.btnHeaderEdit)
        btnHeaderEdit.setOnClickListener {
            val isNowDeleteMode = adapter.toggleDeleteMode()

            if (isNowDeleteMode) {
                btnHeaderEdit.setImageResource(R.drawable.round_check_24)
            } else {
                btnHeaderEdit.setImageResource(R.drawable.round_edit_24)
            }
        }

        fetchFarmsFromDatabase()

        if (langCode != TranslateLanguage.ENGLISH) {
            findViewById<View>(android.R.id.content).post {
                TranslationHelper.translateViewHierarchy(findViewById(android.R.id.content), langCode) {}
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchFarmsFromDatabase() {
        Toast.makeText(this, t("Loading your fields..."), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    val fetchedFarms = SupabaseManager.client.postgrest["farms"]
                        .select {
                            filter { eq("farmer_id", user.id) }
                        }.decodeList<FarmEntry>()

                    withContext(Dispatchers.Main) {
                        farmsList.clear()
                        farmsList.addAll(fetchedFarms)
                        adapter.notifyDataSetChanged()

                        if (farmsList.isEmpty()) {
                            Toast.makeText(this@ManageFieldsActivity, t("No fields found. Add one!"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // NEW: Added onSuccess callback so the UI waits for the database!
    private fun updateFieldInDatabase(fieldId: String, newName: String, currentSoil: String, newCrop: String, position: Int, onSuccess: () -> Unit) {
        val cleanName = newName.trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user == null) return@launch

                val existingFarms = SupabaseManager.client.postgrest["farms"]
                    .select {
                        filter {
                            eq("farmer_id", user.id)
                            ilike("name", cleanName) // FIX: ilike ensures "My Farm" and "my farm" are caught as duplicates!
                            neq("id", fieldId)
                        }
                    }.decodeList<FarmEntry>()

                if (existingFarms.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ManageFieldsActivity, t("A farm with this name already exists!"), Toast.LENGTH_LONG).show()
                    }
                    return@launch // Reject the save and stop!
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ManageFieldsActivity, t("Saving changes..."), Toast.LENGTH_SHORT).show()
                }

                SupabaseManager.client.postgrest["farms"].update(
                    {
                        set("name", cleanName)
                        set("soil_type", currentSoil)
                        set("crop", newCrop)
                    }
                ) {
                    filter { eq("id", fieldId) }
                }

                withContext(Dispatchers.Main) {
                    val oldField = farmsList[position]
                    farmsList[position] = oldField.copy(name = cleanName, soil_type = currentSoil, crop = newCrop)

                    // SUCCESS! Tell the UI to close the edit mode.
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
                SupabaseManager.client.postgrest["farms"].delete { filter { eq("id", fieldId) } }
                withContext(Dispatchers.Main) {
                    farmsList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, farmsList.size)
                    Toast.makeText(this@ManageFieldsActivity, t("Field deleted"), Toast.LENGTH_SHORT).show()
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

            val layoutFieldSize: com.google.android.material.textfield.TextInputLayout = view.findViewById(R.id.layoutFieldSize)
            val layoutSoilType: com.google.android.material.textfield.TextInputLayout = view.findViewById(R.id.layoutSoilType)
            val layoutCrop: com.google.android.material.textfield.TextInputLayout = view.findViewById(R.id.layoutCrop)

            val spinnerCrop: AutoCompleteTextView = view.findViewById(R.id.spinnerCrop)

            val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
            val btnEditField: ImageView = view.findViewById(R.id.btnEditField)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_field_card, parent, false)
            return FieldViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: FieldViewHolder, position: Int) {
            val field = fields[position]

            val areaParts = field.land_size.split(" ")
            if (areaParts.size == 2) {
                holder.tvFieldSize.setText(d(areaParts[0]))
                holder.layoutFieldSize.suffixText = t(areaParts[1])
            } else {
                holder.tvFieldSize.setText(field.land_size)
                holder.layoutFieldSize.suffixText = null
            }

            val defaultName = "${t("Field")} ${d(position + 1)}"
            holder.etFieldName.setText(field.name ?: defaultName)
            holder.tvSoilType.setText(t(field.soil_type))
            holder.layoutFieldSize.hint = t("Field Size")
            holder.layoutSoilType.hint = t("Soil Type")
            holder.layoutCrop.hint = t("Crop")

            val translatedCropOptions = cropOptions.map { t(it) }
            holder.spinnerCrop.setAdapter(ArrayAdapter(this@ManageFieldsActivity, android.R.layout.simple_dropdown_item_1line, translatedCropOptions))
            holder.spinnerCrop.setText(t(field.crop ?: "Not Selected"), false)

            val isEditing = editingRows.contains(field.id)

            holder.etFieldName.filters = arrayOf(InputFilter { source, _, _, _, _, _ ->
                if (source.toString().matches(Regex("[a-zA-Z0-9 ]*"))) null else ""
            })

            if (isDeleteMode) {
                holder.btnEditField.setImageResource(android.R.drawable.ic_menu_delete)
                holder.btnEditField.setColorFilter(android.graphics.Color.RED)

                holder.etFieldName.isEnabled = false
                holder.etFieldName.setBackgroundResource(0)

                holder.layoutCrop.isEnabled = true
                holder.layoutCrop.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                holder.spinnerCrop.setAdapter(null)

                holder.btnEditField.setOnClickListener {
                    showDeleteConfirmationDialog(field, holder.adapterPosition)
                }
            } else {
                holder.btnEditField.clearColorFilter()

                if (isEditing) {
                    holder.btnEditField.setImageResource(R.drawable.round_check_24)

                    holder.etFieldName.isEnabled = true
                    holder.etFieldName.setBackgroundResource(androidx.appcompat.R.drawable.abc_edit_text_material)
                    holder.etFieldName.requestFocus()

                    holder.layoutCrop.isEnabled = true
                    holder.layoutCrop.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
                    holder.spinnerCrop.setAdapter(ArrayAdapter(this@ManageFieldsActivity, android.R.layout.simple_dropdown_item_1line, translatedCropOptions))

                    holder.btnEditField.setOnClickListener {
                        val selectedCropUi = holder.spinnerCrop.text.toString()
                        val selectedCrop = cropOptions.find { t(it) == selectedCropUi } ?: "Not Selected"

                        val newName = holder.etFieldName.text.toString().takeIf { it.isNotBlank() } ?: defaultName

                        updateFieldInDatabase(field.id!!, newName, field.soil_type, selectedCrop, holder.adapterPosition) {
                            editingRows.remove(field.id)
                        }
                    }
                } else {
                    holder.btnEditField.setImageResource(R.drawable.round_edit_24)

                    holder.etFieldName.isEnabled = false
                    holder.etFieldName.setBackgroundResource(0)

                    holder.layoutCrop.isEnabled = true
                    holder.layoutCrop.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
                    holder.spinnerCrop.setAdapter(null)

                    holder.btnEditField.setOnClickListener {
                        editingRows.add(field.id!!)
                        notifyItemChanged(holder.adapterPosition)
                    }
                }
            }

            holder.btnAction.text = t("GENERATE SOIL REPORT")
            holder.btnAction.setOnClickListener {
                Toast.makeText(this@ManageFieldsActivity, t("Generating report..."), Toast.LENGTH_SHORT).show()
            }
        }
        override fun getItemCount() = fields.size
    }
}