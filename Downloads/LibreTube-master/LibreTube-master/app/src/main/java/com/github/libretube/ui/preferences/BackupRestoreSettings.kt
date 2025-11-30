package com.github.libretube.ui.preferences

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.github.libretube.R
import com.github.libretube.constants.IntentData
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.databinding.DialogImportExportFormatChooserBinding
import com.github.libretube.enums.ImportFormat
import com.github.libretube.extensions.ProgressCallback
import com.github.libretube.helpers.BackupHelper
import com.github.libretube.helpers.ImportHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.obj.BackupFile
import com.github.libretube.ui.base.BasePreferenceFragment
import com.github.libretube.ui.dialogs.BackupDialog
import com.github.libretube.ui.dialogs.BackupDialog.Companion.BACKUP_DIALOG_REQUEST_KEY
import com.github.libretube.ui.dialogs.RequireRestartDialog
import com.github.libretube.util.TextUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import android.app.Dialog


class BackupRestoreSettings : BasePreferenceFragment() {
    private var backupFile = BackupFile()
    private var importFormat: ImportFormat = ImportFormat.NEWPIPE

    override val titleResourceId: Int = R.string.backup_restore

    private var progressDialog: Dialog? = null
    private var progressBar: ProgressBar? = null
    private var progressMessage: TextView? = null

    private val progressCallback: ProgressCallback = { progress, message ->
        withContext(Dispatchers.Main) {
            updateProgressDialog(progress, message)
        }
    }

    private fun showProgressDialog(title: String, initialMessage: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_progress, null)
        progressBar = dialogView.findViewById(R.id.progress_bar)
        progressMessage = dialogView.findViewById(R.id.progress_message)

        progressMessage?.text = initialMessage
        progressBar?.progress = 0

        progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun updateProgressDialog(progress: Int, message: String) {
        progressBar?.progress = progress
        progressMessage?.text = message
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressMessage = null
    }

    // backup and restore database
    private val getBackupFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            showProgressDialog(getString(R.string.backup_restore), getString(R.string.restore_start))
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    BackupHelper.restoreAdvancedBackup(
                        requireContext().applicationContext,
                        uri,
                        progressCallback
                    )
                    withContext(Dispatchers.Main) {
                        // could fail if fragment is already closed
                        runCatching {
                            RequireRestartDialog().show(childFragmentManager, this::class.java.name)
                        }
                    }
                } finally {
                    dismissProgressDialog()
                }
            }
        }
    private val createBackupFile = registerForActivityResult(CreateDocument(FILETYPE_ANY)) { uri ->
        if (uri == null) return@registerForActivityResult
        showProgressDialog(getString(R.string.backup_restore), getString(R.string.backup_creation_start))
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                BackupHelper.createAdvancedBackup(
                    requireContext().applicationContext,
                    uri,
                    backupFile,
                    progressCallback
                )
            } finally {
                dismissProgressDialog()
            }
        }
    }

    /**
     * result listeners for importing and exporting subscriptions
     */
    private val getSubscriptionsFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        showProgressDialog(getString(R.string.import_subscriptions_from), getString(R.string.import_subscriptions_start))
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ImportHelper.importSubscriptions(
                    requireContext().applicationContext,
                    uri,
                    importFormat,
                    progressCallback
                )
            } finally {
                dismissProgressDialog()
            }
        }
    }

    private val createSubscriptionsFile =
        registerForActivityResult(CreateDocument(FILETYPE_ANY)) { uri ->
            if (uri == null) return@registerForActivityResult
            showProgressDialog(getString(R.string.export_subscriptions_to), getString(R.string.export_subscriptions_start))
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    ImportHelper.exportSubscriptions(
                        requireContext().applicationContext,
                        uri,
                        importFormat,
                        progressCallback
                    )
                } finally {
                    dismissProgressDialog()
                }
            }
        }


    // result listeners for importing and exporting playlists
    private val getPlaylistsFile =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
            if (files.isEmpty()) return@registerForActivityResult
            showProgressDialog(getString(R.string.import_playlists_from), getString(R.string.import_playlists_start))
            // This assumes only one file is selected, or takes the first one. If multiple, it needs a loop.
            // For simplicity, let's assume one file for now or process files sequentially with progress.
            // If multiple files are expected to be imported with a single progress bar, the ImportHelper
            // would need to handle overall progress for all files.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Assuming we only care about the first file selected for a single progress update
                    // or modifying ImportHelper to handle multiple Uris if needed.
                    // For now, let's pass the first URI. The current ImportHelper.importPlaylists
                    // takes a single Uri.
                    ImportHelper.importPlaylists(
                        requireContext().applicationContext,
                        files.first(),
                        importFormat,
                        progressCallback
                    )
                } finally {
                    dismissProgressDialog()
                }
            }
        }

    private val getWatchHistoryFile =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
            if (files.isEmpty()) return@registerForActivityResult
            showProgressDialog(getString(R.string.import_watch_history), getString(R.string.import_watch_history_start))
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Similar assumption as getPlaylistsFile, processing the first file
                    ImportHelper.importWatchHistory(
                        requireContext().applicationContext,
                        files.first(),
                        importFormat,
                        progressCallback
                    )
                } finally {
                    dismissProgressDialog()
                }
            }
        }

    private val createPlaylistsFile =
        registerForActivityResult(CreateDocument(FILETYPE_ANY)) { uri ->
            uri?.let {
                showProgressDialog(getString(R.string.export_playlists_to), getString(R.string.export_playlists_start))
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        ImportHelper.exportPlaylists(
                            requireContext().applicationContext,
                            uri,
                            importFormat,
                            null, // selectedPlaylistIds, needs to be passed if available
                            progressCallback
                        )
                    } finally {
                        dismissProgressDialog()
                    }
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.import_export_settings, rootKey)

        val importSubscriptions = findPreference<Preference>("import_subscriptions")
        importSubscriptions?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.import_subscriptions_from,
                importSubscriptionFormatList
            ) { format, _ ->
                importFormat = format
                getSubscriptionsFile.launch("*/*")
            }
            true
        }

        val exportSubscriptions = findPreference<Preference>("export_subscriptions")
        exportSubscriptions?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.export_subscriptions_to,
                exportSubscriptionFormatList,
                isExport = true
            ) { format, includeTimestamp ->
                importFormat = format
                createSubscriptionsFile.launch(
                    getExportFileName(requireContext(), format, "subscriptions", includeTimestamp)
                )
            }
            true
        }

        val importPlaylists = findPreference<Preference>("import_playlists")
        importPlaylists?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.import_playlists_from,
                importPlaylistFormatList
            ) { format, _ ->
                importFormat = format
                getPlaylistsFile.launch(arrayOf("*/*"))
            }
            true
        }

        val exportPlaylists = findPreference<Preference>("export_playlists")
        exportPlaylists?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.export_playlists_to,
                exportPlaylistFormatList,
                isExport = true
            ) { format, includeTimestamp ->
                importFormat = format
                createPlaylistsFile.launch(
                    getExportFileName(requireContext(), format, "playlists", includeTimestamp)
                )
            }
            true
        }

        val importWatchHistory = findPreference<Preference>("import_watch_history")
        importWatchHistory?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.import_watch_history,
                importWatchHistoryFormatList
            ) { format, _ ->
                importFormat = format
                getWatchHistoryFile.launch(arrayOf("*/*"))
            }
            true
        }

        childFragmentManager.setFragmentResultListener(
            BACKUP_DIALOG_REQUEST_KEY,
            this
        ) { _, resultBundle ->
            val encodedBackupFile = resultBundle.getString(IntentData.backupFile)!!
            backupFile = Json.decodeFromString(encodedBackupFile)
            val timestamp = TextUtils.getFileSafeTimeStampNow()
            createBackupFile.launch("libretube-backup-${timestamp}.json")
        }
        val advancedBackup = findPreference<Preference>("backup")
        advancedBackup?.setOnPreferenceClickListener {
            BackupDialog().show(childFragmentManager, null)
            true
        }

        val restoreAdvancedBackup = findPreference<Preference>("restore")
        restoreAdvancedBackup?.setOnPreferenceClickListener {
            getBackupFile.launch(JSON)
            true
        }
    }

    companion object {
        const val JSON = "application/json"

        /**
         * Mimetype to use to create new files when setting extension manually
         */
        const val FILETYPE_ANY = "application/octet-stream"

        val importSubscriptionFormatList = listOf(
            ImportFormat.NEWPIPE,
            ImportFormat.FREETUBE,
            ImportFormat.YOUTUBECSV
        )
        val exportSubscriptionFormatList = listOf(
            ImportFormat.NEWPIPE,
            ImportFormat.FREETUBE
        )
        val importPlaylistFormatList = listOf(
            ImportFormat.PIPED,
            ImportFormat.FREETUBE,
            ImportFormat.YOUTUBECSV,
            ImportFormat.URLSORIDS
        )
        val exportPlaylistFormatList = listOf(
            ImportFormat.PIPED,
            ImportFormat.FREETUBE
        )
        val importWatchHistoryFormatList = listOf(ImportFormat.YOUTUBEJSON)

        fun createImportFormatDialog(
            context: Context,
            @StringRes titleStringId: Int,
            formats: List<ImportFormat>,
            isExport: Boolean = false,
            onConfirm: (ImportFormat, Boolean) -> Unit
        ) {
            var selectedIndex = 0

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(titleStringId))
                .setSingleChoiceItems(
                    formats.map { context.getString(it.value) }.toTypedArray(),
                    selectedIndex
                ) { _, i ->
                    selectedIndex = i
                }

            val layoutInflater = LayoutInflater.from(context)
            val binding = DialogImportExportFormatChooserBinding.inflate(layoutInflater)
            binding.includeTimestamp.isChecked = PreferenceHelper.getBoolean(
                PreferenceKeys.INCLUDE_TIMESTAMP_IN_BACKUP_FILENAME,
                false
            )
            if (isExport) {
                dialog.setView(binding.root)
            }

            dialog.setPositiveButton(R.string.okay) { _, _ ->
                if (isExport) PreferenceHelper.putBoolean(
                    PreferenceKeys.INCLUDE_TIMESTAMP_IN_BACKUP_FILENAME,
                    binding.includeTimestamp.isChecked
                )

                onConfirm(formats[selectedIndex], binding.includeTimestamp.isChecked)
            }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        fun getExportFileName(
            context: Context,
            format: ImportFormat,
            type: String,
            includeTimestamp: Boolean
        ): String {
            var baseString = context.getString(format.value).lowercase()
            baseString += "-${type}"

            if (includeTimestamp) {
                baseString += "-${TextUtils.getFileSafeTimeStampNow()}"
            }

            return "${baseString}.${format.fileExtension}"
        }
    }
}
