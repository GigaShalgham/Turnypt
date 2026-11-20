package com.turnypt.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.turnypt.data.repository.ContactRepository
import com.turnypt.data.repository.MessageCipher

/**
 * Home-screen state for the companion architecture (spec 003).
 *
 * The IME-status plumbing from v0.1 was removed together with the
 * `:keyboard` dependency: the shipped app no longer registers an input
 * method at all, so there is nothing to query.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val contactsRepository: ContactRepository,
    private val messageCipher: MessageCipher,
) : ViewModel() {

    private val _contactCount = MutableStateFlow(0)
    val contactCount = _contactCount.asStateFlow()

    private val _unlockedCount = MutableStateFlow(0)
    val unlockedCount = _unlockedCount.asStateFlow()

    init {
        refresh()
        // v0.4.2 (deep-scan N1): the Home "Unlocked: N" chip used to keep
        // its count until the next resume — a lockAll() or a timeout in
        // another tab left it claiming sessions that were already wiped.
        viewModelScope.launch {
            messageCipher.sessionChanges.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            // v1.5.9 (lag audit): one DB read (the contact ids) and ONE
            // session-manager lock (the whole unlocked set in a single
            // pass) — this ran on every sessionChanges bump, and the old
            // body did two database round-trips plus one isUnlocked()
            // mutex acquisition PER CONTACT on each one. The counters
            // carry the same values as before; only the traffic is gone.
            val contacts = contactsRepository.observeContacts().first()
            val unlockedIds = messageCipher.unlockedContactIds()
            _contactCount.value = contacts.size
            _unlockedCount.value = contacts.count { it.id in unlockedIds }
        }
    }

    override fun onCleared() {
        // Nothing sensitive is held here.
        super.onCleared()
    }
}
