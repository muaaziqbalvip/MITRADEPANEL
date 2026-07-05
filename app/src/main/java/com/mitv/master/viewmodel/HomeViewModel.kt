package com.mitv.master.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitv.master.data.model.Channel
import com.mitv.master.data.parser.M3uParser
import com.mitv.master.data.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val httpClient = OkHttpClient()

    init {
        observeRemoteChannels()
    }

    private fun observeRemoteChannels() {
        viewModelScope.launch {
            firebaseRepository.observeChannels().collect { list ->
                _channels.value = list
            }
        }
    }

    /** Fetches and parses an M3U/M3U8 URL added by the user. */
    fun loadPlaylistFromUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                val parsed = M3uParser.parse(body)
                _channels.value = parsed
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun filteredChannels(): List<Channel> {
        val query = _searchQuery.value.trim()
        return if (query.isEmpty()) {
            _channels.value
        } else {
            _channels.value.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun groupedByCategory(): Map<String, List<Channel>> {
        return filteredChannels().groupBy { it.groupTitle }
    }
}
