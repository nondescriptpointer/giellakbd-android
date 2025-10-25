package no.divvun.dictionary

import android.content.Context
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.NgramContext
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion
import no.divvun.divvunspell.SpellerArchive
import no.divvun.divvunspell.SpellerConfig
import no.divvun.packageobserver.SpellerArchiveWatcher
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import kotlin.collections.ArrayList

class DivvunDictionary(private val context: Context?, private val locale: Locale?) : Dictionary(TYPE_MAIN, locale) {
    private val spellerArchiveWatcher: SpellerArchiveWatcher? = context?.let { SpellerArchiveWatcher(it, locale!!) }

    private val speller get(): SpellerArchive? {
        if (context == null || locale == null) {
            return null
        }

        val speller = spellerArchiveWatcher?.archive
        if (speller != null) {
            return speller
        }

        // If no package, try getting it from the assets.
        val bhfstName = "${locale.toLanguageTag()}.bhfst"
        val bhfstFile = File(context.cacheDir, bhfstName)

        if (!bhfstFile.exists()) {
            try {
                val asset = context.assets.open(bhfstName)
                asset.copyTo(bhfstFile.outputStream())
            } catch (e: FileNotFoundException) {
                // Ignore this.
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        if (!bhfstFile.exists()) {
            try {
                spellerArchiveWatcher?.archive = SpellerArchive.open(bhfstFile.path)
                return spellerArchiveWatcher?.archive
            } catch (e: Exception) {
                // Ignore Rust errors that are just about missing files.
                if (!e.toString().contains("No such file or directory")) {
                    Timber.w(e)
                }
            }
        }

        return null
    }

    init {
        Timber.d("DivvunDictionaryCreated")
    }

    override fun getSuggestions(
            composedData: ComposedData,
            ngramContext: NgramContext,
            proximityInfoHandle: Long,
            settingsValuesForSuggestion: SettingsValuesForSuggestion,
            sessionId: Int,
            weightForLocale: Float,
            inOutWeightOfLangModelVsSpatialModel: FloatArray
    ): ArrayList<SuggestedWordInfo> {

        Timber.d("getSuggestions - TEST MODE")
        val word = composedData.mTypedWord.trim()
        Timber.d("word: '$word'")

        // Return test suggestions based on whether word is empty or not
        val testSuggestions = if (word.isEmpty()) {
            listOf("test1", "test2")
        } else {
            listOf("complete1", "complete2")
        }

        Timber.d("Returning test suggestions: $testSuggestions")

        val result = testSuggestions.mapIndexed { index, suggestion ->
            SuggestedWordInfo(suggestion, ngramContext.extractPrevWordsContext(),
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, this,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        }

        return ArrayList(result)
    }

    override fun isInDictionary(word: String): Boolean {
        val speller = this.speller ?: return false
        return speller.speller().isCorrect(word)
    }

    companion object {
        const val N_BEST_SUGGESTION_SIZE = 3L
        const val MAX_WEIGHT = 4999.99f
    }
}