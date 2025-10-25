package no.divvun.spellchecker

import android.content.Context
import android.service.textservice.SpellCheckerService
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import no.divvun.packageobserver.SpellerArchiveWatcher
import no.divvun.toLocale
import timber.log.Timber

class DivvunSpellCheckerService : SpellCheckerService() {
    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")
    }

    override fun createSession(): Session {
        Timber.d("createSession")
        return DivvunSpellCheckerSession(this)
    }

    class DivvunSpellCheckerSession(private val context: Context) : Session() {

        /*private lateinit var spellerArchiveWatcher: SpellerArchiveWatcher
        private val speller
            get() = spellerArchiveWatcher.archive?.speller()*/

        override fun onCreate() {
            Timber.d("onCreate, locale: ${locale.toLocale()}")
            //spellerArchiveWatcher = SpellerArchiveWatcher(context, locale.toLocale())
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            Timber.d("onGetSuggestions()")
            
            // Get the word
            val word = textInfo?.text?.trim() ?: ""
            Timber.d("word: '$word'")
            
            // Return test suggestions based on whether word is empty or not
            val suggestions = if (word.isEmpty()) {
                arrayOf("test1", "test2")
            } else {
                arrayOf("complete1", "complete2")
            }
            
            val attrs = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
            return SuggestionsInfo(attrs, suggestions)
        }

        override fun onGetSentenceSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int): Array<SentenceSuggestionsInfo> {
            Timber.d("onGetSentenceSuggestionsMultiple()")
            return super.onGetSentenceSuggestionsMultiple(textInfos, suggestionsLimit)
        }

        override fun onGetSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int, sequentialWords: Boolean): Array<SuggestionsInfo> {
            Timber.d("onGetSuggestionsMultiple()")
            return super.onGetSuggestionsMultiple(textInfos, suggestionsLimit, sequentialWords)
        }
    }

}