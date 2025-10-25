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
import no.divvun.arabic.ArabicDictionaryService
import no.divvun.arabic.WordContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import kotlin.collections.ArrayList

class DivvunDictionary(private val context: Context?, private val locale: Locale?) : Dictionary(TYPE_MAIN, locale) {
    private val spellerArchiveWatcher: SpellerArchiveWatcher? = context?.let { SpellerArchiveWatcher(it, locale!!) }
    private val arabicService: ArabicDictionaryService? = context?.let { ArabicDictionaryService(it) }

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

        Timber.d("getSuggestions")
        val word = composedData.mTypedWord.trim()
        Timber.d("word: '$word'")

        // Check if we should use Arabic service
        //val isArabicLocale = locale?.language == "ar"
        val isArabicLocale = true
        
        if (isArabicLocale && arabicService != null) {
            Timber.d("Using Arabic prediction service")
            return getArabicSuggestions(composedData, ngramContext, word)
        }

        // Fall back to original Divvun speller or test mode
        val speller = this.speller
        if (speller != null) {
            Timber.d("Using Divvun speller")
            return getDivvunSuggestions(composedData, ngramContext, word, speller)
        } else {
            Timber.d("Using test suggestions")
            return getTestSuggestions(composedData, ngramContext, word)
        }
    }
    
    private fun getArabicSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        word: String
    ): ArrayList<SuggestedWordInfo> {
        val arabicService = this.arabicService ?: return ArrayList()
        
        // Extract context from ngramContext
        val prevWords = ngramContext.extractPrevWordsContextArray()
        val prev2 = if (prevWords.size >= 2) prevWords[prevWords.size - 2] else null
        val prev1 = if (prevWords.size >= 1) prevWords[prevWords.size - 1] else null
        
        // Update context for the Arabic service
        val wordContext = WordContext(
            secondBefore = prev2,
            firstBefore = prev1,
            word = word
        )
        arabicService.updateContext(wordContext)
        
        // Get suggestions from Arabic service
        val suggestions = if (word.isEmpty()) {
            arabicService.getNextWords(prev2 = prev2, prev1 = prev1, limit = 5)
        } else {
            arabicService.getNextWords(prev2 = prev2, prev1 = prev1, prefix = word, limit = 5)
        }
        
        Timber.d("Arabic suggestions: ${suggestions.map { it.first }}")
        
        val result = suggestions.mapIndexed { index, (suggestion, score) ->
            SuggestedWordInfo(
                suggestion,
                ngramContext.extractPrevWordsContext(),
                score.toInt(),
                SuggestedWordInfo.KIND_TYPED,
                this,
                SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWordInfo.NOT_A_CONFIDENCE
            )
        }
        
        return ArrayList(result)
    }
    
    private fun getDivvunSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        word: String,
        speller: SpellerArchive
    ): ArrayList<SuggestedWordInfo> {
        if (word.isEmpty()) {
            Timber.wtf("Word was invalid!")
            return ArrayList()
        }
        
        val suggestions = mutableListOf(composedData.mTypedWord)
        val config = SpellerConfig(nBest = N_BEST_SUGGESTION_SIZE, maxWeight = MAX_WEIGHT)
        val spellerSuggestions = speller.speller().suggest(word, config)
        suggestions.addAll(spellerSuggestions)
        
        Timber.d("Divvun suggestions: $suggestions")
        
        val result = suggestions.mapIndexed { index, suggestion ->
            if (index == 0) {
                SuggestedWordInfo(suggestion, ngramContext.extractPrevWordsContext(),
                        suggestions.size - index, SuggestedWordInfo.KIND_CORRECTION, this,
                        SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            } else {
                SuggestedWordInfo(suggestion, ngramContext.extractPrevWordsContext(),
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, this,
                        SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            }
        }
        
        return ArrayList(result)
    }
    
    private fun getTestSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        word: String
    ): ArrayList<SuggestedWordInfo> {
        // Return test suggestions based on whether word is empty or not
        val testSuggestions = if (word.isEmpty()) {
            listOf("test1", "test2")
        } else {
            listOf("complete1", "complete2")
        }

        Timber.d("Test suggestions: $testSuggestions")

        val result = testSuggestions.mapIndexed { index, suggestion ->
            SuggestedWordInfo(suggestion, ngramContext.extractPrevWordsContext(),
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED, this,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        }

        return ArrayList(result)
    }

    override fun isInDictionary(word: String): Boolean {
        // Check if we should use Arabic service
        val isArabicLocale = locale?.language == "ar"
        
        if (isArabicLocale && arabicService != null) {
            return arabicService.isCorrect(word)
        }
        
        // Fall back to Divvun speller
        val speller = this.speller ?: return false
        return speller.speller().isCorrect(word)
    }

    fun close() {
        arabicService?.close()
    }
    
    companion object {
        const val N_BEST_SUGGESTION_SIZE = 3L
        const val MAX_WEIGHT = 4999.99f
    }
}